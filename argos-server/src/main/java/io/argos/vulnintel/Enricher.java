package io.argos.vulnintel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

import io.quarkus.narayana.jta.QuarkusTransactionException;

import io.argos.signal.SignalChanged;
import io.argos.signal.SignalQueries;

/**
 * Enrichit les signaux par les sources secondaires à partir de leurs identifiants CVE (COL-04, ADR-0039).
 *
 * <p>L'enregistrement d'un signal le place dans la file d'enrichissement ({@link FileEnrichissement}) : la
 * synchronisation n'attend aucune source secondaire (COL-04-S4). La tâche {@link TraitementEnrichissements} appelle
 * {@link #enrich(UUID)} hors de toute transaction ; un signal dont une source n'a pas répondu reste en file et est
 * repris (COL-04-S3), et {@link #retryMissing(int)} replace en file les signaux à CVE restés sans enrichissement.
 * Les données sont rattachées au signal existant ; aucune source secondaire ne crée de signal (COL-04-S2).
 */
@ApplicationScoped
public class Enricher {

    private static final Logger LOG = Logger.getLogger(Enricher.class);
    private static final Pattern CVE = Pattern.compile("CVE-\\d{4}-\\d{4,}");

    /** Donnée d'enrichissement rattachée à un signal. */
    public record EnrichmentView(String source, String reference, String data, Instant fetchedAt) {
    }

    @Inject
    Clock horloge;

    @Inject
    Instance<EnrichmentSource> sources;

    @Inject
    SignalQueries signals;

    @Inject
    FileEnrichissement file;

    @Inject
    StockEnrichissements stock;

    @Inject
    DataSource dataSource;

    /** Place le signal créé ou modifié dans la file, après la validation de son enregistrement. */
    void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) {
        try {
            file.demander(event.signalId(), horloge.instant());
        } catch (IllegalStateException | QuarkusTransactionException erreur) {
            LOG.errorf(erreur, "mise en file de l'enrichissement du signal %s en échec ; le rattrapage la reprendra",
                    event.signalId());
        }
    }

    /**
     * Interroge chaque source pour chaque CVE du signal et enregistre leurs données, chacune dans sa transaction ;
     * renvoie les sources indisponibles, sans doublon. Aucune transaction n'est ouverte pendant un appel à une source.
     */
    public List<String> enrich(UUID signalId) {
        Set<String> unavailable = new LinkedHashSet<>();
        for (String cve : signals.cveAliases(signalId)) {
            if (!CVE.matcher(cve).matches()) {
                continue;
            }
            for (EnrichmentSource source : sources) {
                try {
                    for (EnrichmentSource.Finding finding : source.lookup(cve)) {
                        stock.enregistrer(signalId, source.id(), finding);
                    }
                } catch (EnrichmentUnavailableException error) {
                    unavailable.add(source.id());
                    LOG.infof(error, "source %s indisponible pour %s : %s", source.id(), cve, error.getMessage());
                }
            }
        }
        return List.copyOf(unavailable);
    }

    /** Replace en file au plus {@code limit} signaux à CVE sans aucun enrichissement ; renvoie leur nombre. */
    public int retryMissing(int limit) {
        return file.rattraper(horloge.instant(), limit);
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
}
