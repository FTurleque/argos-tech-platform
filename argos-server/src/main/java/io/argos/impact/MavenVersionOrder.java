package io.argos.impact;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ordre des versions de l'écosystème Maven, selon l'algorithme de {@code ComparableVersion} de
 * Maven 3.9 (décision D5 du porteur du projet, 2026-11-18, question Q-COR-3) :
 *
 * <ul>
 *   <li>la version est découpée aux séparateurs {@code .} et {@code -} et aux transitions entre
 *       chiffres et lettres ; un {@code -} ou une transition ouvre une sous-liste ;</li>
 *   <li>les segments numériques se comparent numériquement et suivent tout qualificatif ;</li>
 *   <li>les qualificatifs connus s'ordonnent {@code alpha < beta < milestone < rc < snapshot <
 *       (version publiée) < sp}, avec les alias {@code a1}, {@code b1}, {@code m1} (suivis d'un
 *       chiffre), {@code cr = rc} et {@code ga = final = release = (version publiée)} ;</li>
 *   <li>un qualificatif inconnu suit tous les qualificatifs connus, version publiée comprise, et
 *       les qualificatifs inconnus s'ordonnent entre eux par ordre lexical : {@code 32.0.0-android
 *       < 32.0.0-jre}.</li>
 * </ul>
 *
 * <p>Implémentation propre au dépôt, sans dépendance ; sa conformité à {@code ComparableVersion}
 * est vérifiée par {@code MavenVersionOrderTest} sur les exemples de la documentation de Maven.
 */
public final class MavenVersionOrder {

    /** Qualificatifs connus dans leur ordre ; la chaîne vide représente la version publiée. */
    private static final List<String> KNOWN = List.of("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");

    private static final Map<String, String> ALIASES = Map.of("ga", "", "final", "", "release", "", "cr", "rc");

    /** Rang comparable de la version publiée. */
    private static final String RELEASE = rank("");

    private MavenVersionOrder() {
    }

    /** Signe de {@code a - b} dans l'ordre Maven ; toute chaîne non nulle est ordonnable. */
    public static int compare(String a, String b) {
        return Integer.signum(parse(a).compareTo(parse(b)));
    }

    /** Segment d'une version : nombre, qualificatif ou sous-liste. */
    private sealed interface Item permits Num, Qual, Sub {

        /** Comparaison à un autre segment ; {@code other} vaut {@code null} pour un segment absent. */
        int compareTo(Item other);

        /** Segment neutre retiré en fin de liste : 0, version publiée, liste vide. */
        boolean neutral();
    }

    private record Num(BigInteger value) implements Item {

        @Override
        public int compareTo(Item other) {
            if (other == null) {
                return value.signum() == 0 ? 0 : 1;
            }
            return other instanceof Num n ? value.compareTo(n.value) : 1; // 1.1 > 1-sp, 1.1 > 1-1
        }

        @Override
        public boolean neutral() {
            return value.signum() == 0;
        }
    }

    private record Qual(String value) implements Item {

        static Qual of(String raw, boolean followedByDigit) {
            String v = raw;
            if (followedByDigit && v.length() == 1) {
                // a1 = alpha-1, b1 = beta-1, m1 = milestone-1
                v = switch (v.charAt(0)) {
                    case 'a' -> "alpha";
                    case 'b' -> "beta";
                    case 'm' -> "milestone";
                    default -> v;
                };
            }
            return new Qual(ALIASES.getOrDefault(v, v));
        }

        @Override
        public int compareTo(Item other) {
            if (other == null) {
                return rank(value).compareTo(RELEASE); // 1-rc < 1, 1-sp > 1
            }
            return other instanceof Qual q ? rank(value).compareTo(rank(q.value)) : -1; // 1-sp < 1.1, 1-sp < 1-1
        }

        @Override
        public boolean neutral() {
            return rank(value).equals(RELEASE);
        }
    }

    private record Sub(List<Item> items) implements Item {

        Sub() {
            this(new ArrayList<>());
        }

        @Override
        public int compareTo(Item other) {
            if (other == null) {
                for (Item i : items) {
                    int c = i.compareTo(null);
                    if (c != 0) {
                        return c;
                    }
                }
                return 0;
            }
            if (other instanceof Num) {
                return -1; // 1-1 < 1.1
            }
            if (other instanceof Qual) {
                return 1; // 1-1 > 1-sp
            }
            Sub s = (Sub) other;
            int n = Math.max(items.size(), s.items.size());
            for (int k = 0; k < n; k++) {
                Item l = k < items.size() ? items.get(k) : null;
                Item r = k < s.items.size() ? s.items.get(k) : null;
                int c = l == null ? (r == null ? 0 : -r.compareTo(null)) : l.compareTo(r);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        }

        @Override
        public boolean neutral() {
            return items.isEmpty();
        }

        /** Retire les segments neutres finaux, sans franchir une sous-liste non vide. */
        void normalize() {
            for (int k = items.size() - 1; k >= 0; k--) {
                Item last = items.get(k);
                if (last.neutral()) {
                    items.remove(k);
                } else if (!(last instanceof Sub)) {
                    break;
                }
            }
        }
    }

    /** Rang lexicalement comparable : indice d'un qualificatif connu, sinon « 7- » suivi du qualificatif. */
    private static String rank(String qualifier) {
        int i = KNOWN.indexOf(qualifier);
        return i < 0 ? KNOWN.size() + "-" + qualifier : Integer.toString(i);
    }

    private static Sub parse(String version) {
        String v = version.toLowerCase(Locale.ENGLISH);
        Sub root = new Sub();
        Deque<Sub> opened = new ArrayDeque<>();
        opened.push(root);
        Sub list = root;
        boolean digits = false;
        int start = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '.' || c == '-') {
                list.items.add(i == start ? new Num(BigInteger.ZERO) : item(digits, v.substring(start, i)));
                start = i + 1;
                if (c == '-') {
                    list = open(list, opened);
                }
            } else if (Character.isDigit(c)) {
                if (!digits && i > start) {
                    // Qualificatif suivi d'un chiffre (« rc1 ») : sous-liste propre, puis le nombre dans une autre.
                    if (!list.items.isEmpty()) {
                        list = open(list, opened);
                    }
                    list.items.add(Qual.of(v.substring(start, i), true));
                    start = i;
                    list = open(list, opened);
                }
                digits = true;
            } else {
                if (digits && i > start) {
                    list.items.add(item(true, v.substring(start, i)));
                    start = i;
                    list = open(list, opened);
                }
                digits = false;
            }
        }
        if (v.length() > start) {
            // « .X » final traité comme « -X » pour un qualificatif X.
            if (!digits && !list.items.isEmpty()) {
                list = open(list, opened);
            }
            list.items.add(item(digits, v.substring(start)));
        }
        while (!opened.isEmpty()) {
            opened.pop().normalize();
        }
        return root;
    }

    private static Sub open(Sub parent, Deque<Sub> opened) {
        Sub child = new Sub();
        parent.items.add(child);
        opened.push(child);
        return child;
    }

    private static Item item(boolean digits, String text) {
        return digits ? new Num(new BigInteger(text)) : Qual.of(text, false);
    }
}
