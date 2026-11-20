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
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.PackageKey;
import io.argos.ingestion.AdvisoryFeed;
import io.argos.ingestion.AdvisorySink;
import io.argos.ingestion.NormalizedAdvisory;
import io.argos.ingestion.RawAdvisory;

/**
 * Enregistre les avis normalisés en signaux de sécurité (COL-01 à COL-03, COL-10, décision D1).
 *
 * <p>Idempotence : insertion conditionnelle sur la contrainte (source, identifiant externe) ;
 * la base arbitre les collectes concurrentes (COL-03-S3). Un avis connu est inchangé si l'empreinte
 * de son contenu normalisé est identique à celle de la version courante (COL-10, décision J2 du
 * 2026-11-19) ; sinon le signal est mis à jour : une nouvelle version est créée, ou, si le contenu
 * redevient celui d'une version antérieure, le signal repointe vers cette version sans la dupliquer.
 * Un avis plus ancien que la version courante ne la remplace pas. L'empreinte du contenu brut reste enregistrée pour l'audit.
 */
@ApplicationScoped
public class SignalRecorder implements AdvisorySink {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(SignalRecorder.class);

    @Inject
    DataSource dataSource;

    @Inject
    Event<SignalChanged> changes;

    /** Normaliseurs des sources, pour rattraper l'empreinte normalisée d'un signal antérieur à V18. */
    @Inject
    Instance<AdvisoryFeed> feeds;

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public Outcome record(String sourceId, NormalizedAdvisory advisory) {
        List<AffectedPackage> affected = toAffected(advisory.affected());
        String affectedJson = json(affected);
        String normalizedHash = advisory.normalizedHash();
        try (Connection connection = dataSource.getConnection()) {
            UUID signalId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            boolean inserted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO signal(id, source, external_id, title, summary, severity, published_at, modified_at,
                                       collected_at, updated_at, content_hash, normalized_hash, affected,
                                       current_version_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, ?::jsonb, ?)
                    ON CONFLICT (source, external_id) DO NOTHING
                    """)) {
                statement.setObject(1, signalId);
                statement.setString(2, sourceId);
                statement.setString(3, advisory.externalId());
                statement.setString(4, cut(advisory.title(), 1000));
                statement.setString(5, advisory.summary());
                statement.setString(6, cut(advisory.severity(), 300));
                statement.setObject(7, utc(advisory.publishedAt()));
                statement.setObject(8, utc(advisory.modifiedAt()));
                statement.setString(9, advisory.contentHash());
                statement.setString(10, normalizedHash);
                statement.setString(11, affectedJson);
                statement.setObject(12, versionId);
                inserted = statement.executeUpdate() == 1;
            }
            if (inserted) {
                insertVersion(connection, versionId, signalId, 1, advisory, normalizedHash);
                replaceIndexes(connection, signalId, advisory.aliases(), affected);
                changes.fire(new SignalChanged(signalId, true));
                return Outcome.CREATED;
            }

            Current current = lockCurrent(connection, sourceId, advisory.externalId());
            String currentHash = current.normalizedHash();
            if (currentHash == null) {
                // Signal antérieur à l'empreinte normalisée (V18) : rattrapage sans nouvelle version (COL-10-S4).
                currentHash = catchUp(connection, sourceId, current, advisory, normalizedHash);
            }
            boolean unchanged = currentHash != null ? currentHash.equals(normalizedHash)
                    : current.contentHash().equals(advisory.contentHash());
            if (unchanged) {
                // Même contenu normalisé : seules la présentation ou la date de modification diffèrent (COL-10-S1, S2).
                return Outcome.UNCHANGED;
            }
            if (advisory.modifiedAt().isBefore(current.modifiedAt())) {
                return Outcome.IGNORED_OLDER;
            }
            Optional<UUID> earlier = earlierVersion(connection, current.id(), normalizedHash, advisory.contentHash());
            UUID targetVersion;
            if (earlier.isPresent()) {
                // Contenu revenu à celui d'une version antérieure : le signal est mis à jour et repointe vers
                // cette version, sans la dupliquer (COL-10, décision J2 ; invariant INV-A1).
                targetVersion = earlier.get();
            } else {
                insertVersion(connection, versionId, current.id(), current.lastVersionNo() + 1, advisory, normalizedHash);
                targetVersion = versionId;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE signal SET title = ?, summary = ?, severity = ?, published_at = ?, modified_at = ?,
                           updated_at = now(), content_hash = ?, normalized_hash = ?, affected = ?::jsonb,
                           current_version_id = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, cut(advisory.title(), 1000));
                statement.setString(2, advisory.summary());
                statement.setString(3, cut(advisory.severity(), 300));
                statement.setObject(4, utc(advisory.publishedAt()));
                statement.setObject(5, utc(advisory.modifiedAt()));
                statement.setString(6, advisory.contentHash());
                statement.setString(7, normalizedHash);
                statement.setString(8, affectedJson);
                statement.setObject(9, targetVersion);
                statement.setObject(10, current.id());
                statement.executeUpdate();
            }
            replaceIndexes(connection, current.id(), advisory.aliases(), affected);
            changes.fire(new SignalChanged(current.id(), false));
            return Outcome.UPDATED;
        } catch (SQLException error) {
            throw new IllegalStateException("enregistrement du signal " + advisory.externalId() + " impossible", error);
        }
    }

    private record Current(UUID id, String contentHash, String normalizedHash, Instant modifiedAt, int lastVersionNo,
            UUID currentVersionId, String rawContent) {
    }

    private static Current lockCurrent(Connection connection, String sourceId, String externalId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.id, s.content_hash, s.normalized_hash, s.modified_at,
                       (SELECT max(v.version_no) FROM signal_version v WHERE v.signal_id = s.id),
                       s.current_version_id,
                       (SELECT v.raw_content FROM signal_version v WHERE v.id = s.current_version_id)
                FROM signal s WHERE s.source = ? AND s.external_id = ? FOR UPDATE
                """)) {
            statement.setString(1, sourceId);
            statement.setString(2, externalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("signal attendu introuvable : " + externalId);
                }
                String normalized = resultSet.getString(3);
                return new Current(resultSet.getObject(1, UUID.class), resultSet.getString(2).trim(),
                        normalized == null ? null : normalized.trim(),
                        resultSet.getObject(4, OffsetDateTime.class).toInstant(), resultSet.getInt(5),
                        resultSet.getObject(6, UUID.class), resultSet.getString(7));
            }
        }
    }

    /**
     * Calcule l'empreinte normalisée de la version courante d'un signal qui n'en a pas encore et l'enregistre
     * sur le signal et sur cette version, sans créer de version (COL-10-S4). Le contenu brut courant est
     * renormalisé par le normaliseur de la source ; à défaut (source sans normaliseur, contenu devenu
     * illisible), renvoie {@code null} et la comparaison se fait sur l'empreinte du contenu brut, comme avant V18.
     */
    private String catchUp(Connection connection, String sourceId, Current current, NormalizedAdvisory advisory,
            String normalizedHash) throws SQLException {
        String hash;
        if (current.contentHash().equals(advisory.contentHash())) {
            hash = normalizedHash;
        } else {
            hash = renormalize(sourceId, advisory.externalId(), current.rawContent()).orElse(null);
        }
        if (hash == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE signal SET normalized_hash = ? WHERE id = ?")) {
            statement.setString(1, hash);
            statement.setObject(2, current.id());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE signal_version SET normalized_hash = ?
                WHERE id = ? AND NOT EXISTS (SELECT 1 FROM signal_version o
                                             WHERE o.signal_id = ? AND o.normalized_hash = ? AND o.id <> ?)
                """)) {
            statement.setString(1, hash);
            statement.setObject(2, current.currentVersionId());
            statement.setObject(3, current.id());
            statement.setString(4, hash);
            statement.setObject(5, current.currentVersionId());
            statement.executeUpdate();
        }
        return hash;
    }

    private Optional<String> renormalize(String sourceId, String externalId, String rawContent) {
        if (rawContent == null) {
            return Optional.empty();
        }
        Optional<AdvisoryFeed> feed = feeds.stream().filter(candidate -> candidate.sourceId().equals(sourceId)).findFirst();
        if (feed.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(feed.get().normalize(new RawAdvisory(externalId, rawContent)).normalizedHash());
        } catch (RuntimeException illisible) {
            LOG.warnf("empreinte normalisée du signal %s/%s non rattrapée : %s", sourceId, externalId,
                    illisible.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Version antérieure du signal dont le contenu normalisé (ou, à défaut, le contenu brut) est celui de l'avis
     * reçu ; la plus récente si plusieurs conviennent.
     */
    private static Optional<UUID> earlierVersion(Connection connection, UUID signalId, String normalizedHash, String contentHash)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM signal_version WHERE signal_id = ? AND (normalized_hash = ? OR content_hash = ?)
                ORDER BY version_no DESC LIMIT 1
                """)) {
            statement.setObject(1, signalId);
            statement.setString(2, normalizedHash);
            statement.setString(3, contentHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getObject(1, UUID.class)) : Optional.empty();
            }
        }
    }

    private static void insertVersion(Connection connection, UUID versionId, UUID signalId, int versionNo,
            NormalizedAdvisory advisory, String normalizedHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO signal_version(id, signal_id, version_no, content_hash, normalized_hash, source_modified_at,
                                           collected_at, raw_content)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                """)) {
            statement.setObject(1, versionId);
            statement.setObject(2, signalId);
            statement.setInt(3, versionNo);
            statement.setString(4, advisory.contentHash());
            statement.setString(5, normalizedHash);
            statement.setObject(6, utc(advisory.modifiedAt()));
            statement.setString(7, advisory.rawContent());
            statement.executeUpdate();
        }
    }

    private static void replaceIndexes(Connection connection, UUID signalId, List<String> aliases, List<AffectedPackage> affected)
            throws SQLException {
        try (PreparedStatement del = connection.prepareStatement("DELETE FROM signal_alias WHERE signal_id = ?")) {
            del.setObject(1, signalId);
            del.executeUpdate();
        }
        try (PreparedStatement del = connection.prepareStatement("DELETE FROM signal_package WHERE signal_id = ?")) {
            del.setObject(1, signalId);
            del.executeUpdate();
        }
        try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO signal_alias(signal_id, alias) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            for (String alias : aliases) {
                ins.setObject(1, signalId);
                ins.setString(2, cut(alias, 200));
                ins.addBatch();
            }
            ins.executeBatch();
        }
        Map<String, String> keys = new LinkedHashMap<>();
        affected.forEach(affectedPackage -> keys.putIfAbsent(affectedPackage.packageKey(), affectedPackage.ecosystem()));
        try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO signal_package(signal_id, package_key, ecosystem) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> entry : keys.entrySet()) {
                ins.setObject(1, signalId);
                ins.setString(2, cut(entry.getKey(), 500));
                ins.setString(3, cut(entry.getValue(), 100));
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    /** Traduit les paquets de l'avis ; un paquet sans clé déterminable n'est pas indexé. */
    static List<AffectedPackage> toAffected(List<NormalizedAdvisory.Affected> source) {
        List<AffectedPackage> result = new ArrayList<>();
        for (NormalizedAdvisory.Affected entry : source) {
            Optional<PackageKey.Purl> purl = PackageKey.parse(entry.purl());
            Optional<String> key = purl.map(PackageKey.Purl::key).or(() -> PackageKey.fromEcosystem(entry.ecosystem(), entry.name()));
            if (key.isEmpty()) {
                continue;
            }
            List<AffectedPackage.VersionRange> ranges = entry.ranges().stream()
                    .map(range -> new AffectedPackage.VersionRange(range.type(), range.introduced(), range.fixed(), range.lastAffected()))
                    .toList();
            result.add(new AffectedPackage(key.get(), purl.isPresent(), entry.ecosystem(), entry.name(), ranges, entry.versions()));
        }
        return result;
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("sérialisation des paquets affectés impossible", error);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
