package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
        String m = nettoyer(motif);
        String com = nettoyer(commentaire);
        if (vers == EtatImpact.ECARTE && m == null) {
            throw new IllegalArgumentException("motif obligatoire pour écarter un impact");
        }
        if ((m != null && m.length() > LONGUEUR_MAX) || (com != null && com.length() > LONGUEUR_MAX)) {
            throw new IllegalArgumentException("motif ou commentaire trop long");
        }
        try (Connection c = dataSource.getConnection()) {
            RlsSession.account(c, compteId);
            Optional<Lu> lu = lire(c, impactId);
            if (lu.isEmpty()) {
                return Optional.empty();
            }
            EtatImpact de = lu.get().etat();
            // Un impact dont le composant a quitté l'inventaire ne redevient pas actif (INV-C3) : 409.
            Set<EtatImpact> possibles = de.suivants(lu.get().composantPresent());
            if (!possibles.contains(vers)) {
                throw new TransitionInterdite(de, possibles);
            }
            if (!changer(c, impactId, de, vers)) {
                // Aucune ligne modifiée : état changé entre-temps (409), ou refus de la politique RLS.
                EtatImpact maintenantLu = lire(c, impactId).map(Lu::etat).orElse(de);
                if (maintenantLu != de) {
                    throw new TransitionInterdite(maintenantLu);
                }
                throw new ModificationRefusee();
            }
            historiser(c, impactId, lu.get().espace(), de, vers, compteId, m, com, maintenant);
            if (!EtatImpact.ACTIFS.contains(vers)) {
                traites.fire(new ImpactsTraites(lu.get().signal()));
            }
            return Optional.of(vers);
        } catch (SQLException e) {
            if ("42501".equals(e.getSQLState())) {
                // Politique d'insertion de l'historique refusée (rôle insuffisant).
                throw new ModificationRefusee();
            }
            throw new IllegalStateException("transition de l'impact impossible", e);
        }
    }

    /**
     * Vrai si la dernière transition de l'impact vers {@code RESOLU} a été faite par le système (COR-11) : seule une
     * résolution système peut être annulée par la recorrélation ; une résolution décidée par un utilisateur autorisé
     * est respectée (décision J5).
     */
    static boolean resoluParLeSysteme(Connection c, UUID impactId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT actor_system FROM project_impact_transition
                WHERE impact_id = ? AND to_state = 'RESOLU' ORDER BY occurred_at DESC, id DESC LIMIT 1
                """)) {
            ps.setObject(1, impactId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Transition système, dans la transaction et la connexion de l'appelant ({@link CorrelationEngine}).
     *
     * @return vrai si l'impact était encore à l'état {@code de} et a été modifié
     */
    static boolean systeme(Connection c, UUID impactId, UUID espace, EtatImpact de, EtatImpact vers, MotifSysteme motif,
            Instant maintenant) throws SQLException {
        if (!changer(c, impactId, de, vers)) {
            return false;
        }
        historiser(c, impactId, espace, de, vers, null, motif.name(), null, maintenant);
        return true;
    }

    private record Lu(EtatImpact etat, UUID espace, UUID signal, boolean composantPresent) {
    }

    private static Optional<Lu> lire(Connection c, UUID impactId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT state, workspace_id, signal_id, component_id IS NOT NULL FROM project_impact WHERE id = ?")) {
            ps.setObject(1, impactId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Lu(EtatImpact.valueOf(rs.getString(1)), rs.getObject(2, UUID.class),
                                rs.getObject(3, UUID.class), rs.getBoolean(4)))
                        : Optional.empty();
            }
        }
    }

    private static boolean changer(Connection c, UUID impactId, EtatImpact de, EtatImpact vers) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE project_impact SET state = ? WHERE id = ? AND state = ?")) {
            ps.setString(1, vers.name());
            ps.setObject(2, impactId);
            ps.setString(3, de.name());
            return ps.executeUpdate() == 1;
        }
    }

    private static void historiser(Connection c, UUID impactId, UUID espace, EtatImpact de, EtatImpact vers, UUID compte,
            String motif, String commentaire, Instant maintenant) throws SQLException {
        // Horodatage strictement croissant par impact : l'ordre de l'historique est celui des transitions (COR-12-S1),
        // même pour deux transitions de la même milliseconde.
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO project_impact_transition(id, impact_id, workspace_id, from_state, to_state, actor_account_id,
                       actor_system, reason, comment, occurred_at)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, greatest(?::timestamptz, coalesce(
                       (SELECT max(t.occurred_at) + interval '1 microsecond' FROM project_impact_transition t
                        WHERE t.impact_id = ?), ?::timestamptz))
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, impactId);
            ps.setObject(3, espace);
            ps.setString(4, de.name());
            ps.setString(5, vers.name());
            ps.setObject(6, compte);
            ps.setBoolean(7, compte == null);
            ps.setString(8, motif);
            ps.setString(9, commentaire);
            ps.setObject(10, maintenant.atOffset(ZoneOffset.UTC));
            ps.setObject(11, impactId);
            ps.setObject(12, maintenant.atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private static String nettoyer(String texte) {
        return texte == null || texte.isBlank() ? null : texte.strip();
    }
}
