package io.argos.ingestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;
import io.argos.commun.Page;
import io.argos.commun.Pagination;

/**
 * Persistance de l'état de collecte : curseurs, traitements de synchronisation, tentatives,
 * état des sources, zone d'échec et événements d'exploitation (COL-05 à COL-08, DC-3).
 */
@ApplicationScoped
public class SyncStore {

    /** État d'une source exposé à l'exploitant (DF-COL-3). */
    public record SourceState(String sourceId, String label, String state, int consecutiveFailures,
            Instant lastSuccessAt, Instant lastFailureAt, String lastError, String cursor) {
    }

    /** Avis mis à l'écart (COL-08). */
    public record Failure(UUID id, String sourceId, String externalId, String reason, Instant receivedAt) {
    }

    /** Événement d'exploitation daté (COL-07-S1). */
    public record OpsEvent(UUID id, String kind, String sourceId, String detail, Instant occurredAt) {
    }

    @Inject
    Clock horloge;

    @Inject
    DataSource dataSource;

    @Inject
    Jdbc jdbc;

    /** Durée sans activité après laquelle un traitement {@code RUNNING} est considéré comme interrompu. */
    @ConfigProperty(name = "argos.ingestion.running-expiry", defaultValue = "PT30M")
    Duration runningExpiry;

    @Transactional
    public Optional<String> cursor(String sourceId) {
        return queryString("SELECT position FROM sync_cursor WHERE source_id = ?", sourceId);
    }

