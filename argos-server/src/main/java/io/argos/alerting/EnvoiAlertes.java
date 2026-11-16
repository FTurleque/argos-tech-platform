package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>Chaque message est verrouillé pendant sa diffusion ({@code FOR UPDATE SKIP LOCKED}) : deux
 * traitements concurrents ne l'envoient pas deux fois.
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
                issues.put(id, QuarkusTransaction.requiringNew().call(() -> tenter(id, parCode, maintenant)));
            } catch (RuntimeException e) {
                LOG.errorf(e, "diffusion de l'alerte %s en échec", id);
            }
        }
        return issues;
    }

    private Issue tenter(UUID id, Map<String, CanalDiffusion> parCode, Instant maintenant) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Optional<MessageAlerte> message = charger(c, id);
            if (message.isEmpty()) {
                return Issue.IGNOREE;
            }
            CanalDiffusion canal = parCode.get(message.get().canal());
            if (canal == null) {
                return Issue.IGNOREE;
            }
            try {
                canal.diffuser(message.get());
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE alerte SET etat = 'ENVOYEE', tentatives = tentatives + 1, envoye_le = ?,
                               prochain_essai = NULL, derniere_erreur = NULL WHERE id = ?
                        """)) {
                    ps.setObject(1, maintenant.atOffset(ZoneOffset.UTC));
                    ps.setObject(2, id);
                    ps.executeUpdate();
                }
                return Issue.ENVOYEE;
            } catch (CanalDiffusion.EchecDiffusion echec) {
                int tentatives = tentatives(c, id) + 1;
                boolean abandon = echec.definitif() || tentatives >= tentativesMax;
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE alerte SET etat = ?, tentatives = ?, prochain_essai = ?, derniere_erreur = ? WHERE id = ?
                        """)) {
                    ps.setString(1, abandon ? "EN_ECHEC" : "EN_ATTENTE");
                    ps.setInt(2, tentatives);
                    ps.setObject(3, abandon ? null : maintenant.plus(delai(tentatives)).atOffset(ZoneOffset.UTC));
                    ps.setString(4, echec.getMessage());
                    ps.setObject(5, id);
                    ps.executeUpdate();
                }
                LOG.warnf("diffusion de l'alerte %s : tentative %d en échec (%s)", id, tentatives, echec.getMessage());
                return abandon ? Issue.EN_ECHEC : Issue.A_RETENTER;
            }
        }
    }

    /** Délai avant la tentative suivante : délai initial doublé à chaque échec, plafonné. */
    Duration delai(int tentatives) {
        Duration d = delaiInitial;
        for (int i = 1; i < tentatives && d.compareTo(delaiMax) < 0; i++) {
            d = d.multipliedBy(2);
        }
        return d.compareTo(delaiMax) > 0 ? delaiMax : d;
    }

    private static int tentatives(Connection c, UUID id) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT tentatives FROM alerte WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Message verrouillé, s'il est encore en attente. */
    private static Optional<MessageAlerte> charger(Connection c, UUID id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT a.id, a.nature, a.compte_id, c.email, a.canal, a.titre, a.gravite, a.contenu::text
                FROM alerte a JOIN compte c ON c.id = a.compte_id
                WHERE a.id = ? AND a.etat = 'EN_ATTENTE' FOR UPDATE OF a SKIP LOCKED
                """)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MessageAlerte(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        MoteurAlertes.JSON.readValue(rs.getString(8), ContenuAlerte.class)));
            }
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
