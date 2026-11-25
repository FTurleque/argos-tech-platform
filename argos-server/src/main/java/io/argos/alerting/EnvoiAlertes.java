package io.argos.alerting;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;
import io.argos.observabilite.ErreursImprevues;
import io.argos.observabilite.TacheObservee;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.scheduler.Scheduled;

/**
 * Diffusion des alertes et résumés {@code EN_ATTENTE} sur les canaux qui ont une implémentation de
 * {@link CanalDiffusion} (ALR-06). Un échec est retenté avec un délai croissant, borné ; au-delà du
 * nombre maximal de tentatives, ou pour un échec définitif, le message passe {@code EN_ECHEC} et reste
 * consultable dans Argos (ALR-06-S2).
 *
 * <p>Chaque message est réservé avant sa diffusion ({@code FOR UPDATE SKIP LOCKED}, puis échéance
 * repoussée du {@link #BAIL}) : deux traitements concurrents ne l'envoient pas deux fois, et aucun
 * verrou ni aucune transaction n'est tenu pendant l'appel au relais SMTP.
 *
 * <p>Chaque tentative est comptée dans {@code argos_alertes_envois_total{issue}} (EXP-14) ; après chaque passage
 * planifié, la file est relevée par {@link SanteEnvoiAlertes}.
 */
@ApplicationScoped
public class EnvoiAlertes {

    private static final Logger LOG = Logger.getLogger(EnvoiAlertes.class);

    @Inject
    Clock horloge;

    @Inject
    DataSource dataSource;

    @Inject
    Jdbc jdbc;

    @Inject
    @Any
    Instance<CanalDiffusion> canaux;

    @Inject
    @Readiness
    SanteEnvoiAlertes sante;

    @Inject
    MeterRegistry registre;

    @ConfigProperty(name = "argos.alerting.envoi.tentatives-max")
    int tentativesMax;

    @ConfigProperty(name = "argos.alerting.envoi.delai-initial")
    Duration delaiInitial;

    @ConfigProperty(name = "argos.alerting.envoi.delai-max")
    Duration delaiMax;

    /**
     * Bail d'une tentative : échéance provisoire posée à la réservation, supérieure au délai de connexion
     * SMTP ({@code quarkus.mailer.timeout}). Un message réservé n'est pas repris avant son expiration.
     */
    static final Duration BAIL = Duration.ofMinutes(5);

    /** Issue d'une tentative. */
    public enum Issue { ENVOYEE, A_RETENTER, EN_ECHEC, IGNOREE }

    @TacheObservee
    @Scheduled(identity = "argos-envoi-alertes", every = "{argos.alerting.envoi.intervalle}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifie() {
        Instant maintenant = horloge.instant();
        try {
            traiterEnAttente(maintenant);
        } finally {
            // La file est relevée même quand le passage relance une erreur imprévue (arc42 §8.5).
            sante.actualiser(maintenant);
        }
    }

    /** Diffuse les messages en attente dont l'échéance de tentative est passée. */
    public Map<UUID, Issue> traiterEnAttente(Instant maintenant) {
        Map<String, CanalDiffusion> parCode = parCode();
        if (parCode.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = QuarkusTransaction.requiringNew().call(() -> jdbc.lister("""
                SELECT id FROM alerte WHERE etat = 'EN_ATTENTE' AND canal = ANY (?)
                  AND (prochain_essai IS NULL OR prochain_essai <= ?) ORDER BY cree_le, id LIMIT 500
                """, requete -> {
                    requete.setArray(1, requete.getConnection().createArrayOf("varchar", parCode.keySet().toArray()));
                    requete.setObject(2, Conversions.utc(maintenant));
                }, resultat -> resultat.getObject(1, UUID.class)));
        return envoyer(ids, maintenant);
    }

    /**
     * Première tentative ou nouvelle tentative pour les messages donnés. Tous les messages sont tentés ; la première
     * erreur imprévue est relancée ensuite (frontière d'erreur, arc42 §8.5).
     */
    public Map<UUID, Issue> envoyer(Collection<UUID> ids, Instant maintenant) {
        Map<String, CanalDiffusion> parCode = parCode();
        Map<UUID, Issue> issues = new HashMap<>();
        ErreursImprevues imprevues = new ErreursImprevues();
        for (UUID id : ids) {
            tenterOuJournaliser(id, parCode, maintenant, imprevues).ifPresent(issue -> {
                issues.put(id, issue);
                compter(issue);
            });
        }
        imprevues.relancer();
        return issues;
    }

    /**
     * Point d'isolation de la diffusion : une panne d'accès aux données d'un message (réservation ou enregistrement
     * de son issue) ou une erreur imprévue est journalisée avec sa cause et n'empêche pas la diffusion des messages
     * suivants ; l'erreur imprévue est retenue.
     */
    private Optional<Issue> tenterOuJournaliser(UUID id, Map<String, CanalDiffusion> parCode, Instant maintenant,
            ErreursImprevues imprevues) {
        try {
            return Optional.of(tenter(id, parCode, maintenant));
        } catch (IllegalStateException | QuarkusTransactionException erreur) {
            LOG.errorf(erreur, "diffusion de l'alerte %s en échec", id);
            return Optional.empty();
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "diffusion de l'alerte %s interrompue par une erreur imprévue", id);
            imprevues.retenir(imprevue);
            return Optional.empty();
        }
    }

