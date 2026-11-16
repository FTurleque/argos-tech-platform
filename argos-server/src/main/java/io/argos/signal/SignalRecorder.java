package io.argos.signal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.PackageKey;
import io.argos.ingestion.AdvisorySink;
import io.argos.ingestion.NormalizedAdvisory;

/**
 * Enregistre les avis normalisés en signaux de sécurité (COL-01 à COL-03, décision D1).
 *
 * <p>Idempotence : insertion conditionnelle sur la contrainte (source, identifiant externe) ;
 * la base arbitre les collectes concurrentes (COL-03-S3). Une modification crée une nouvelle
 * version ; une version plus ancienne que la version courante ne la remplace pas.
 */
@ApplicationScoped
public class SignalRecorder implements AdvisorySink {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DataSource dataSource;

    @Inject
    Event<SignalChanged> changes;

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public Outcome record(String sourceId, NormalizedAdvisory advisory) {
        List<AffectedPackage> affected = toAffected(advisory.affected());
        String affectedJson = json(affected);
        try (Connection c = dataSource.getConnection()) {
            UUID signalId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            boolean inserted;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO signal(id, source, external_id, title, summary, severity, published_at, modified_at,
                                       collected_at, updated_at, content_hash, affected, current_version_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?::jsonb, ?)
                    ON CONFLICT (source, external_id) DO NOTHING
                    """)) {
                ps.setObject(1, signalId);
                ps.setString(2, sourceId);
                ps.setString(3, advisory.externalId());
                ps.setString(4, cut(advisory.title(), 1000));
                ps.setString(5, advisory.summary());
                ps.setString(6, cut(advisory.severity(), 300));
                ps.setObject(7, utc(advisory.publishedAt()));
                ps.setObject(8, utc(advisory.modifiedAt()));
                ps.setString(9, advisory.contentHash());
                ps.setString(10, affectedJson);
                ps.setObject(11, versionId);
                inserted = ps.executeUpdate() == 1;
            }
            if (inserted) {
                insertVersion(c, versionId, signalId, 1, advisory);
                replaceIndexes(c, signalId, advisory.aliases(), affected);
                changes.fire(new SignalChanged(signalId, true));
                return Outcome.CREATED;
            }

            Current current = lockCurrent(c, sourceId, advisory.externalId());
            if (current.contentHash().equals(advisory.contentHash())) {
                return Outcome.UNCHANGED;
            }
            if (advisory.modifiedAt().isBefore(current.modifiedAt())) {
                return Outcome.IGNORED_OLDER;
            }
            if (versionExists(c, current.id(), advisory.contentHash())) {
                return Outcome.UNCHANGED;
            }
            insertVersion(c, versionId, current.id(), current.lastVersionNo() + 1, advisory);
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE signal SET title = ?, summary = ?, severity = ?, published_at = ?, modified_at = ?,
                           updated_at = now(), content_hash = ?, affected = ?::jsonb, current_version_id = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, cut(advisory.title(), 1000));
                ps.setString(2, advisory.summary());
                ps.setString(3, cut(advisory.severity(), 300));
                ps.setObject(4, utc(advisory.publishedAt()));
                ps.setObject(5, utc(advisory.modifiedAt()));
                ps.setString(6, advisory.contentHash());
                ps.setString(7, affectedJson);
                ps.setObject(8, versionId);
                ps.setObject(9, current.id());
                ps.executeUpdate();
            }
            replaceIndexes(c, current.id(), advisory.aliases(), affected);
            changes.fire(new SignalChanged(current.id(), false));
            return Outcome.UPDATED;
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement du signal " + advisory.externalId() + " impossible", e);
        }
    }

    private record Current(UUID id, String contentHash, Instant modifiedAt, int lastVersionNo) {
    }

    private static Current lockCurrent(Connection c, String sourceId, String externalId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT s.id, s.content_hash, s.modified_at,
                       (SELECT max(v.version_no) FROM signal_version v WHERE v.signal_id = s.id)
                FROM signal s WHERE s.source = ? AND s.external_id = ? FOR UPDATE
                """)) {
            ps.setString(1, sourceId);
            ps.setString(2, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("signal attendu introuvable : " + externalId);
                }
                return new Current(rs.getObject(1, UUID.class), rs.getString(2).trim(),
                        rs.getObject(3, OffsetDateTime.class).toInstant(), rs.getInt(4));
            }
        }
    }

    private static boolean versionExists(Connection c, UUID signalId, String hash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM signal_version WHERE signal_id = ? AND content_hash = ?")) {
            ps.setObject(1, signalId);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void insertVersion(Connection c, UUID versionId, UUID signalId, int versionNo,
            NormalizedAdvisory advisory) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO signal_version(id, signal_id, version_no, content_hash, source_modified_at, collected_at,
                                           raw_content)
                VALUES (?, ?, ?, ?, ?, now(), ?)
                """)) {
            ps.setObject(1, versionId);
            ps.setObject(2, signalId);
            ps.setInt(3, versionNo);
            ps.setString(4, advisory.contentHash());
            ps.setObject(5, utc(advisory.modifiedAt()));
            ps.setString(6, advisory.rawContent());
            ps.executeUpdate();
        }
    }

    private static void replaceIndexes(Connection c, UUID signalId, List<String> aliases, List<AffectedPackage> affected)
            throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM signal_alias WHERE signal_id = ?")) {
            del.setObject(1, signalId);
            del.executeUpdate();
        }
        try (PreparedStatement del = c.prepareStatement("DELETE FROM signal_package WHERE signal_id = ?")) {
            del.setObject(1, signalId);
            del.executeUpdate();
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO signal_alias(signal_id, alias) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            for (String alias : aliases) {
                ins.setObject(1, signalId);
                ins.setString(2, cut(alias, 200));
                ins.addBatch();
            }
            ins.executeBatch();
        }
        Map<String, String> keys = new LinkedHashMap<>();
        affected.forEach(a -> keys.putIfAbsent(a.packageKey(), a.ecosystem()));
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO signal_package(signal_id, package_key, ecosystem) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> e : keys.entrySet()) {
                ins.setObject(1, signalId);
                ins.setString(2, cut(e.getKey(), 500));
                ins.setString(3, cut(e.getValue(), 100));
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    /** Traduit les paquets de l'avis ; un paquet sans clé déterminable n'est pas indexé. */
    static List<AffectedPackage> toAffected(List<NormalizedAdvisory.Affected> source) {
        List<AffectedPackage> result = new ArrayList<>();
        for (NormalizedAdvisory.Affected a : source) {
            Optional<PackageKey.Purl> purl = PackageKey.parse(a.purl());
            Optional<String> key = purl.map(PackageKey.Purl::key).or(() -> PackageKey.fromEcosystem(a.ecosystem(), a.name()));
            if (key.isEmpty()) {
                continue;
            }
            List<AffectedPackage.VersionRange> ranges = a.ranges().stream()
                    .map(r -> new AffectedPackage.VersionRange(r.type(), r.introduced(), r.fixed(), r.lastAffected()))
                    .toList();
            result.add(new AffectedPackage(key.get(), purl.isPresent(), a.ecosystem(), a.name(), ranges, a.versions()));
        }
        return result;
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("sérialisation des paquets affectés impossible", e);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
