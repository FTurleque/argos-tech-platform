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

    /**
     * Maven (décision D5, 2026-11-18, Q-COR-3) : les qualificatifs inconnus suivent l'ordre de
     * {@code ComparableVersion}, après la version publiée et entre eux par ordre lexical.
     */
    @Test
    void qualificatifsInconnusOrdonnesSelonMavenPourMaven() {
        assertEquals(Optional.of(1), VersionComparator.compare("Maven", "32.0.0-jre", "32.0.0-android"));
        assertEquals(Optional.of(-1), VersionComparator.compare("Maven", "32.0.0-android", "32.0.0-jre"));
        assertEquals(Optional.of(1), VersionComparator.compare("Maven", "1.0-foo", "1.0"), "un qualificatif inconnu suit la version publiée");
        assertEquals(Optional.of(1), VersionComparator.compare("maven", "1.0-foo", "1.0-sp"), "et suit sp");
        assertEquals(Optional.of(0), VersionComparator.compare("Maven", "32.1.3-jre", "32.1.3-jre"));
        assertEquals(Optional.of(-1), VersionComparator.compare("Maven", "31.1-jre", "32.0.0-android"), "décidé par les segments numériques");
        assertTrue(VersionComparator.compare("Maven", "latest", "1.0").isEmpty(), "une version non numérique reste non interprétable");
    }

    /** GHSA-7g45-4rm6-3mm3 (guava, borne fixed 32.0.0-android) : 32.0.0-jre non affectée, 31.1-jre affectée. */
    @Test
    void cor01S5GuavaJreFaceALaBorneAndroid() {
        AffectedPackage guava = new AffectedPackage("pkg:maven/com.google.guava/guava", true, "Maven",
                "com.google.guava:guava", List.of(new VersionRange("ECOSYSTEM", "1.0", "32.0.0-android", null)), List.of());
        assertEquals(Optional.empty(), MatchRules.evaluate(guava, "32.0.0-jre", true));
        assertEquals(Optional.of(false), MatchRules.versionAffected(guava, "32.0.0-jre"));
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(guava, "31.1-jre", true));
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(guava, "32.0.0-SNAPSHOT", true), "snapshot précède la borne");
        assertEquals(Optional.empty(), MatchRules.evaluate(guava, "32.1.3-jre", true));
        AffectedPackage sansEcosysteme = new AffectedPackage("pkg:maven/com.google.guava/guava", true, null,
                "com.google.guava:guava", List.of(new VersionRange("ECOSYSTEM", "0", "32.0.0-android", null)), List.of());
        assertEquals(Optional.empty(), MatchRules.evaluate(sansEcosysteme, "32.0.0-jre", true), "écosystème déduit de la clé PURL");
    }

    /** Un qualificatif inconnu face à la borne d'un paquet Maven : plage interprétable, niveau selon COR-02. */
    @Test
    void qualificatifInconnuMavenNePlusAVerifier() {
        AffectedPackage pkg = new AffectedPackage(KEY, true, "Maven", "org.exemple:lib",
                List.of(new VersionRange("ECOSYSTEM", "0", "1.0", null)), List.of());
        assertEquals(Optional.empty(), MatchRules.evaluate(pkg, "1.0-foo", true), "1.0-foo suit la borne 1.0");
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(pkg, "0.9-foo", true));
        AffectedPackage last = new AffectedPackage(KEY, true, "Maven", "org.exemple:lib",
                List.of(new VersionRange("ECOSYSTEM", "1.0-bar", null, "1.0-foo")), List.of());
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(last, "1.0-baz", true), "bar < baz <= foo");
        assertEquals(Optional.empty(), MatchRules.evaluate(last, "1.0-zzz", true));
        assertEquals(Optional.empty(), MatchRules.evaluate(last, "1.0-alpha", true), "alpha précède la borne introduced 1.0-bar");
    }

    /**
     * L'ordre Maven reçoit la version lue par l'ordre générique : préfixe {@code v} et métadonnées
     * {@code +…} retirés ; une version avec {@code _} reste « à vérifier ».
     */
    @Test
    void qualificatifInconnuMavenAvecPrefixeOuMetadonnees() {
        assertEquals(Optional.of(1), VersionComparator.compare("Maven", "v32.0.0-jre", "32.0.0-android"));
        assertEquals(Optional.of(1), VersionComparator.compare("Maven", "V32.0.0-jre+build.7", "32.0.0-android"));
        assertEquals(Optional.of(-1), VersionComparator.compare("Maven", "32.0.0-android", "v32.0.0-jre"));
        assertTrue(VersionComparator.compare("Maven", "32_0_0-jre", "32.0.0-android").isEmpty());
        AffectedPackage guava = new AffectedPackage("pkg:maven/com.google.guava/guava", true, "Maven",
                "com.google.guava:guava", List.of(new VersionRange("ECOSYSTEM", "1.0", "32.0.0-android", null)), List.of());
        assertEquals(Optional.empty(), MatchRules.evaluate(guava, "v32.0.0-jre", true), "v32.0.0-jre suit la borne");
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(guava, "32_0_0-jre", true));
    }

    /** Hors Maven, un qualificatif inconnu n'est classé ni avant ni après la version publiée : niveau « à vérifier ». */
    @Test
    void qualificatifInconnuHorsMavenResteAVerifier() {
        assertTrue(VersionComparator.compare("1.0-foo", "1.0").isEmpty());
        assertTrue(VersionComparator.compare("1.0", "1.0-foo").isEmpty());
        assertTrue(VersionComparator.compare("npm", "32.0.0-jre", "32.0.0-android").isEmpty());
        assertTrue(VersionComparator.compare("PyPI", "1.0-foo", "1.0").isEmpty());
        assertTrue(VersionComparator.compare(null, "1.0-foo", "1.0").isEmpty());
        AffectedPackage npm = new AffectedPackage("pkg:npm/exemple", true, "npm", "exemple",
                List.of(new VersionRange("SEMVER", "0", "1.0.0", null)), List.of());
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(npm, "1.0.0-foo", true));
        AffectedPackage pypi = new AffectedPackage("pkg:pypi/exemple", true, "PyPI", "exemple",
                List.of(new VersionRange("ECOSYSTEM", "0", "1.0", null)), List.of());
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(pypi, "1.0-foo", true));
    }

    /** Les qualificatifs connus gardent l'ordre générique, pour Maven comme pour les autres écosystèmes. */
    @Test
    void qualificatifsConnusInchangesPourMaven() {
        assertEquals(Optional.of(-1), VersionComparator.compare("Maven", "1.0-rc1", "1.0"));
        assertEquals(Optional.of(-1), VersionComparator.compare("Maven", "2.0-beta9", "2.0"));
        assertEquals(Optional.of(1), VersionComparator.compare("Maven", "1.0-sp1", "1.0"));
        assertEquals(Optional.of(0), VersionComparator.compare("Maven", "1.0.0.RELEASE", "1.0"));
        assertEquals(Optional.of(-1), VersionComparator.compare("Maven", "1.0-SNAPSHOT", "1.0"));
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