    /**
     * Une tentative en trois temps : réservation du message et décompte de la tentative dans une
     * transaction courte, diffusion hors transaction (aucun verrou n'est tenu pendant l'appel SMTP),
     * puis enregistrement de l'issue. Un échec du canal, un contenu illisible ou une panne de l'enregistrement
     * du succès comptent comme une tentative en échec : le nombre de tentatives reste borné (ALR-06-S2).
     */
    private Issue tenter(UUID id, Map<String, CanalDiffusion> parCode, Instant maintenant) {
        Optional<Reservation> reservation = QuarkusTransaction.requiringNew()
                .call(() -> reserver(id, parCode.keySet(), maintenant));
        if (reservation.isEmpty()) {
            return Issue.IGNOREE;
        }
        Reservation reservationObtenue = reservation.get();
        if (reservationObtenue.epuisee()) {
            return Issue.EN_ECHEC;
        }
        Optional<Echec> echec = diffuser(id, reservationObtenue, parCode, maintenant);
        if (echec.isEmpty()) {
            return Issue.ENVOYEE;
        }
        String motif = echec.get().motif();
        boolean abandon = echec.get().definitif() || reservationObtenue.tentatives() >= tentativesMax;
        QuarkusTransaction.requiringNew().run(() -> enregistrerEchec(id, abandon,
                abandon ? null : maintenant.plus(delai(reservationObtenue.tentatives())), motif));
        LOG.warnf("diffusion de l'alerte %s : tentative %d en échec (%s)", id, reservationObtenue.tentatives(), motif);
        return abandon ? Issue.EN_ECHEC : Issue.A_RETENTER;
    }

    /** Échec d'une tentative : motif enregistré sur le message, et caractère définitif (aucune nouvelle tentative). */
    private record Echec(String motif, boolean definitif) {
    }

    /** Diffuse le message réservé puis enregistre son envoi ; vide en cas de succès, sinon l'échec de la tentative. */
    private Optional<Echec> diffuser(UUID id, Reservation reservation, Map<String, CanalDiffusion> parCode, Instant maintenant) {
        try {
            parCode.get(reservation.canal()).diffuser(reservation.message(id));
            QuarkusTransaction.requiringNew().run(() -> enregistrerSucces(id, maintenant));
            return Optional.empty();
        } catch (CanalDiffusion.EchecDiffusion echec) {
            LOG.warnf(echec, "diffusion de l'alerte %s sur le canal %s refusée", id, reservation.canal());
            return Optional.of(new Echec(echec.getMessage(), echec.definitif()));
        } catch (ContenuIllisible illisible) {
            LOG.warnf(illisible, "contenu de l'alerte %s illisible", id);
            return Optional.of(new Echec("contenu du message illisible", true));
        } catch (IllegalStateException | QuarkusTransactionException panne) {
            LOG.warnf(panne, "enregistrement de l'envoi de l'alerte %s impossible", id);
            return Optional.of(new Echec("diffusion impossible : " + panne.getClass().getSimpleName(), false));
        }
    }

    private void compter(Issue issue) {
        Counter.builder("argos.alertes.envois").description("Tentatives d'envoi des alertes et résumés, par issue")
                .tag("issue", issue.name().toLowerCase(Locale.ROOT)).register(registre).increment();
    }

    /** Délai avant la tentative suivante : délai initial doublé à chaque échec, plafonné. */
    Duration delai(int tentatives) {
        Duration delai = delaiInitial;
        for (int tentative = 1; tentative < tentatives && delai.compareTo(delaiMax) < 0; tentative++) {
            delai = delai.multipliedBy(2);
        }
        return delai.compareTo(delaiMax) > 0 ? delaiMax : delai;
    }

