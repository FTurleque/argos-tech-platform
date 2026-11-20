package io.argos.impact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Comparaison générique de versions pour les plages SEMVER et ECOSYSTEM : segments numériques
 * comparés numériquement, qualificatifs ordonnés {@code alpha < beta < milestone < rc < snapshot
 * < (version publiée) < sp}, préfixe {@code v} ignoré, métadonnées de build ({@code +…}) ignorées.
 *
 * <p>Une version qui ne commence pas par un chiffre n'est pas interprétable : la comparaison
 * renvoie vide et l'impact éventuel est classé « à vérifier » (Q-2).
 *
 * <p>Quand l'ordre dépend d'un qualificatif inconnu ({@code jre}, {@code android}…), la comparaison
 * générique renvoie vide : les écosystèmes autres que Maven gardent alors le niveau « à vérifier ».
 *
 * <p>Pour l'écosystème Maven, {@link #compare(String, String, String)} n'utilise pas l'ordre générique :
 * toute comparaison passe par {@link ComparableVersion} de la bibliothèque {@code maven-artifact}
 * (décision E3 du porteur du projet, 2026-11-19, qui révise D5 du 2026-11-18, Q-COR-3).
 */
public final class VersionComparator {

    /**
     * Rang des qualificatifs connus par rapport à la version publiée (rang 0) : négatif pour une
     * pré-version (Maven, SemVer, PEP 440 {@code a}, {@code b}, {@code c}, {@code rc}, {@code dev}),
     * positif pour une version postérieure (Maven {@code sp}, PEP 440 {@code post}, {@code rev}, {@code r}).
     */
    private static final Map<String, Integer> QUALIFIERS = Map.ofEntries(
            Map.entry("alpha", -5), Map.entry("a", -5), Map.entry("beta", -4), Map.entry("b", -4),
            Map.entry("milestone", -3), Map.entry("m", -3), Map.entry("rc", -2), Map.entry("cr", -2),
            Map.entry("c", -2), Map.entry("pre", -2), Map.entry("preview", -2), Map.entry("snapshot", -1),
            Map.entry("dev", -6), Map.entry("ga", 0), Map.entry("final", 0), Map.entry("release", 0),
            Map.entry("sp", 1), Map.entry("post", 1), Map.entry("rev", 1), Map.entry("r", 1));

    private VersionComparator() {
    }

    /**
     * Signe de {@code version - reference}, ou vide si l'une des versions n'est pas interprétable ou si
     * l'ordre dépend d'un qualificatif inconnu : un qualificatif inconnu n'est classé ni avant ni après la
     * version publiée, et le rapprochement retombe alors sur le niveau « à vérifier ».
     */
    public static Optional<Integer> compare(String version, String reference) {
        Optional<List<Object>> versionTokens = tokens(version);
        Optional<List<Object>> referenceTokens = tokens(reference);
        if (versionTokens.isEmpty() || referenceTokens.isEmpty()) {
            return Optional.empty();
        }
        List<Object> left = versionTokens.get();
        List<Object> right = referenceTokens.get();
        int length = Math.max(left.size(), right.size());
        for (int index = 0; index < length; index++) {
            Integer order = compareToken(index < left.size() ? left.get(index) : null, index < right.size() ? right.get(index) : null);
            if (order == null) {
                return Optional.empty();
            }
            if (order != 0) {
                return Optional.of(Integer.signum(order));
            }
        }
        return Optional.of(0);
    }

    /**
     * Signe de {@code version - reference} pour un écosystème. Pour Maven, ordre de {@link ComparableVersion}
     * sur les versions normalisées (préfixe {@code v} et métadonnées de build {@code +…} retirés) ; vide si
     * l'une des versions n'est pas interprétable au sens de {@link #mavenInterpretable(String)}. Pour les
     * autres écosystèmes, comparaison générique {@link #compare(String, String)}.
     */
    public static Optional<Integer> compare(String ecosystem, String version, String reference) {
        if (!maven(ecosystem)) {
            return compare(version, reference);
        }
        if (!mavenInterpretable(version) || !mavenInterpretable(reference)) {
            return Optional.empty();
        }
        int order = new ComparableVersion(normalized(version)).compareTo(new ComparableVersion(normalized(reference)));
        return Optional.of(Integer.signum(order));
    }

    /**
     * Version Maven interprétable : après normalisation, elle commence par un chiffre et ne contient que
     * des chiffres, des lettres ASCII et les séparateurs {@code .} et {@code -} de Maven. Un autre
     * caractère ({@code _}, {@code ~}…) n'est pas un séparateur pour {@link ComparableVersion} : la
     * version reste non interprétable et l'impact éventuel porte le niveau « à vérifier » (COR-02).
     */
    public static boolean mavenInterpretable(String version) {
        if (version == null) {
            return false;
        }
        String normalizedVersion = normalized(version);
        return startsWithDigit(normalizedVersion) && normalizedVersion.chars().allMatch(VersionComparator::mavenCharacter);
    }

