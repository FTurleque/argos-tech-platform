package io.argos.vulnintel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;

import io.argos.signal.SignalChanged;
import io.argos.signal.SignalQueries;

/**
 * Enrichit les signaux par les sources secondaires à partir de leurs identifiants CVE (COL-04).
 *
 * <p>L'enrichissement a lieu après l'enregistrement du signal : l'indisponibilité d'une source
 * secondaire n'empêche jamais l'enregistrement (COL-04-S3) et l'enrichissement manquant est
 * retenté par {@link #retryMissing(int)}. Les données sont rattachées au signal existant ;
 * aucune source secondaire ne crée de signal (COL-04-S2).
 */
@ApplicationScoped
public class Enricher {

    private static final Logger LOG = Logger.getLogger(Enricher.class);
    private static final Pattern CVE = Pattern.compile("CVE-\\d{4}-\\d{4,}");

    /** Donnée d'enrichissement rattachée à un signal. */
    public record EnrichmentView(String source, String reference, String data, Instant fetchedAt) {
    }

    @Inject
    Instance<EnrichmentSource> sources;

    @Inject
    SignalQueries signals;

    @Inject
    DataSource dataSource;

    void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) {
        try {
            // Nouvelle transaction : celle qui a émis l'événement est déjà validée.
            QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId()));
        } catch (RuntimeException error) {
            LOG.warnf("enrichissement du signal %s différé : %s", event.signalId(), error.getMessage());
        }
    }

    /** Interroge chaque source pour chaque CVE du signal ; renvoie les sources indisponibles. */
    public List<String> enrich(UUID signalId) {
        List<String> unavailable = new ArrayList<>();
        for (String cve : signals.cveAliases(signalId)) {
            if (!CVE.matcher(cve).matches()) {
                continue;
            }
            for (EnrichmentSource source : sources) {
                try {
                    for (EnrichmentSource.Finding finding : source.lookup(cve)) {
                        save(signalId, source.id(), finding);
                    }
                } catch (EnrichmentUnavailableException error) {
                    unavailable.add(source.id());
                    LOG.infof("source %s indisponible pour %s : %s", source.id(), cve, error.getMessage());
                }
            }
        }
        return unavailable;
    }

    /** Relance l'enrichissement des signaux à CVE qui n'ont encore aucun enrichissement. */
    public int retryMissing(int limit) {
        List<UUID> pending = new ArrayList<>();
        String sql = """
                SELECT DISTINCT a.signal_id FROM signal_alias a
                WHERE a.alias LIKE 'CVE-%' AND NOT EXISTS (SELECT 1 FROM enrichment e WHERE e.signal_id = a.signal_id)
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    pending.add(resultSet.getObject(1, UUID.class));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("recherche des enrichissements manquants impossible", error);
        }
        pending.forEach(this::enrich);
        return pending.size();
    }

    @Transactional
    public List<EnrichmentView> forSignal(UUID signalId) {
        List<EnrichmentView> views = new ArrayList<>();
        String sql = "SELECT source, reference, data::text, fetched_at FROM enrichment WHERE signal_id = ? ORDER BY source, reference";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, signalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    views.add(new EnrichmentView(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                            resultSet.getObject(4, OffsetDateTime.class).toInstant()));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("lecture des enrichissements impossible", error);
        }
        return views;
    }

    /** Vrai si le catalogue CISA KEV signale l'exploitation avérée (COL-04-S1). */
    public boolean exploited(UUID signalId) {
        return forSignal(signalId).stream().anyMatch(view -> "KEV".equals(view.source()));
    }

    @Transactional
    void save(UUID signalId, String source, EnrichmentSource.Finding finding) {
        String sql = """
                INSERT INTO enrichment(signal_id, source, reference, data, fetched_at) VALUES (?, ?, ?, ?::jsonb, now())
                ON CONFLICT (signal_id, source, reference) DO UPDATE SET data = excluded.data, fetched_at = excluded.fetched_at
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, signalId);
            statement.setString(2, source);
            statement.setString(3, finding.reference());
            statement.setString(4, finding.data().toString());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("enregistrement de l'enrichissement impossible", error);
        }
    }
}
