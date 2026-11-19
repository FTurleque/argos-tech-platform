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
 * (décision E3 du porteur du projet, 2026-11-19, qui précise D5 du 2026-11-18, Q-COR-3).
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
     * Signe de {@code a - b}, ou vide si l'une des versions n'est pas interprétable ou si l'ordre
     * dépend d'un qualificatif inconnu : un qualificatif inconnu n'est classé ni avant ni après la
     * version publiée, et le rapprochement retombe alors sur le niveau « à vérifier ».
     */
    public static Optional<Integer> compare(String a, String b) {
        Optional<List<Object>> ta = tokens(a);
        Optional<List<Object>> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return Optional.empty();
        }
        List<Object> x = ta.get();
        List<Object> y = tb.get();
        int n = Math.max(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            Object p = i < x.size() ? x.get(i) : null;
            Object q = i < y.size() ? y.get(i) : null;
            Integer c = compareToken(p, q);
            if (c == null) {
                return Optional.empty();
            }
            if (c != 0) {
                return Optional.of(Integer.signum(c));
            }
        }
        return Optional.of(0);
    }

    /**
     * Signe de {@code a - b} pour un écosystème. Pour Maven, ordre de {@link ComparableVersion} sur les
     * versions normalisées (préfixe {@code v} et métadonnées de build {@code +…} retirés) ; vide si
     * l'une des versions n'est pas interprétable au sens de {@link #mavenInterpretable(String)}. Pour
     * les autres écosystèmes, comparaison générique {@link #compare(String, String)}.
     */
    public static Optional<Integer> compare(String ecosystem, String a, String b) {
        if (!maven(ecosystem)) {
            return compare(a, b);
        }
        if (!mavenInterpretable(a) || !mavenInterpretable(b)) {
            return Optional.empty();
        }
        int c = new ComparableVersion(normalized(a)).compareTo(new ComparableVersion(normalized(b)));
        return Optional.of(Integer.signum(c));
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
        String v = normalized(version);
        if (v.isEmpty() || !Character.isDigit(v.charAt(0))) {
            return false;
        }
        for (char ch : v.toCharArray()) {
            boolean ok = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || ch == '.' || ch == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** Vrai pour l'écosystème Maven, désigné par son nom OSV ({@code Maven}) ou son type PURL ({@code maven}). */
    public static boolean maven(String ecosystem) {
        return ecosystem != null && "maven".equalsIgnoreCase(ecosystem.strip());
    }

    public static boolean interpretable(String version) {
        return tokens(version).isPresent();
    }

    /** Comparaison de deux segments ; {@code null} si l'ordre dépend d'un qualificatif inconnu. */
    private static Integer compareToken(Object p, Object q) {
        if (p == null && q == null) {
            return 0;
        }
        if (p == null) {
            Integer s = sign(q);
            return s == null ? null : -s;
        }
        if (q == null) {
            return sign(p);
        }
        if (p instanceof Long lp && q instanceof Long lq) {
            return Long.compare(lp, lq);
        }
        if (p instanceof Long) {
            return 1; // 1.0.1 > 1.0-rc
        }
        if (q instanceof Long) {
            return -1;
        }
        String sp = (String) p;
        String sq = (String) q;
        if (sp.equals(sq)) {
            return 0;
        }
        Integer rp = QUALIFIERS.get(sp);
        Integer rq = QUALIFIERS.get(sq);
        if (rp == null || rq == null) {
            return null;
        }
        return Integer.compare(rp, rq);
    }

    /**
     * Position d'un segment face à un segment absent : un segment numérique non nul ou un
     * qualificatif « après » allonge la version ; {@code null} pour un qualificatif inconnu.
     */
    private static Integer sign(Object token) {
        if (token instanceof Long l) {
            return l == 0 ? 0 : 1;
        }
        Integer rank = QUALIFIERS.get((String) token);
        return rank == null ? null : Integer.signum(rank);
    }

    /** Version en minuscules, sans préfixe {@code v} ni métadonnées de build ({@code +…}). */
    private static String normalized(String version) {
        String v = version.strip().toLowerCase(Locale.ROOT);
        if (v.startsWith("v")) {
            v = v.substring(1);
        }
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    private static Optional<List<Object>> tokens(String version) {
        if (version == null) {
            return Optional.empty();
        }
        String v = normalized(version);
        if (v.isEmpty() || !Character.isDigit(v.charAt(0))) {
            return Optional.empty();
        }
        List<Object> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Boolean digits = null;
        for (char ch : v.toCharArray()) {
            if (ch == '.' || ch == '-' || ch == '_') {
                flush(tokens, current, digits);
                digits = null;
                continue;
            }
            boolean isDigit = Character.isDigit(ch);
            if (digits != null && digits != isDigit) {
                flush(tokens, current, digits);
            }
            current.append(ch);
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
        String s = current.toString();
        if (Boolean.TRUE.equals(digits)) {
            try {
                tokens.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                tokens.add(s);
            }
        } else {
            tokens.add(s);
        }
        current.setLength(0);
    }
}
