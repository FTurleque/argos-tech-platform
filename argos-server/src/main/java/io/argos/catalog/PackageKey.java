package io.argos.catalog;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

/**
 * Clé d'identification d'un paquet, indépendante de sa version : {@code pkg:type/namespace/name}
 * (spécification Package URL). Signaux et inventaires utilisent la même clé, ce qui rend la
 * corrélation PURL déterministe (COR-01).
 *
 * <p>Normalisation appliquée : type en minuscules ; pour {@code npm}, {@code pypi},
 * {@code nuget}, {@code cargo}, {@code gem} et {@code golang}, namespace et nom en minuscules ;
 * pour {@code pypi}, « _ » et « . » remplacés par « - ». Qualificatifs et sous-chemin ignorés.
 */
public final class PackageKey {

    /** PURL décomposé : clé sans version et version éventuelle. */
    public record Purl(String type, String key, String version) {
    }

    private static final Logger LOG = Logger.getLogger(PackageKey.class);

    /** Préfixe de tout PURL. */
    private static final String PREFIXE = "pkg:";

    /** Écosystèmes OSV → type PURL. */
    private static final Map<String, String> ECOSYSTEM_TYPES = Map.ofEntries(
            Map.entry("maven", "maven"), Map.entry("npm", "npm"), Map.entry("pypi", "pypi"),
            Map.entry("crates.io", "cargo"), Map.entry("go", "golang"), Map.entry("nuget", "nuget"),
            Map.entry("rubygems", "gem"), Map.entry("packagist", "composer"), Map.entry("pub", "pub"),
            Map.entry("hex", "hex"));

    private PackageKey() {
    }

    /**
     * Décompose un PURL ; vide s'il n'est pas conforme à {@code pkg:type/[namespace/]name[@version]} ou si l'un de ses
     * segments est mal encodé (par exemple {@code %zz}) : un PURL mal encodé est un PURL illisible, que chaque appelant
     * traite selon sa règle (COL-11, INV-11, RAD-07). Aucune chaîne ne lève d'erreur d'exécution.
     */
    public static Optional<Purl> parse(String purl) {
        if (purl == null || !purl.startsWith(PREFIXE)) {
            return Optional.empty();
        }
        String rest = sansQualificatifs(purl.substring(PREFIXE.length()));
        int versionSeparator = rest.lastIndexOf('@');
        if (versionSeparator < 0 || versionSeparator <= rest.indexOf('/')) {
            return paquet(rest, "");
        }
        return decode(rest.substring(versionSeparator + 1))
                .flatMap(version -> paquet(rest.substring(0, versionSeparator), version));
    }

    /** PURL sans ses qualificatifs ({@code ?…}) ni son sous-chemin ({@code #…}). */
    private static String sansQualificatifs(String rest) {
        int cut = indexOfAny(rest, '?', '#');
        return cut >= 0 ? rest.substring(0, cut) : rest;
    }

    /** Type, namespace et nom de {@code type/[namespace/]name} ; {@code version} vide : PURL sans version. */
    private static Optional<Purl> paquet(String chemin, String version) {
        String sansBarres = chemin.replaceFirst("^/+", "");
        int slash = sansBarres.indexOf('/');
        if (slash <= 0 || slash == sansBarres.length() - 1) {
            return Optional.empty();
        }
        String type = sansBarres.substring(0, slash).toLowerCase(Locale.ROOT);
        String path = sansBarres.substring(slash + 1);
        int last = path.lastIndexOf('/');
        Optional<String> namespace = last < 0 ? Optional.of("") : decode(path.substring(0, last));
        Optional<String> name = decode(path.substring(last + 1)).filter(valeur -> !valeur.isBlank());
        if (namespace.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Purl(type, key(type, namespace.get(), name.get()), version.isBlank() ? null : version));
    }

    /**
     * Clé déduite de l'écosystème et du nom quand la source ne fournit pas de PURL ;
     * vide pour un écosystème inconnu. Pour Maven, le nom {@code groupe:artefact} est découpé.
     */
    public static Optional<String> fromEcosystem(String ecosystem, String name) {
        if (ecosystem == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        String eco = ecosystem.toLowerCase(Locale.ROOT);
        int colon = eco.indexOf(':');
        if (colon > 0) {
            eco = eco.substring(0, colon);
        }
        String type = ECOSYSTEM_TYPES.get(eco);
        if (type == null) {
            return Optional.empty();
        }
        if ("maven".equals(type)) {
            int sep = name.indexOf(':');
            return sep <= 0 ? Optional.empty() : Optional.of(key(type, name.substring(0, sep), name.substring(sep + 1)));
        }
        int last = name.lastIndexOf('/');
        String namespace = last < 0 ? null : name.substring(0, last);
        return Optional.of(key(type, namespace, name.substring(last + 1)));
    }

    /** Type PURL d'un écosystème OSV, s'il est connu. */
    public static Optional<String> typeOfEcosystem(String ecosystem) {
        return ecosystem == null ? Optional.empty()
                : Optional.ofNullable(ECOSYSTEM_TYPES.get(ecosystem.toLowerCase(Locale.ROOT)));
    }

    /** Construit la clé normalisée. */
    public static String key(String type, String namespace, String name) {
        String normalizedType = type.toLowerCase(Locale.ROOT);
        boolean lower = switch (normalizedType) {
            case "npm", "pypi", "nuget", "cargo", "gem", "golang" -> true;
            default -> false;
        };
        String normalizedNamespace = namespace == null || namespace.isBlank() ? null
                : (lower ? namespace.toLowerCase(Locale.ROOT) : namespace);
        String normalizedName = lower ? name.toLowerCase(Locale.ROOT) : name;
        if ("pypi".equals(normalizedType)) {
            normalizedName = normalizedName.replace('_', '-').replace('.', '-');
        }
        return "pkg:" + normalizedType + "/" + (normalizedNamespace == null ? "" : normalizedNamespace + "/") + normalizedName;
    }

    private static int indexOfAny(String text, char first, char second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        if (firstIndex < 0) {
            return secondIndex;
        }
        return secondIndex < 0 ? firstIndex : Math.min(firstIndex, secondIndex);
    }

    /** Segment décodé ; vide si son encodage en pourcentage est invalide. */
    private static Optional<String> decode(String value) {
        try {
            return Optional.of(URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException encodageInvalide) {
            LOG.debugf(encodageInvalide, "segment de PURL mal encodé : PURL illisible");
            return Optional.empty();
        }
    }
}
