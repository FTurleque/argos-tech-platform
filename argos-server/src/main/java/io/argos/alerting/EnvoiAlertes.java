package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;
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
 */
@ApplicationScoped
public class EnvoiAlertes {

    private static final Logger LOG = Logger.getLogger(EnvoiAlertes.class);

    @Inject
    DataSource dataSource;

    @Inject
    @Any
    Instance<CanalDiffusion> canaux;

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

    @Scheduled(identity = "argos-envoi-alertes", every = "{argos.alerting.envoi.intervalle}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifie() {
        traiterEnAttente(Instant.now());
    }

    /** Diffuse les messages en attente dont l'échéance de tentative est passée. */
    public Map<UUID, Issue> traiterEnAttente(Instant maintenant) {
        Map<String, CanalDiffusion> parCode = parCode();
        if (parCode.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = QuarkusTransaction.requiringNew().call(() -> {
            List<UUID> found = new ArrayList<>();
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                    SELECT id FROM alerte WHERE etat = 'EN_ATTENTE' AND canal = ANY (?)
                      AND (prochain_essai IS NULL OR prochain_essai <= ?) ORDER BY cree_le, id LIMIT 500
                    """)) {
                ps.setArray(1, c.createArrayOf("varchar", parCode.keySet().toArray()));
                ps.setObject(2, maintenant.atOffset(ZoneOffset.UTC));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        found.add(rs.getObject(1, UUID.class));
                    }
                }
            }
            return found;
        });
        return envoyer(ids, maintenant);
    }

    /** Première tentative ou nouvelle tentative pour les messages donnés. */
    public Map<UUID, Issue> envoyer(Collection<UUID> ids, Instant maintenant) {
        Map<String, CanalDiffusion> parCode = parCode();
        Map<UUID, Issue> issues = new HashMap<>();
        for (UUID id : ids) {
            try {
                issues.put(id, tenter(id, parCode, maintenant));
            } catch (RuntimeException e) {
                LOG.errorf(e, "diffusion de l'alerte %s en échec", id);
            }
        }
        return issues;
    }

    /**
     * Une tentative en trois temps : réservation du message et décompte de la tentative dans une
     * transaction courte, diffusion hors transaction (aucun verrou n'est tenu pendant l'appel SMTP),
     * puis enregistrement de l'issue. Toute exception de la diffusion ou de la lecture du contenu
     * compte comme un échec : le nombre de tentatives reste borné (ALR-06-S2).
     */
    private Issue tenter(UUID id, Map<String, CanalDiffusion> parCode, Instant maintenant) {
        Optional<Reservation> reservation = QuarkusTransaction.requiringNew()
                .call(() -> reserver(id, parCode.keySet(), maintenant));
        if (reservation.isEmpty()) {
            return Issue.IGNOREE;
        }
        Reservation r = reservation.get();
        if (r.epuisee()) {
            return Issue.EN_ECHEC;
        }
        String erreur;
        boolean definitif;
        try {
            parCode.get(r.canal()).diffuser(r.message(id));
            QuarkusTransaction.requiringNew().run(() -> enregistrerSucces(id, maintenant));
            return Issue.ENVOYEE;
        } catch (CanalDiffusion.EchecDiffusion echec) {
            erreur = echec.getMessage();
            definitif = echec.definitif();
        } catch (ContenuIllisible e) {
            erreur = "contenu du message illisible";
            definitif = true;
        } catch (RuntimeException e) {
            erreur = "diffusion impossible : " + e.getClass().getSimpleName();
            definitif = false;
        }
        boolean abandon = definitif || r.tentatives() >= tentativesMax;
        String motif = erreur;
        QuarkusTransaction.requiringNew().run(() -> enregistrerEchec(id, abandon,
                abandon ? null : maintenant.plus(delai(r.tentatives())), motif));
        LOG.warnf("diffusion de l'alerte %s : tentative %d en échec (%s)", id, r.tentatives(), erreur);
        return abandon ? Issue.EN_ECHEC : Issue.A_RETENTER;
    }

    /** Délai avant la tentative suivante : délai initial doublé à chaque échec, plafonné. */
    Duration delai(int tentatives) {
        Duration d = delaiInitial;
        for (int i = 1; i < tentatives && d.compareTo(delaiMax) < 0; i++) {
            d = d.multipliedBy(2);
        }
        return d.compareTo(delaiMax) > 0 ? delaiMax : d;
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
            } catch (java.io.IOException e) {
                throw new ContenuIllisible(e);
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
        try (Connection c = dataSource.getConnection()) {
            // ALR-07-S1 : une alerte immédiate dont tous les impacts ont été écartés ou résolus avant l'envoi n'est
            // pas diffusée.
            if (AlertesSansObjet.annulerSiSansObjet(c, id)) {
                return Optional.empty();
            }
            Reservation r;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT a.canal, a.tentatives, a.nature, a.compte_id, c.email, a.titre, a.gravite, a.contenu::text
                    FROM alerte a JOIN compte c ON c.id = a.compte_id
                    WHERE a.id = ? AND a.etat = 'EN_ATTENTE' AND (a.prochain_essai IS NULL OR a.prochain_essai <= ?)
                    FOR UPDATE OF a SKIP LOCKED
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, maintenant.atOffset(ZoneOffset.UTC));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !canauxDiffuses.contains(rs.getString(1))) {
                        return Optional.empty();
                    }
                    int faites = rs.getInt(2);
                    r = new Reservation(rs.getString(1), faites + 1, faites >= tentativesMax, rs.getString(3),
                            rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getString(8));
                }
            }
            if (r.epuisee()) {
                executer(c, "UPDATE alerte SET etat = 'EN_ECHEC', prochain_essai = NULL, "
                        + "derniere_erreur = 'tentatives épuisées' WHERE id = ?", id);
            } else {
                executer(c, "UPDATE alerte SET tentatives = tentatives + 1, prochain_essai = ? WHERE id = ?",
                        maintenant.plus(BAIL).atOffset(ZoneOffset.UTC), id);
            }
            return Optional.of(r);
        }
    }

    private void enregistrerSucces(UUID id, Instant maintenant) {
        enregistrer("UPDATE alerte SET etat = 'ENVOYEE', envoye_le = ?, prochain_essai = NULL, derniere_erreur = NULL "
                + "WHERE id = ?", maintenant.atOffset(ZoneOffset.UTC), id);
    }

    private void enregistrerEchec(UUID id, boolean abandon, Instant prochainEssai, String erreur) {
        enregistrer("UPDATE alerte SET etat = ?, prochain_essai = ?, derniere_erreur = ? WHERE id = ?",
                abandon ? "EN_ECHEC" : "EN_ATTENTE",
                prochainEssai == null ? null : prochainEssai.atOffset(ZoneOffset.UTC), erreur, id);
    }

    private void enregistrer(String sql, Object... valeurs) {
        try (Connection c = dataSource.getConnection()) {
            executer(c, sql, valeurs);
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de l'issue d'une alerte impossible", e);
        }
    }

    private static void executer(Connection c, String sql, Object... valeurs) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < valeurs.length; i++) {
                ps.setObject(i + 1, valeurs[i]);
            }
            ps.executeUpdate();
        }
    }

    private Map<String, CanalDiffusion> parCode() {
        Map<String, CanalDiffusion> parCode = new HashMap<>();
        for (CanalDiffusion c : canaux) {
            parCode.put(c.code(), c);
        }
        return parCode;
    }
}
