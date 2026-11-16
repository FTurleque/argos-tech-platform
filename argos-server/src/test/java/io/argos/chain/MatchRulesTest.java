package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.argos.catalog.PackageKey;
import io.argos.impact.MatchRules;
import io.argos.impact.MatchRules.Level;
import io.argos.impact.VersionComparator;
import io.argos.signal.AffectedPackage;
import io.argos.signal.AffectedPackage.VersionRange;

/** Règles déterministes de corrélation (COR-01, COR-02, COR-06 ; tâches 4.2 et 4.3). */
class MatchRulesTest {

    private static final String KEY = "pkg:maven/org.exemple/lib";

    private static AffectedPackage below130(boolean declaredPurl) {
        return new AffectedPackage(KEY, declaredPurl, "Maven", "org.exemple:lib",
                List.of(new VersionRange("ECOSYSTEM", "0", "1.3.0", null)), List.of());
    }

    @Test
    void cor01S1VersionDansLaPlage() {
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(below130(true), "1.2.0", true));
    }

    @Test
    void cor01S2VersionHorsPlage() {
        assertEquals(Optional.empty(), MatchRules.evaluate(below130(true), "1.3.0", true));
        assertEquals(Optional.empty(), MatchRules.evaluate(below130(true), "2.0", true));
    }

    @Test
    void cor02S1ExactSurSbomNonObsolete() {
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(below130(true), "1.2.9", true));
    }

    @Test
    void cor06S1ProbableSurInventaireDeRepliOuSbomObsolete() {
        assertEquals(Optional.of(Level.PROBABLE), MatchRules.evaluate(below130(true), "1.2.0", false));
    }

    @Test
    void aVerifierSansPurlDeclare() {
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(below130(false), "1.2.0", true));
    }

    @Test
    void aVerifierVersionInconnueOuPlageNonInterpretable() {
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(below130(true), null, true));
        AffectedPackage git = new AffectedPackage(KEY, true, "Maven", "x",
                List.of(new VersionRange("GIT", "abc123", "def456", null)), List.of());
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(git, "1.0.0", true));
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(below130(true), "release-candidate", true));
    }

    @Test
    void plagesMultiplesEtDerniereVersionAffectee() {
        AffectedPackage log4j = new AffectedPackage(KEY, true, "Maven", "x", List.of(
                new VersionRange("ECOSYSTEM", "2.0-beta9", "2.3.1", null),
                new VersionRange("ECOSYSTEM", "2.4", "2.12.2", null),
                new VersionRange("ECOSYSTEM", "2.13.0", "2.15.0", null)), List.of());
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(log4j, "2.14.1", true));
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(log4j, "2.0", true));
        assertEquals(Optional.empty(), MatchRules.evaluate(log4j, "2.12.2", true));
        assertEquals(Optional.empty(), MatchRules.evaluate(log4j, "2.15.0", true));
        assertEquals(Optional.empty(), MatchRules.evaluate(log4j, "1.2.17", true));
        AffectedPackage last = new AffectedPackage(KEY, true, "Maven", "x",
                List.of(new VersionRange("ECOSYSTEM", "0", null, "1.2.1")), List.of());
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(last, "1.2.1", true));
        assertEquals(Optional.empty(), MatchRules.evaluate(last, "1.2.2", true));
    }

    @Test
    void versionsEnumerees() {
        AffectedPackage listed = new AffectedPackage(KEY, true, "Maven", "x", List.of(), List.of("1.0.0", "1.0.1"));
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(listed, "1.0.1", true));
        assertEquals(Optional.empty(), MatchRules.evaluate(listed, "1.0.2", true));
    }

    @Test
    void comparaisonDesVersions() {
        assertTrue(VersionComparator.compare("1.2.0", "1.3.0").orElseThrow() < 0);
        assertEquals(0, VersionComparator.compare("1.0", "1.0.0").orElseThrow());
        assertTrue(VersionComparator.compare("1.0-rc1", "1.0").orElseThrow() < 0);
        assertTrue(VersionComparator.compare("2.0-beta9", "2.0").orElseThrow() < 0);
        assertTrue(VersionComparator.compare("1.10", "1.9").orElseThrow() > 0);
        assertTrue(VersionComparator.compare("v2.1.0", "2.0.9").orElseThrow() > 0);
        assertFalse(VersionComparator.interpretable("latest"));
    }

    /** PEP 440 : une post-release suit la version publiée, ce n'est pas une pré-version. */
    @Test
    void postReleasesPyPi() {
        assertTrue(VersionComparator.compare("1.0", "1.0.post1").orElseThrow() < 0);
        assertTrue(VersionComparator.compare("2.0.post1", "2.0").orElseThrow() > 0);
        assertTrue(VersionComparator.compare("1.0.post2", "1.0.post10").orElseThrow() < 0);
        assertTrue(VersionComparator.compare("1.0rc1", "1.0").orElseThrow() < 0);
        assertTrue(VersionComparator.compare("1.0c1", "1.0").orElseThrow() < 0);
        AffectedPackage pkg = new AffectedPackage("pkg:pypi/exemple", true, "PyPI", "exemple",
                List.of(new VersionRange("ECOSYSTEM", "0", "1.0.post1", null)), List.of());
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(pkg, "1.0", true), "1.0 précède le correctif 1.0.post1");
        assertEquals(Optional.empty(), MatchRules.evaluate(pkg, "1.0.post1", true));
        AffectedPackage fixed20 = new AffectedPackage("pkg:pypi/exemple", true, "PyPI", "exemple",
                List.of(new VersionRange("ECOSYSTEM", "0", "2.0", null)), List.of());
        assertEquals(Optional.empty(), MatchRules.evaluate(fixed20, "2.0.post1", true), "2.0.post1 suit le correctif 2.0");
    }

    /** Un qualificatif inconnu n'est classé ni avant ni après la version publiée : niveau « à vérifier ». */
    @Test
    void qualificatifInconnuNonClasseCommePreVersion() {
        assertTrue(VersionComparator.compare("1.0-foo", "1.0").isEmpty());
        assertTrue(VersionComparator.compare("1.0", "1.0-foo").isEmpty());
        assertEquals(Optional.of(0), VersionComparator.compare("32.1.3-jre", "32.1.3-jre"));
        assertTrue(VersionComparator.compare("31.1-jre", "32.0.0-jre").orElseThrow() < 0, "décidé par les segments numériques");
        AffectedPackage pkg = new AffectedPackage(KEY, true, "Maven", "org.exemple:lib",
                List.of(new VersionRange("ECOSYSTEM", "0", "1.0", null)), List.of());
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(pkg, "1.0-foo", true));
    }

    @Test
    void clesDePaquet() {
        assertEquals("pkg:maven/org.exemple/lib", PackageKey.parse("pkg:maven/org.exemple/lib@1.2.0").orElseThrow().key());
        assertEquals("1.2.0", PackageKey.parse("pkg:maven/org.exemple/lib@1.2.0?type=jar").orElseThrow().version());
        assertEquals("pkg:npm/@angular/core", PackageKey.parse("pkg:npm/%40angular/core@17.0.0").orElseThrow().key());
        assertEquals("pkg:pypi/django-rest", PackageKey.parse("pkg:pypi/Django_Rest@1.0").orElseThrow().key());
        assertEquals(Optional.of("pkg:maven/org.exemple/lib"), PackageKey.fromEcosystem("Maven", "org.exemple:lib"));
        assertEquals(Optional.of("pkg:cargo/serde"), PackageKey.fromEcosystem("crates.io", "serde"));
        assertTrue(PackageKey.parse("not-a-purl").isEmpty());
    }
}
