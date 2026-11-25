package io.argos.signal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.PackageKey;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;

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
    Jdbc jdbc;

    @Transactional
    public Optional<SignalView> find(UUID id) {
        return jdbc.unique(VUE + " WHERE id = ?", Jdbc.parametres(id), SignalQueries::view);
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
        jdbc.lister(VUE + " WHERE id = ANY (?)",
                requete -> requete.setArray(1, requete.getConnection().createArrayOf("uuid", new LinkedHashSet<>(ids).toArray())),
                SignalQueries::view).forEach(signal -> lus.put(signal.id(), signal));
        Map<UUID, SignalView> ordonnes = new LinkedHashMap<>();
        for (UUID id : ids) {
            SignalView signal = lus.get(id);
            if (signal != null) {
                ordonnes.putIfAbsent(id, signal);
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
        return jdbc.lister("""
                SELECT id, version_no, content_hash, source_modified_at, collected_at FROM signal_version
                WHERE signal_id = ? ORDER BY version_no
                """, Jdbc.parametres(signalId), resultat -> new SignalVersionView(resultat.getObject(1, UUID.class),
                resultat.getInt(2), resultat.getString(3).trim(), Conversions.instant(resultat, 4),
                Conversions.instant(resultat, 5)));
    }

    /** Contenu brut reçu pour une version du signal (COL-02). */
    @Transactional
    public Optional<String> rawContent(UUID signalId, UUID versionId) {
        return jdbc.unique("SELECT raw_content FROM signal_version WHERE signal_id = ? AND id = ?",
                Jdbc.parametres(signalId, versionId), resultat -> resultat.getString(1));
    }

    /** Signaux dont un paquet affecté a l'une des clés données. */
    @Transactional
    public List<UUID> signalsAffecting(Collection<String> packageKeys) {
        if (packageKeys.isEmpty()) {
            return List.of();
        }
        return jdbc.lister("SELECT DISTINCT signal_id FROM signal_package WHERE package_key = ANY (?)",
                requete -> requete.setArray(1, requete.getConnection().createArrayOf("varchar", packageKeys.toArray())),
                resultat -> resultat.getObject(1, UUID.class));
    }

    /** Identifiants CVE référencés par les alias d'un signal (enrichissement, COL-04). */
    @Transactional
    public List<String> cveAliases(UUID signalId) {
        return jdbc.lister("""
                SELECT alias FROM signal_alias WHERE signal_id = ? AND alias LIKE 'CVE-%'
                UNION SELECT external_id FROM signal WHERE id = ? AND external_id LIKE 'CVE-%' ORDER BY 1
                """, Jdbc.parametres(signalId, signalId), resultat -> resultat.getString(1));
    }

    /** Signaux correspondant à au moins un critère de veille, du plus récent au plus ancien. */
    @Transactional
    public List<SignalView> search(Criteria criteria, int limit) {
        Set<String> ecosystems = new java.util.HashSet<>();
        Set<String> types = new java.util.HashSet<>();
        Set<String> names = new java.util.HashSet<>();
        for (String technologie : criteria.technologies()) {
            String tech = technologie.toLowerCase(Locale.ROOT).trim();
            ecosystems.addAll(TECHNOLOGY_ECOSYSTEMS.getOrDefault(tech, Set.of(tech)));
            PackageKey.typeOfEcosystem(tech).ifPresent(types::add);
            types.add(tech);
            names.add(tech);
        }
        Set<String> keys = new java.util.HashSet<>();
        for (String paquet : criteria.packages()) {
            keys.add(PackageKey.parse(paquet).map(PackageKey.Purl::key).orElse(paquet));
        }
        Set<String> sources = new java.util.HashSet<>();
        criteria.sources().forEach(source -> sources.add(source.toUpperCase(Locale.ROOT)));
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
        List<UUID> ids = jdbc.lister(sql, requete -> {
            requete.setArray(1, requete.getConnection().createArrayOf("varchar", ecosystems.toArray()));
            requete.setArray(2, requete.getConnection().createArrayOf("varchar", types.toArray()));
            requete.setArray(3, requete.getConnection().createArrayOf("varchar", names.toArray()));
            requete.setArray(4, requete.getConnection().createArrayOf("varchar", keys.toArray()));
            requete.setArray(5, requete.getConnection().createArrayOf("varchar", sources.toArray()));
            requete.setInt(6, limit);
        }, resultat -> resultat.getObject(1, UUID.class));
        return List.copyOf(findAll(ids).values());
    }

    private List<UUID> findIds(String sql, Object... params) {
        return jdbc.lister(sql, Jdbc.parametres(params), resultat -> resultat.getObject(1, UUID.class));
    }

    private static SignalView view(ResultSet resultat) throws SQLException {
        List<AffectedPackage> affected;
        try {
            affected = MAPPER.readValue(resultat.getString(11), AFFECTED);
        } catch (JsonProcessingException erreur) {
            throw new IllegalStateException("paquets affectés illisibles", erreur);
        }
        String[] aliases = (String[]) resultat.getArray(12).getArray();
        return new SignalView(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3), resultat.getString(4),
                resultat.getString(5), resultat.getString(6), Conversions.instant(resultat, 7), Conversions.instant(resultat, 8),
                Conversions.instant(resultat, 9), resultat.getObject(10, UUID.class), List.of(aliases), affected);
    }
}
