package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.commun.ContexteBase;
import io.argos.commun.Jdbc;

/**
 * État de traitement des impacts (COR-09 à COR-11, décision J5 du porteur du projet, 2026-11-19 ; DC-5).
 *
 * <p>Transition demandée par un utilisateur : le rôle de l'appelant est résolu par la couche API, qui seule connaît
 * les appartenances ({@code impact} ne dépend pas de {@code workspace}) ; la base l'applique de nouveau sous
 * {@code argos_app} avec le compte de l'appelant (politiques {@code project_impact_etat} et
 * {@code project_impact_transition_creation}, migration V20) : un {@code LECTEUR} ne peut rien modifier même si la
 * couche API se trompait. La mise à jour est conditionnée par l'état lu ({@code WHERE state = :de}) et l'historique
 * est écrit dans la même transaction : deux demandes concurrentes ne produisent jamais deux transitions depuis le même
 * état (concurrence optimiste, 409 pour la seconde).
 *
 * <p>Transition système : appelée par {@link CorrelationEngine} sous son verrou par signal, dans sa transaction et
 * sous l'utilisateur de connexion (traitement technique), avec un {@link MotifSysteme}.
 */
@ApplicationScoped
public class TraitementImpacts {

    /** Longueur maximale d'un motif ou d'un commentaire (design, « Sécurité et isolation »). */
    public static final int LONGUEUR_MAX = 1000;

    /** Transition non prévue depuis l'état courant (COR-10-S3), ou état modifié entre-temps. */
    public static final class TransitionInterdite extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final EtatImpact courant;

        private final Set<EtatImpact> possibles;

        public TransitionInterdite(EtatImpact courant) {
            this(courant, courant.suivants());
        }

        public TransitionInterdite(EtatImpact courant, Set<EtatImpact> possibles) {
            super("Transition impossible depuis l'état " + courant + ".");
            this.courant = courant;
            this.possibles = Set.copyOf(possibles);
        }

        public EtatImpact courant() {
            return courant;
        }

