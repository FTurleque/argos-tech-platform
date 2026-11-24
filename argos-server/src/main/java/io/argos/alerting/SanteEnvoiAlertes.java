package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * File d'envoi des alertes et résumés pour l'exploitation (EXP-14, EXP-15, design.md DC-2) :
 * <ul>
 * <li>contrôle de disponibilité {@value #NOM} : messages en attente et messages abandonnés ({@code EN_ECHEC}) sur la
 * dernière {@link #FENETRE_ECHECS}, {@code etat=degrade} s'il y en a ; le contrôle reste {@code UP} (design.md D5) ;</li>
 * <li>jauge {@code argos_alertes_file{etat}} ({@code en_attente}, {@code en_echec}), relevée à chaque contrôle et après
 * chaque passage d'envoi ({@link #actualiser(Instant)}).</li>
 * </ul>
 * Aucune adresse ni aucun compte n'apparaît dans les données du contrôle ni dans les étiquettes (EXP-14-S3).
 */
@Readiness
@ApplicationScoped
public class SanteEnvoiAlertes implements HealthCheck {

    /** Nom du contrôle de disponibilité. */
    public static final String NOM = "envoi-alertes";

    /** Période sur laquelle les messages abandonnés signalent un envoi dégradé. */
    static final Duration FENETRE_ECHECS = Duration.ofHours(24);

    private static final Logger LOG = Logger.getLogger(SanteEnvoiAlertes.class);

    /** Contenu de la file à un instant. */
    record EtatFile(long enAttente, long enEchecRecents) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    MeterRegistry registre;

    private final AtomicLong enAttente = new AtomicLong();
    private final AtomicLong enEchec = new AtomicLong();

    @PostConstruct
    void enregistrerJauges() {
        Gauge.builder("argos.alertes.file", enAttente, AtomicLong::get)
                .description("Messages d'alerte en attente d'envoi").tag("etat", "en_attente").register(registre);
        Gauge.builder("argos.alertes.file", enEchec, AtomicLong::get)
                .description("Messages d'alerte abandonnés sur les dernières 24 heures").tag("etat", "en_echec")
                .register(registre);
    }

    @Override
    public HealthCheckResponse call() {
        EtatFile file;
        try {
            file = actualiser(Instant.now());
        } catch (IllegalStateException erreur) {
            LOG.warnf(erreur, "contrôle %s : file d'envoi illisible", NOM);
            return HealthCheckResponse.named(NOM).up().withData("etat", "illisible").build();
        }
        return HealthCheckResponse.named(NOM).up()
                .withData("etat", file.enEchecRecents() > 0 ? "degrade" : "nominal")
                .withData("en-attente", file.enAttente())
                .withData("en-echec-24h", file.enEchecRecents())
                .build();
    }

    /** Relit la file d'envoi et met à jour la jauge {@code argos_alertes_file}. */
    EtatFile actualiser(Instant maintenant) {
        EtatFile file = lire(maintenant);
        enAttente.set(file.enAttente());
        enEchec.set(file.enEchecRecents());
        return file;
    }

    private EtatFile lire(Instant maintenant) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT count(*) FILTER (WHERE etat = 'EN_ATTENTE'),
                       count(*) FILTER (WHERE etat = 'EN_ECHEC' AND cree_le >= ?)
                FROM alerte WHERE etat IN ('EN_ATTENTE', 'EN_ECHEC')
                """)) {
            requete.setObject(1, maintenant.minus(FENETRE_ECHECS).atOffset(ZoneOffset.UTC));
            try (ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                return new EtatFile(resultat.getLong(1), resultat.getLong(2));
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de la file d'envoi des alertes impossible", erreur);
        }
    }
}
