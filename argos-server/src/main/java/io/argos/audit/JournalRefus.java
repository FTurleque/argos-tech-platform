package io.argos.audit;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.commun.securite.LimiteurDebit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Journal des refus d'accès (ACC-06, ADR-0016).
 *
 * <p>Chaque entrée enregistre le compte (ou, à défaut, le sujet OIDC), la ressource demandée
 * (méthode et chemin, sans paramètre de requête ni corps), le motif et l'horodatage. Aucune
 * donnée de la ressource refusée n'est recopiée. L'écriture se fait dans sa propre transaction,
 * indépendamment de la requête refusée.
 *
 * <p>Le journal est borné par auteur (ACC-15) : au plus {@code argos.audit.refus.par-minute} refus écrits par
 * minute glissante pour un même compte, ou pour un même sujet OIDC sans compte. Au-delà, le refus reste appliqué par
 * l'appelant ; il n'est pas écrit et il est compté ({@code argos_refus_non_journalises_total}).
 */
@ApplicationScoped
public class JournalRefus {

    static final String METRIQUE_NON_JOURNALISES = "argos.refus.non.journalises";

    private static final Logger LOG = Logger.getLogger(JournalRefus.class);
    private static final int LONGUEUR_MAX_RESSOURCE = 500;

    /** Clé commune des refus sans compte ni sujet. */
    private static final UUID AUTEUR_ANONYME = new UUID(0L, 0L);

    @Inject
    DataSource dataSource;

    @Inject
    MeterRegistry registre;

    @ConfigProperty(name = "argos.audit.refus.par-minute", defaultValue = "30")
    int refusParMinute;

    private LimiteurDebit limiteur;
    private Counter nonJournalises;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(refusParMinute);
        nonJournalises = Counter.builder(METRIQUE_NON_JOURNALISES)
                .description("Refus d'accès appliqués sans être écrits au journal, auteur au-delà de sa limite")
                .register(registre);
    }

    public void enregistrer(UUID compteId, String sujetOidc, String ressource, String motif) {
        if (!limiteur.admettre(auteur(compteId, sujetOidc))) {
            nonJournalises.increment();
            return;
        }
        String ressourceBornee = ressource.length() > LONGUEUR_MAX_RESSOURCE
                ? ressource.substring(0, LONGUEUR_MAX_RESSOURCE) : ressource;
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement(
                        "INSERT INTO refus_acces (compte_id, sujet_oidc, ressource, motif) VALUES (?, ?, ?, ?)")) {
            requete.setObject(1, compteId);
            requete.setString(2, sujetOidc);
            requete.setString(3, ressourceBornee);
            requete.setString(4, motif);
            requete.executeUpdate();
        } catch (SQLException erreur) {
            // Le refus est appliqué même si sa trace échoue ; l'échec reste visible dans les journaux.
            LOG.errorf(erreur, "Refus d'accès non journalisé : %s %s", motif, ressourceBornee);
        }
    }

    /**
     * Clé de l'auteur d'un refus : le compte, sinon une empreinte du sujet OIDC (le sujet n'est pas gardé en clair en
     * mémoire), sinon la clé anonyme commune.
     */
    static UUID auteur(UUID compteId, String sujetOidc) {
        if (compteId != null) {
            return compteId;
        }
        if (sujetOidc != null && !sujetOidc.isBlank()) {
            return UUID.nameUUIDFromBytes(("sujet:" + sujetOidc).getBytes(StandardCharsets.UTF_8));
        }
        return AUTEUR_ANONYME;
    }
}
