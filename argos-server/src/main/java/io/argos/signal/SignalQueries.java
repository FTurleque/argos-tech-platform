package io.argos.signal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.PackageKey;

/**
 * Consultation des signaux de sécurité. Les signaux sont globaux (ADR-0016) : aucune donnée
 * d'espace n'est exposée ici.
 */
@ApplicationScoped
public class SignalQueries {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Colonnes d'une vue de signal ({@link #view(ResultSet)}), sans clause {@code WHERE}. */
    private static final String VUE = """
            SELECT id, source, external_id, title, summary, severity, published_at, modified_at, collected_at,
                   current_version_id, affected::text,
                   ARRAY(SELECT alias FROM signal_alias a WHERE a.signal_id = s.id ORDER BY alias)
            FROM signal s""";
    private static final TypeReference<List<AffectedPackage>> AFFECTED = new TypeReference<>() {
    };

    /**
     * Correspondances indicatives technologie → écosystèmes, utilisées par les filtres de veille
     * tant que le référentiel technologique ({@code catalog}) n'existe pas.
     */
    private static final Map<String, Set<String>> TECHNOLOGY_ECOSYSTEMS = Map.ofEntries(
            Map.entry("rust", Set.of("crates.io")), Map.entry("java", Set.of("maven")),
            Map.entry("javascript", Set.of("npm")), Map.entry("node", Set.of("npm")),
            Map.entry("typescript", Set.of("npm")), Map.entry("python", Set.of("pypi")),
            Map.entry("go", Set.of("go")), Map.entry(".net", Set.of("nuget")), Map.entry("php", Set.of("packagist")),
            Map.entry("ruby", Set.of("rubygems")));

    /**
     * Écosystèmes de paquets associés à une technologie (minuscules), selon la même correspondance
     * que les filtres de veille ; la technologie elle-même à défaut.
     */
    public static Set<String> ecosystemsOf(String technology) {
        String tech = technology.toLowerCase(Locale.ROOT).trim();
        return TECHNOLOGY_ECOSYSTEMS.getOrDefault(tech, Set.of(tech));
    }

    /**
     * Termes comparés au type de PURL et au nom des paquets affectés pour une technologie
     * (minuscules) : la technologie et, si c'est un écosystème connu, son type de PURL.
     */
    public static Set<String> technologyTerms(String technology) {
        String tech = technology.toLowerCase(Locale.ROOT).trim();
        Set<String> terms = new java.util.LinkedHashSet<>();
        terms.add(tech);
        PackageKey.typeOfEcosystem(tech).ifPresent(terms::add);
        return terms;
    }

    /** Signal et sa provenance (COL-02-S1). */
    public record SignalView(UUID id, String source, String externalId, String title, String summary, String severity,
            Instant publishedAt, Instant modifiedAt, Instant collectedAt, UUID currentVersionId, List<String> aliases,
            List<AffectedPackage> affected) {
    }

    /** Version historisée d'un avis (COL-03-S2). */
    public record SignalVersionView(UUID id, int versionNo, String contentHash, Instant sourceModifiedAt,
            Instant collectedAt) {
    }

    /** Critères de veille appliqués aux signaux (ABO-04). */
    public record Criteria(Set<String> technologies, Set<String> packages, Set<String> sources) {
    }

    @Inject
    DataSource dataSource;