        /** Transitions possibles depuis l'état courant. */
        public Set<EtatImpact> possibles() {
            return possibles;
        }
    }

    /** La base a refusé la modification sous {@code argos_app} : rôle insuffisant ou impact hors périmètre. */
    public static final class ModificationRefusee extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ModificationRefusee() {
            super("Modification de l'état refusée.");
        }
    }

    @Inject
    DataSource dataSource;

    @Inject
    Event<ImpactsTraites> traites;

    /**
     * Applique la transition demandée par le compte donné.
     *
     * @return vide si l'impact n'est pas visible du compte sous la RLS ; sinon l'état atteint
     * @throws TransitionInterdite si la transition n'est pas permise depuis l'état courant
     * @throws ModificationRefusee si la base refuse la modification (rôle insuffisant)
     * @throws IllegalArgumentException si {@code ECARTE} est demandé sans motif, ou si un texte dépasse
     *         {@link #LONGUEUR_MAX} caractères
     */
    @Transactional
    public Optional<EtatImpact> transitionner(UUID impactId, UUID compteId, EtatImpact vers, String motif,
            String commentaire, Instant maintenant) {
        String motifNettoye = nettoyer(motif);
        String com = nettoyer(commentaire);
        if (vers == EtatImpact.ECARTE && motifNettoye == null) {
            throw new IllegalArgumentException("motif obligatoire pour écarter un impact");
        }
        if ((motifNettoye != null && motifNettoye.length() > LONGUEUR_MAX) || (com != null && com.length() > LONGUEUR_MAX)) {
            throw new IllegalArgumentException("motif ou commentaire trop long");
        }
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(compteId));
            Optional<ImpactLu> lecture = lire(connexion, impactId);
            if (lecture.isEmpty()) {
                return Optional.empty();
            }
            EtatImpact depart = lecture.get().etat();
            // Un impact dont le composant a quitté l'inventaire ne redevient pas actif (INV-C3) : 409.
            Set<EtatImpact> possibles = depart.suivants(lecture.get().composantPresent());
            if (!possibles.contains(vers)) {
                throw new TransitionInterdite(depart, possibles);
            }
            if (!changer(connexion, impactId, depart, vers)) {
                // Aucune ligne modifiée : état changé entre-temps (409), ou refus de la politique RLS.
                EtatImpact maintenantLu = lire(connexion, impactId).map(ImpactLu::etat).orElse(depart);
                if (maintenantLu != depart) {
                    throw new TransitionInterdite(maintenantLu);
                }
                throw new ModificationRefusee();
            }
            historiser(connexion, impactId, lecture.get().espace(), depart, vers, compteId, motifNettoye, com, maintenant);
            if (!EtatImpact.ACTIFS.contains(vers)) {
                traites.fire(new ImpactsTraites(lecture.get().signal()));
            }
            return Optional.of(vers);
        } catch (SQLException erreur) {
            if ("42501".equals(erreur.getSQLState())) {
                // Politique d'insertion de l'historique refusée (rôle insuffisant).
                throw new ModificationRefusee();
            }
            throw new IllegalStateException("transition de l'impact impossible", erreur);
        }
    }

    /**
     * Vrai si la dernière transition de l'impact vers {@code RESOLU} a été faite par le système (COR-11) : seule une
     * résolution système peut être annulée par la recorrélation ; une résolution décidée par un utilisateur autorisé
     * est respectée (décision J5).
     */
    static boolean resoluParLeSysteme(Connection connexion, UUID impactId) {
        return Jdbc.unique(connexion, """
                SELECT actor_system FROM project_impact_transition
                WHERE impact_id = ? AND to_state = 'RESOLU' ORDER BY occurred_at DESC, id DESC LIMIT 1
                """, Jdbc.parametres(impactId), resultat -> resultat.getBoolean(1)).orElse(false);
    }

    /**
     * Transition système, dans la transaction et la connexion de l'appelant ({@link CorrelationEngine}).
     *
     * @return vrai si l'impact était encore à l'état {@code depart} et a été modifié
     */
    static boolean systeme(Connection connexion, UUID impactId, UUID espace, EtatImpact depart, EtatImpact vers, MotifSysteme motif,
            Instant maintenant) throws SQLException {
        if (!changer(connexion, impactId, depart, vers)) {
            return false;
        }
        historiser(connexion, impactId, espace, depart, vers, null, motif.name(), null, maintenant);
        return true;
    }

    private record ImpactLu(EtatImpact etat, UUID espace, UUID signal, boolean composantPresent) {
    }

    private static Optional<ImpactLu> lire(Connection connexion, UUID impactId) {
        return Jdbc.unique(connexion,
                "SELECT state, workspace_id, signal_id, component_id IS NOT NULL FROM project_impact WHERE id = ?",
                Jdbc.parametres(impactId), resultat -> new ImpactLu(EtatImpact.valueOf(resultat.getString(1)),
                        resultat.getObject(2, UUID.class), resultat.getObject(3, UUID.class), resultat.getBoolean(4)));
    }

    private static boolean changer(Connection connexion, UUID impactId, EtatImpact depart, EtatImpact vers) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("UPDATE project_impact SET state = ? WHERE id = ? AND state = ?")) {
            requete.setString(1, vers.name());
            requete.setObject(2, impactId);
            requete.setString(3, depart.name());
            return requete.executeUpdate() == 1;
        }
    }

    private static void historiser(Connection connexion, UUID impactId, UUID espace, EtatImpact depart, EtatImpact vers, UUID compte,
            String motif, String commentaire, Instant maintenant) throws SQLException {
        // Horodatage strictement croissant par impact : l'ordre de l'historique est celui des transitions (COR-12-S1),
        // même pour deux transitions de la même milliseconde.
        try (PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO project_impact_transition(id, impact_id, workspace_id, from_state, to_state, actor_account_id,
                       actor_system, reason, comment, occurred_at)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, greatest(?::timestamptz, coalesce(
                       (SELECT max(t.occurred_at) + interval '1 microsecond' FROM project_impact_transition t
                        WHERE t.impact_id = ?), ?::timestamptz))
                """)) {
            requete.setObject(1, UUID.randomUUID());
            requete.setObject(2, impactId);
            requete.setObject(3, espace);
            requete.setString(4, depart.name());
            requete.setString(5, vers.name());
            requete.setObject(6, compte);
            requete.setBoolean(7, compte == null);
            requete.setString(8, motif);
            requete.setString(9, commentaire);
            requete.setObject(10, maintenant.atOffset(ZoneOffset.UTC));
            requete.setObject(11, impactId);
            requete.setObject(12, maintenant.atOffset(ZoneOffset.UTC));
            requete.executeUpdate();
        }
    }

    private static String nettoyer(String texte) {
        return texte == null || texte.isBlank() ? null : texte.strip();
    }
}
