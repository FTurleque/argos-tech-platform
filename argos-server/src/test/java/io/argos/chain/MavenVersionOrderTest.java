package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.argos.impact.MavenVersionOrder;

/**
 * Ordre des versions Maven (décision D5, 2026-11-18, Q-COR-3) : exemples de la documentation de
 * Maven (« Version Order Specification », POM Reference) et de {@code ComparableVersion}.
 */
class MavenVersionOrderTest {

    private static void avant(String a, String b) {
        assertEquals(-1, MavenVersionOrder.compare(a, b), a + " < " + b);
        assertEquals(1, MavenVersionOrder.compare(b, a), b + " > " + a);
    }

    private static void egales(String a, String b) {
        assertEquals(0, MavenVersionOrder.compare(a, b), a + " = " + b);
        assertEquals(0, MavenVersionOrder.compare(b, a), b + " = " + a);
    }

    @Test
    void ordreDesQualificatifsConnus() {
        List<String> ordre = List.of("1-alpha", "1-beta", "1-milestone", "1-rc", "1-snapshot", "1", "1-sp");
        for (int i = 0; i + 1 < ordre.size(); i++) {
            avant(ordre.get(i), ordre.get(i + 1));
        }
    }

    @Test
    void qualificatifsInconnusApresLaVersionPublieeParOrdreLexical() {
        avant("1-sp", "1-foo");
        avant("1", "1-foo");
        avant("32.0.0-android", "32.0.0-jre");
        avant("1-abc", "1-abd");
        avant("1-foo2", "1-foo10");
        avant("31.1-jre", "32.0.0-android");
    }

    @Test
    void aliasEtEquivalences() {
        egales("1", "1.0");
        egales("1", "1.0.0");
        egales("1.ga", "1-ga");
        egales("1-ga", "1-0");
        egales("1-final", "1");
        egales("1.0.0.RELEASE", "1");
        egales("1-cr1", "1-rc1");
        egales("1-a1", "1-alpha-1");
        egales("1-b2", "1-beta-2");
        egales("1-m3", "1-milestone-3");
        egales("1.0-ALPHA1", "1.0-alpha-1");
        egales("1.foo", "1-foo");
    }

    @Test
    void separateursEtSousListes() {
        avant("1", "1.1");
        avant("1-foo", "1-1");
        avant("1-1", "1.1");
        avant("1-ga", "1-sp");
        avant("1-ga.1", "1-sp.1");
        avant("1-sp-1", "1-ga-1");
        avant("1.0-SNAPSHOT", "1.0");
        avant("2.0-beta9", "2.0");
        avant("1.9", "1.10");
    }

    @Test
    void grandsNombres() {
        avant("1.2147483647", "1.2147483648");
        avant("1.9223372036854775807", "1.9223372036854775808");
        avant("1.99999999999999999999", "1.100000000000000000000");
        egales("1.0001", "1.1");
    }
}