    /** Avance le curseur ; appelé dans la transaction qui enregistre l'avis correspondant (INV-D5). */
    @Transactional(Transactional.TxType.MANDATORY)
    public void advanceCursor(String sourceId, String position, UUID runId) {
        update("""
                INSERT INTO sync_cursor(source_id, position, updated_at) VALUES (?, ?, now())
                ON CONFLICT (source_id) DO UPDATE SET position = excluded.position, updated_at = excluded.updated_at
                """, sourceId, position);
        update("UPDATE sync_run SET heartbeat_at = now() WHERE id = ?", runId);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void recordFailure(String sourceId, UUID runId, String externalId, String reason, String raw) {
        update("""
                INSERT INTO ingestion_failure(id, source_id, external_id, reason, raw_content, received_at, run_id)
                VALUES (?, ?, ?, ?, ?, now(), ?)
                """, UUID.randomUUID(), sourceId, externalId, truncate(reason), raw, runId);
    }

    /**
     * Démarre un traitement ou reprend le traitement {@code RETRYABLE} de la source (DC-3).
     * Renvoie vide si un traitement {@code RUNNING} non expiré existe déjà.
     */
    @Transactional
    public Optional<UUID> startOrResume(String sourceId) {
        update("SELECT id FROM source WHERE id = ? FOR UPDATE", sourceId);
        update("""
                UPDATE sync_run SET state = 'RETRYABLE', last_error = 'traitement interrompu, repris au prochain lancement'
                WHERE source_id = ? AND state = 'RUNNING' AND heartbeat_at <= ?
                """, sourceId, utc(horloge.instant().minus(runningExpiry)));
        if (queryString("SELECT id::text FROM sync_run WHERE source_id = ? AND state = 'RUNNING'", sourceId).isPresent()) {
            return Optional.empty();
        }
        Optional<String> retryable = queryString(
                "SELECT id::text FROM sync_run WHERE source_id = ? AND state = 'RETRYABLE' ORDER BY started_at LIMIT 1",
                sourceId);
        if (retryable.isPresent()) {
            UUID id = UUID.fromString(retryable.get());
            update("""
                    UPDATE sync_run SET state = 'RUNNING', heartbeat_at = now(), next_attempt_at = NULL
                    WHERE id = ? AND state = 'RETRYABLE'
                    """, id);
            return Optional.of(id);
        }
        UUID id = UUID.randomUUID();
        update("""
                INSERT INTO sync_run(id, source_id, state, attempts, started_at, heartbeat_at)
                VALUES (?, ?, 'RUNNING', 0, now(), now())
                """, id, sourceId);
        return Optional.of(id);
    }

    @Transactional
    public int attempts(UUID runId) {
        return queryString("SELECT attempts::text FROM sync_run WHERE id = ?", runId).map(Integer::parseInt).orElse(0);
    }

    /**
     * Reprend le traitement après son attente : vrai s'il est toujours {@code RUNNING} (INV-2). Faux s'il a expiré et
     * qu'un autre lancement l'a repris ; l'appelant s'arrête alors sans rien écrire.
     */
    @Transactional
    public boolean reprendre(UUID runId) {
        return update("UPDATE sync_run SET heartbeat_at = now(), next_attempt_at = NULL WHERE id = ? AND state = 'RUNNING'",
                runId) == 1;
    }

    @Transactional
    public void recordAttempt(UUID runId, int attempt, SourceException error) {
        update("""
                INSERT INTO sync_attempt(id, run_id, attempt_no, occurred_at, http_status, error_kind, error, retry_after_seconds)
                VALUES (?, ?, ?, now(), ?, ?, ?, ?)
                """, UUID.randomUUID(), runId, attempt, error.httpStatus().orElse(null), error.kind().name(),
                truncate(error.getMessage()), error.retryAfter().map(Duration::getSeconds).orElse(null));
    }

    /**
     * Attente d'une nouvelle tentative après une erreur transitoire (COL-06) : le traitement reste {@code RUNNING},
     * donc exclusif (index {@code sync_run_one_running}, COL-12-S3), avec la date de sa prochaine tentative.
     */
    @Transactional
    public void attendreReprise(UUID runId, int attempts, Instant nextAttemptAt, String error) {
        update("""
                UPDATE sync_run SET attempts = ?, next_attempt_at = ?, error_kind = 'TRANSIENT', last_error = ?,
                       heartbeat_at = now() WHERE id = ? AND state = 'RUNNING'
                """, attempts, utc(nextAttemptAt), truncate(error), runId);
    }

    /** Laisse le traitement {@code RETRYABLE} : le prochain lancement le reprend depuis le curseur persisté. */
    @Transactional
    public void abandonner(UUID runId, int attempts, String motif) {
        update("""
                UPDATE sync_run SET state = 'RETRYABLE', attempts = ?, next_attempt_at = NULL, last_error = ?,
                       heartbeat_at = now() WHERE id = ? AND state = 'RUNNING'
                """, attempts, truncate(motif), runId);
    }

    @Transactional
    public void succeed(UUID runId, int attempts) {
        update("""
                UPDATE sync_run SET state = 'SUCCEEDED', attempts = ?, finished_at = now(), heartbeat_at = now()
                WHERE id = ?
                """, attempts, runId);
    }

    @Transactional
    public void fail(UUID runId, int attempts, String errorKind, String error) {
        update("""
                UPDATE sync_run SET state = 'FAILED', attempts = ?, error_kind = ?, last_error = ?, finished_at = now(),
                       heartbeat_at = now() WHERE id = ?
                """, attempts, errorKind, truncate(error), runId);
    }

    /** Synchronisation réussie : la source redevient nominale (COL-07-S2). */
    @Transactional
    public void sourceSucceeded(String sourceId) {
        Optional<String> previous = queryString("SELECT state FROM source WHERE id = ? FOR UPDATE", sourceId);
        update("""
                UPDATE source SET state = 'NOMINAL', consecutive_failures = 0, last_success_at = now(), last_error = NULL
                WHERE id = ?
                """, sourceId);
        if (previous.filter("DEGRADED"::equals).isPresent()) {
            event("SOURCE_RECOVERED", sourceId, "synchronisation réussie");
        }
    }

    /**
     * Synchronisation en échec : compteur incrémenté, état {@code DEGRADED} et événement
     * d'exploitation quand le seuil est atteint (COL-07-S1).
     */
    @Transactional
    public void sourceFailed(String sourceId, String error, int threshold, boolean permanent) {
        String previous = queryString("SELECT state FROM source WHERE id = ? FOR UPDATE", sourceId).orElseThrow();
        update("""
                UPDATE source SET consecutive_failures = consecutive_failures + 1, last_failure_at = now(), last_error = ?,
                       state = CASE WHEN consecutive_failures + 1 >= ? THEN 'DEGRADED' ELSE 'FAILING' END
                WHERE id = ?
                """, truncate(error), threshold, sourceId);
        if (permanent) {
            event("SYNC_PERMANENT_ERROR", sourceId, error);
        }
        String current = queryString("SELECT state FROM source WHERE id = ?", sourceId).orElseThrow();
        if ("DEGRADED".equals(current) && !"DEGRADED".equals(previous)) {
            event("SOURCE_DEGRADED", sourceId, error);
        }
    }

    /** Reclasse {@code RETRYABLE} les traitements {@code RUNNING} expirés (arc42 §6.4, COL-05-S2). */
    @Transactional
    public int recoverExpired(Duration expiry) {
        return update("""
                UPDATE sync_run SET state = 'RETRYABLE', last_error = 'traitement interrompu, repris au prochain lancement'
                WHERE state = 'RUNNING' AND heartbeat_at <= ?
                """, utc(horloge.instant().minus(expiry)));
    }

    @Transactional
    public List<SourceState> sourceStates() {
        List<SourceState> states = new ArrayList<>();
        String sql = """
                SELECT s.id, s.label, s.state, s.consecutive_failures, s.last_success_at, s.last_failure_at, s.last_error,
                       c.position
                FROM source s LEFT JOIN sync_cursor c ON c.source_id = s.id ORDER BY s.id
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                states.add(new SourceState(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3), resultSet.getInt(4),
                        instant(resultSet, 5), instant(resultSet, 6), resultSet.getString(7), resultSet.getString(8)));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("lecture de l'état des sources impossible", error);
        }
        return states;
    }

    /** Page des avis mis à l'écart d'une source, du plus récent au plus ancien (COL-08, ADR-0040). */
    @Transactional
    public Page<Failure> failures(String sourceId, Pagination pagination) {
        long total = jdbc.unique("SELECT count(*) FROM ingestion_failure WHERE source_id = ?", Jdbc.parametres(sourceId),
                resultat -> resultat.getLong(1)).orElse(0L);
        List<Failure> failures = jdbc.lister("""
                SELECT id, source_id, external_id, reason, received_at FROM ingestion_failure
                WHERE source_id = ? ORDER BY received_at DESC, id DESC LIMIT ? OFFSET ?
                """, Jdbc.parametres(sourceId, pagination.taille(), pagination.decalage()),
                resultat -> new Failure(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                        resultat.getString(4), Conversions.instant(resultat, 5)));
        return Page.depuis(failures, pagination, total);
    }

    /** Page des événements d'exploitation d'une source, du plus récent au plus ancien (COL-07, ADR-0040). */
    @Transactional
    public Page<OpsEvent> events(String sourceId, Pagination pagination) {
        long total = jdbc.unique("SELECT count(*) FROM ops_event WHERE source_id = ?", Jdbc.parametres(sourceId),
                resultat -> resultat.getLong(1)).orElse(0L);
        List<OpsEvent> events = jdbc.lister("SELECT id, kind, source_id, detail, occurred_at FROM ops_event WHERE source_id = ?"
                + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
                Jdbc.parametres(sourceId, pagination.taille(), pagination.decalage()),
                resultat -> new OpsEvent(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                        resultat.getString(4), Conversions.instant(resultat, 5)));
        return Page.depuis(events, pagination, total);
    }

    private void event(String kind, String sourceId, String detail) {
        update("INSERT INTO ops_event(id, kind, source_id, detail, occurred_at) VALUES (?, ?, ?, ?, now())",
                UUID.randomUUID(), kind, sourceId, truncate(detail));
    }

    private Optional<String> queryString(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.ofNullable(resultSet.getString(1)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("requête de collecte en échec", error);
        }
    }

    private int update(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.execute() ? 0 : statement.getUpdateCount();
        } catch (SQLException error) {
            throw new IllegalStateException("écriture de collecte en échec", error);
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int index = 0; index < params.length; index++) {
            statement.setObject(index + 1, params[index]);
        }
    }

    private static Instant instant(ResultSet resultSet, int column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