    /** Caractère admis dans une version Maven normalisée : chiffre, lettre ASCII minuscule ou séparateur Maven. */
    private static boolean mavenCharacter(int character) {
        return asciiDigit(character) || asciiLowercaseLetter(character) || mavenSeparator(character);
    }

    private static boolean asciiDigit(int character) {
        return character >= '0' && character <= '9';
    }

    private static boolean asciiLowercaseLetter(int character) {
        return character >= 'a' && character <= 'z';
    }

    /** Séparateurs de segments reconnus par {@link ComparableVersion} : {@code .} et {@code -}. */
    private static boolean mavenSeparator(int character) {
        return character == '.' || character == '-';
    }

    private static boolean startsWithDigit(String normalizedVersion) {
        return !normalizedVersion.isEmpty() && Character.isDigit(normalizedVersion.charAt(0));
    }

    /** Vrai pour l'écosystème Maven, désigné par son nom OSV ({@code Maven}) ou son type PURL ({@code maven}). */
    public static boolean maven(String ecosystem) {
        return ecosystem != null && "maven".equalsIgnoreCase(ecosystem.strip());
    }

    public static boolean interpretable(String version) {
        return tokens(version).isPresent();
    }

    /** Comparaison de deux segments ; {@code null} si l'ordre dépend d'un qualificatif inconnu. */
    private static Integer compareToken(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            Integer rightSign = sign(right);
            return rightSign == null ? null : -rightSign;
        }
        if (right == null) {
            return sign(left);
        }
        if (left instanceof Long leftNumber && right instanceof Long rightNumber) {
            return Long.compare(leftNumber, rightNumber);
        }
        if (left instanceof Long) {
            return 1; // 1.0.1 > 1.0-rc
        }
        if (right instanceof Long) {
            return -1;
        }
        String leftQualifier = (String) left;
        String rightQualifier = (String) right;
        if (leftQualifier.equals(rightQualifier)) {
            return 0;
        }
        Integer leftRank = QUALIFIERS.get(leftQualifier);
        Integer rightRank = QUALIFIERS.get(rightQualifier);
        if (leftRank == null || rightRank == null) {
            return null;
        }
        return Integer.compare(leftRank, rightRank);
    }

    /**
     * Position d'un segment face à un segment absent : un segment numérique non nul ou un
     * qualificatif « après » allonge la version ; {@code null} pour un qualificatif inconnu.
     */
    private static Integer sign(Object token) {
        if (token instanceof Long number) {
            return number == 0 ? 0 : 1;
        }
        Integer rank = QUALIFIERS.get((String) token);
        return rank == null ? null : Integer.signum(rank);
    }

    /** Version en minuscules, sans préfixe {@code v} ni métadonnées de build ({@code +…}). */
    private static String normalized(String version) {
        String lowerCase = version.strip().toLowerCase(Locale.ROOT);
        String withoutPrefix = lowerCase.startsWith("v") ? lowerCase.substring(1) : lowerCase;
        int plus = withoutPrefix.indexOf('+');
        return plus >= 0 ? withoutPrefix.substring(0, plus) : withoutPrefix;
    }

    private static Optional<List<Object>> tokens(String version) {
        if (version == null) {
            return Optional.empty();
        }
        String normalizedVersion = normalized(version);
        if (!startsWithDigit(normalizedVersion)) {
            return Optional.empty();
        }
        List<Object> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Boolean digits = null;
        for (char character : normalizedVersion.toCharArray()) {
            if (character == '.' || character == '-' || character == '_') {
                flush(tokens, current, digits);
                digits = null;
                continue;
            }
            boolean isDigit = Character.isDigit(character);
            if (digits != null && digits != isDigit) {
                flush(tokens, current, digits);
            }
            current.append(character);
            digits = isDigit;
        }
        flush(tokens, current, digits);
        // Les zéros finaux ne changent pas la version : 1.0 == 1.0.0.
        while (tokens.size() > 1 && Long.valueOf(0).equals(tokens.get(tokens.size() - 1))) {
            tokens.remove(tokens.size() - 1);
        }
        return Optional.of(tokens);
    }

    private static void flush(List<Object> tokens, StringBuilder current, Boolean digits) {
        if (current.isEmpty()) {
            return;
        }
        String segment = current.toString();
        if (Boolean.TRUE.equals(digits)) {
            try {
                tokens.add(Long.parseLong(segment));
            } catch (NumberFormatException erreur) {
                tokens.add(segment);
            }
        } else {
            tokens.add(segment);
        }
        current.setLength(0);
    }
}