    @Transactional
    public Optional<SignalView> find(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(VUE + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(view(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du signal impossible", e);
        }
    }

    /**
     * Signaux désignés, lus en une seule requête (lecture groupée : la restitution de la veille ne lit pas les
     * signaux un par un, changement {@code optimiser-ma-veille}). Chaque vue est identique à celle de
     * {@link #find(UUID)} ; un identifiant inconnu est absent du résultat. Ordre du résultat : celui des
     * identifiants donnés.
     */
    @Transactional
    public Map<UUID, SignalView> findAll(Collection<UUID> ids) {
        Map<UUID, SignalView> lus = new HashMap<>();
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(VUE + " WHERE id = ANY (?)")) {
            ps.setArray(1, c.createArrayOf("uuid", new LinkedHashSet<>(ids).toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SignalView v = view(rs);
                    lus.put(v.id(), v);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des signaux impossible", e);
        }
        Map<UUID, SignalView> ordonnes = new LinkedHashMap<>();
        for (UUID id : ids) {
            SignalView v = lus.get(id);
            if (v != null) {
                ordonnes.putIfAbsent(id, v);
            }
        }
        return ordonnes;
    }

    @Transactional
    public Optional<SignalView> findByExternalId(String source, String externalId) {
        return findIds("SELECT id FROM signal WHERE source = ? AND external_id = ?", source, externalId).stream()
                .findFirst().flatMap(this::find);
    }

    @Transactional
    public List<SignalVersionView> versions(UUID signalId) {
        List<SignalVersionView> versions = new ArrayList<>();
        String sql = """
                SELECT id, version_no, content_hash, source_modified_at, collected_at FROM signal_version
                WHERE signal_id = ? ORDER BY version_no
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    versions.add(new SignalVersionView(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3).trim(),
                            instant(rs, 4), instant(rs, 5)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'historique impossible", e);
        }
        return versions;
    }

    /** Contenu brut reçu pour une version du signal (COL-02). */
    @Transactional
    public Optional<String> rawContent(UUID signalId, UUID versionId) {
        String sql = "SELECT raw_content FROM signal_version WHERE signal_id = ? AND id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, signalId);
            ps.setObject(2, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du contenu brut impossible", e);
        }
    }

    /** Signaux dont un paquet affecté a l'une des clés données. */
    @Transactional
    public List<UUID> signalsAffecting(Collection<String> packageKeys) {
        if (packageKeys.isEmpty()) {
            return List.of();
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT signal_id FROM signal_package WHERE package_key = ANY (?)")) {
            ps.setArray(1, c.createArrayOf("varchar", packageKeys.toArray()));
            return ids(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("recherche des signaux par paquet impossible", e);
        }
    }

    /** Identifiants CVE référencés par les alias d'un signal (enrichissement, COL-04). */
    @Transactional
    public List<String> cveAliases(UUID signalId) {
        List<String> cves = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT alias FROM signal_alias WHERE signal_id = ? AND alias LIKE 'CVE-%'
                UNION SELECT external_id FROM signal WHERE id = ? AND external_id LIKE 'CVE-%' ORDER BY 1
                """)) {
            ps.setObject(1, signalId);
            ps.setObject(2, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cves.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des alias impossible", e);
        }
        return cves;
    }

    /** Signaux correspondant à au moins un critère de veille, du plus récent au plus ancien. */
    @Transactional
    public List<SignalView> search(Criteria criteria, int limit) {
        Set<String> ecosystems = new java.util.HashSet<>();
        Set<String> types = new java.util.HashSet<>();
        Set<String> names = new java.util.HashSet<>();
        for (String t : criteria.technologies()) {
            String tech = t.toLowerCase(Locale.ROOT).trim();
            ecosystems.addAll(TECHNOLOGY_ECOSYSTEMS.getOrDefault(tech, Set.of(tech)));
            PackageKey.typeOfEcosystem(tech).ifPresent(types::add);
            types.add(tech);
            names.add(tech);
        }
        Set<String> keys = new java.util.HashSet<>();
        for (String p : criteria.packages()) {
            keys.add(PackageKey.parse(p).map(PackageKey.Purl::key).orElse(p));
        }
        Set<String> sources = new java.util.HashSet<>();
        criteria.sources().forEach(s -> sources.add(s.toUpperCase(Locale.ROOT)));
        String sql = """
                SELECT DISTINCT s.id, s.modified_at FROM signal s
                LEFT JOIN signal_package p ON p.signal_id = s.id
                WHERE lower(p.ecosystem) = ANY (?)
                   OR split_part(substr(p.package_key, 5), '/', 1) = ANY (?)
                   OR lower(regexp_replace(p.package_key, '^.*/', '')) = ANY (?)
                   OR p.package_key = ANY (?)
                   OR s.source = ANY (?)
                ORDER BY s.modified_at DESC LIMIT ?
                """;
        List<UUID> ids;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setArray(1, c.createArrayOf("varchar", ecosystems.toArray()));
            ps.setArray(2, c.createArrayOf("varchar", types.toArray()));
            ps.setArray(3, c.createArrayOf("varchar", names.toArray()));
            ps.setArray(4, c.createArrayOf("varchar", keys.toArray()));
            ps.setArray(5, c.createArrayOf("varchar", sources.toArray()));
            ps.setInt(6, limit);
            ids = ids(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("recherche de veille impossible", e);
        }
        return List.copyOf(findAll(ids).values());
    }

    private List<UUID> findIds(String sql, Object... params) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ids(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("requête sur les signaux impossible", e);
        }
    }

    private static List<UUID> ids(PreparedStatement ps) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getObject(1, UUID.class));
            }
        }
        return ids;
    }

    private static SignalView view(ResultSet rs) throws SQLException {
        List<AffectedPackage> affected;
        try {
            affected = MAPPER.readValue(rs.getString(11), AFFECTED);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("paquets affectés illisibles", e);
        }
        String[] aliases = (String[]) rs.getArray(12).getArray();
        return new SignalView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), instant(rs, 7), instant(rs, 8), instant(rs, 9),
                rs.getObject(10, UUID.class), List.of(aliases), affected);
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
