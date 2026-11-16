package io.argos.impact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Comparaison générique de versions pour les plages SEMVER et ECOSYSTEM : segments numériques
 * comparés numériquement, qualificatifs ordonnés {@code alpha < beta < milestone < rc < snapshot
 * < (version publiée) < sp}, préfixe {@code v} ignoré, métadonnées de build ({@code +…}) ignorées.
 *
 * <p>Une version qui ne commence pas par un chiffre n'est pas interprétable : la comparaison
 * renvoie vide et l'impact éventuel est classé « à vérifier » (Q-2, Q-COR-3).
 */
public final class VersionComparator {

    private static final Map<String, Integer> QUALIFIERS = Map.ofEntries(
            Map.entry("alpha", -5), Map.entry("a", -5), Map.entry("beta", -4), Map.entry("b", -4),
            Map.entry("milestone", -3), Map.entry("m", -3), Map.entry("rc", -2), Map.entry("cr", -2),
            Map.entry("pre", -2), Map.entry("preview", -2), Map.entry("snapshot", -1), Map.entry("dev", -6),
            Map.entry("ga", 0), Map.entry("final", 0), Map.entry("release", 0), Map.entry("sp", 1));

    private VersionComparator() {
    }

    /** Signe de {@code a - b}, ou vide si l'une des versions n'est pas interprétable. */
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
            int c = compareToken(p, q);
            if (c != 0) {
                return Optional.of(Integer.signum(c));
            }
        }
        return Optional.of(0);
    }

    public static boolean interpretable(String version) {
        return tokens(version).isPresent();
    }

    private static int compareToken(Object p, Object q) {
        if (p == null && q == null) {
            return 0;
        }
        if (p == null) {
            return -sign(q);
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
        Integer rp = QUALIFIERS.get(sp);
        Integer rq = QUALIFIERS.get(sq);
        if (rp != null && rq != null) {
            return Integer.compare(rp, rq);
        }
        if (rp != null) {
            return rp < 0 ? -1 : 1;
        }
        if (rq != null) {
            return rq < 0 ? 1 : -1;
        }
        return sp.compareTo(sq);
    }

    /** Position d'un segment absent : un segment numérique non nul ou un qualificatif « après » allonge la version. */
    private static int sign(Object token) {
        if (token instanceof Long l) {
            return l == 0 ? 0 : 1;
        }
        Integer rank = QUALIFIERS.get((String) token);
        if (rank == null) {
            return -1;
        }
        return Integer.signum(rank);
    }

    private static Optional<List<Object>> tokens(String version) {
        if (version == null) {
            return Optional.empty();
        }
        String v = version.strip().toLowerCase(Locale.ROOT);
        if (v.startsWith("v")) {
            v = v.substring(1);
        }
        int plus = v.indexOf('+');
        if (plus >= 0) {
            v = v.substring(0, plus);
        }
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