    /**
     * Message réservé pour une tentative.
     *
     * @param tentatives numéro de la tentative en cours
     * @param epuisee tentatives déjà épuisées : le message est abandonné sans diffusion
     */
    private record Reservation(String canal, int tentatives, boolean epuisee, String nature, UUID compteId,
            String adresse, String titre, String gravite, String contenu) {

        MessageAlerte message(UUID id) {
            try {
                return new MessageAlerte(id, nature, compteId, adresse, canal, titre, gravite,
                        MoteurAlertes.JSON.readValue(contenu, ContenuAlerte.class));
            } catch (java.io.IOException erreur) {
                throw new ContenuIllisible(erreur);
            }
        }
    }

    /** Contenu JSON d'un message qui ne se relit pas : échec définitif. */
    private static final class ContenuIllisible extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ContenuIllisible(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Verrouille le message s'il est en attente et échu, compte la tentative et repousse son échéance
     * du {@link #BAIL} : un arrêt pendant la diffusion laisse une tentative comptée, reprise après le bail.
     */
    private Optional<Reservation> reserver(UUID id, Set<String> canauxDiffuses, Instant maintenant) throws SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            // ALR-07-S1 : une alerte immédiate dont tous les impacts ont été écartés ou résolus avant l'envoi n'est
            // pas diffusée.
            if (AlertesSansObjet.annulerSiSansObjet(connexion, id)) {
                return Optional.empty();
            }
            Optional<Reservation> lue = Jdbc.unique(connexion, """
                    SELECT a.canal, a.tentatives, a.nature, a.compte_id, c.email, a.titre, a.gravite, a.contenu::text
                    FROM alerte a JOIN compte c ON c.id = a.compte_id
                    WHERE a.id = ? AND a.etat = 'EN_ATTENTE' AND (a.prochain_essai IS NULL OR a.prochain_essai <= ?)
                    FOR UPDATE OF a SKIP LOCKED
                    """, Jdbc.parametres(id, Conversions.utc(maintenant)), this::lireReservation)
                    .filter(candidate -> canauxDiffuses.contains(candidate.canal()));
            if (lue.isEmpty()) {
                return Optional.empty();
            }
            Reservation reservation = lue.get();
            if (reservation.epuisee()) {
                Jdbc.modifier(connexion, "UPDATE alerte SET etat = 'EN_ECHEC', prochain_essai = NULL, "
                        + "derniere_erreur = 'tentatives épuisées' WHERE id = ?", Jdbc.parametres(id));
            } else {
                Jdbc.modifier(connexion, "UPDATE alerte SET tentatives = tentatives + 1, prochain_essai = ? WHERE id = ?",
                        Jdbc.parametres(Conversions.utc(maintenant.plus(BAIL)), id));
            }
            return Optional.of(reservation);
        }
    }

    /** Message lu à la réservation : le numéro de la tentative en cours suit celles déjà faites. */
    private Reservation lireReservation(ResultSet resultat) throws SQLException {
        int faites = resultat.getInt(2);
        return new Reservation(resultat.getString(1), faites + 1, faites >= tentativesMax, resultat.getString(3),
                resultat.getObject(4, UUID.class), resultat.getString(5), resultat.getString(6), resultat.getString(7),
                resultat.getString(8));
    }

    private void enregistrerSucces(UUID id, Instant maintenant) {
        jdbc.modifier("UPDATE alerte SET etat = 'ENVOYEE', envoye_le = ?, prochain_essai = NULL, derniere_erreur = NULL "
                + "WHERE id = ?", Jdbc.parametres(Conversions.utc(maintenant), id));
    }

    private void enregistrerEchec(UUID id, boolean abandon, Instant prochainEssai, String erreur) {
        jdbc.modifier("UPDATE alerte SET etat = ?, prochain_essai = ?, derniere_erreur = ? WHERE id = ?",
                Jdbc.parametres(abandon ? "EN_ECHEC" : "EN_ATTENTE", Conversions.utc(prochainEssai), erreur, id));
    }

    private Map<String, CanalDiffusion> parCode() {
        Map<String, CanalDiffusion> parCode = new HashMap<>();
        for (CanalDiffusion canal : canaux) {
            parCode.put(canal.code(), canal);
        }
        return parCode;
    }
}
