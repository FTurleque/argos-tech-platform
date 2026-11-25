package io.argos.signal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.PackageKey;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;
import io.argos.ingestion.AdvisoryFeed;
import io.argos.ingestion.AdvisoryRejectedException;
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

    private static final int TITLE_MAX = 1000;
    private static final int SEVERITY_MAX = 300;
    private static final int ALIAS_MAX = 200;
    private static final int PACKAGE_KEY_MAX = 500;
    private static final int ECOSYSTEM_MAX = 100;

    @Inject
    Jdbc jdbc;

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
        UUID signalId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        boolean inserted = jdbc.modifier("""
                INSERT INTO signal(id, source, external_id, title, summary, severity, published_at, modified_at,
                                   collected_at, updated_at, content_hash, normalized_hash, affected,
                                   current_version_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, ?::jsonb, ?)
                ON CONFLICT (source, external_id) DO NOTHING
                """, Jdbc.parametres(signalId, sourceId, advisory.externalId(), Conversions.couper(advisory.title(), TITLE_MAX),
                advisory.summary(), Conversions.couper(advisory.severity(), SEVERITY_MAX),
                Conversions.utc(advisory.publishedAt()), Conversions.utc(advisory.modifiedAt()), advisory.contentHash(),
                normalizedHash, affectedJson, versionId)) == 1;
        if (inserted) {
            insertVersion(versionId, signalId, 1, advisory, normalizedHash);
            replaceIndexes(signalId, advisory.aliases(), affected);
            changes.fire(new SignalChanged(signalId, true));
            return Outcome.CREATED;
        }

        Current current = lockCurrent(sourceId, advisory.externalId());
        String currentHash = current.normalizedHash();
        if (currentHash == null) {
            // Signal antérieur à l'empreinte normalisée (V18) : rattrapage sans nouvelle version (COL-10-S4).
            currentHash = catchUp(sourceId, current, advisory, normalizedHash);
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
        Optional<UUID> earlier = earlierVersion(current.id(), normalizedHash, advisory.contentHash());
        UUID targetVersion;
        if (earlier.isPresent()) {
            // Contenu revenu à celui d'une version antérieure : le signal est mis à jour et repointe vers
            // cette version, sans la dupliquer (COL-10, décision J2 ; invariant INV-A1).
            targetVersion = earlier.get();
        } else {
            insertVersion(versionId, current.id(), current.lastVersionNo() + 1, advisory, normalizedHash);
            targetVersion = versionId;
        }
        jdbc.modifier("""
                UPDATE signal SET title = ?, summary = ?, severity = ?, published_at = ?, modified_at = ?,
                       updated_at = now(), content_hash = ?, normalized_hash = ?, affected = ?::jsonb,
                       current_version_id = ?
                WHERE id = ?
                """, Jdbc.parametres(Conversions.couper(advisory.title(), TITLE_MAX), advisory.summary(),
                Conversions.couper(advisory.severity(), SEVERITY_MAX), Conversions.utc(advisory.publishedAt()),
                Conversions.utc(advisory.modifiedAt()), advisory.contentHash(), normalizedHash, affectedJson, targetVersion,
                current.id()));
        replaceIndexes(current.id(), advisory.aliases(), affected);
        changes.fire(new SignalChanged(current.id(), false));
        return Outcome.UPDATED;
    }

    private record Current(UUID id, String contentHash, String normalizedHash, Instant modifiedAt, int lastVersionNo,
            UUID currentVersionId, String rawContent) {
    }

    private Current lockCurrent(String sourceId, String externalId) {
        return jdbc.unique("""
                SELECT s.id, s.content_hash, s.normalized_hash, s.modified_at,
                       (SELECT max(v.version_no) FROM signal_version v WHERE v.signal_id = s.id),
                       s.current_version_id,
                       (SELECT v.raw_content FROM signal_version v WHERE v.id = s.current_version_id)
                FROM signal s WHERE s.source = ? AND s.external_id = ? FOR UPDATE
                """, Jdbc.parametres(sourceId, externalId), resultSet -> {
                    String normalized = resultSet.getString(3);
                    return new Current(resultSet.getObject(1, UUID.class), resultSet.getString(2).trim(),
                            normalized == null ? null : normalized.trim(), Conversions.instant(resultSet, 4),
                            resultSet.getInt(5), resultSet.getObject(6, UUID.class), resultSet.getString(7));
                }).orElseThrow(() -> new IllegalStateException("signal attendu introuvable : " + externalId));
    }

    /**
     * Calcule l'empreinte normalisée de la version courante d'un signal qui n'en a pas encore et l'enregistre
     * sur le signal et sur cette version, sans créer de version (COL-10-S4). Le contenu brut courant est
     * renormalisé par le normaliseur de la source ; à défaut (source sans normaliseur, contenu devenu
     * illisible), renvoie {@code null} et la comparaison se fait sur l'empreinte du contenu brut, comme avant V18.
     */
    private String catchUp(String sourceId, Current current, NormalizedAdvisory advisory, String normalizedHash) {
        String hash;
        if (current.contentHash().equals(advisory.contentHash())) {
            hash = normalizedHash;
        } else {
            hash = renormalize(sourceId, advisory.externalId(), current.rawContent()).orElse(null);
        }
        if (hash == null) {
            return null;
        }
        jdbc.modifier("UPDATE signal SET normalized_hash = ? WHERE id = ?", Jdbc.parametres(hash, current.id()));
        jdbc.modifier("""
                UPDATE signal_version SET normalized_hash = ?
                WHERE id = ? AND NOT EXISTS (SELECT 1 FROM signal_version o
                                             WHERE o.signal_id = ? AND o.normalized_hash = ? AND o.id <> ?)
                """, Jdbc.parametres(hash, current.currentVersionId(), current.id(), hash, current.currentVersionId()));
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
        } catch (AdvisoryRejectedException illisible) {
            LOG.warnf(illisible, "empreinte normalisée du signal %s/%s non rattrapée : %s", sourceId, externalId,
                    illisible.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Version antérieure du signal dont le contenu normalisé (ou, à défaut, le contenu brut) est celui de l'avis
     * reçu ; la plus récente si plusieurs conviennent.
     */
    private Optional<UUID> earlierVersion(UUID signalId, String normalizedHash, String contentHash) {
        return jdbc.unique("""
                SELECT id FROM signal_version WHERE signal_id = ? AND (normalized_hash = ? OR content_hash = ?)
                ORDER BY version_no DESC LIMIT 1
                """, Jdbc.parametres(signalId, normalizedHash, contentHash), resultSet -> resultSet.getObject(1, UUID.class));
    }

    private void insertVersion(UUID versionId, UUID signalId, int versionNo, NormalizedAdvisory advisory,
            String normalizedHash) {
        jdbc.modifier("""
                INSERT INTO signal_version(id, signal_id, version_no, content_hash, normalized_hash, source_modified_at,
                                           collected_at, raw_content)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                """, Jdbc.parametres(versionId, signalId, versionNo, advisory.contentHash(), normalizedHash,
                Conversions.utc(advisory.modifiedAt()), advisory.rawContent()));
    }

    private void replaceIndexes(UUID signalId, List<String> aliases, List<AffectedPackage> affected) {
        jdbc.modifier("DELETE FROM signal_alias WHERE signal_id = ?", Jdbc.parametres(signalId));
        jdbc.modifier("DELETE FROM signal_package WHERE signal_id = ?", Jdbc.parametres(signalId));
        for (String alias : aliases) {
            jdbc.modifier("INSERT INTO signal_alias(signal_id, alias) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    Jdbc.parametres(signalId, Conversions.couper(alias, ALIAS_MAX)));
        }
        Map<String, String> keys = new LinkedHashMap<>();
        affected.forEach(affectedPackage -> keys.putIfAbsent(affectedPackage.packageKey(), affectedPackage.ecosystem()));
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            jdbc.modifier("INSERT INTO signal_package(signal_id, package_key, ecosystem) VALUES (?, ?, ?)",
                    Jdbc.parametres(signalId, Conversions.couper(entry.getKey(), PACKAGE_KEY_MAX),
                            Conversions.couper(entry.getValue(), ECOSYSTEM_MAX)));
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
}
