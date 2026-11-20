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

    /** Maven : l'appartenance aux versions énumérées suit l'égalité de ComparableVersion (décision E3). */
    @Test
    void versionsEnumereesMavenSelonComparableVersion() {
        AffectedPackage listed = new AffectedPackage(KEY, true, "Maven", "x", List.of(), List.of("1.0", "2.0-RC1"));
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(listed, "1.0.0", true), "1.0 == 1.0.0");
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(listed, "1.0-ga", true), "1.0 == 1.0-ga");
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(listed, "2.0-rc-1", true), "2.0-RC1 == 2.0-rc-1");
        assertEquals(Optional.empty(), MatchRules.evaluate(listed, "1.0.1", true));
        AffectedPackage npm = new AffectedPackage("pkg:npm/exemple", true, "npm", "exemple", List.of(), List.of("1.0"));
        assertEquals(Optional.empty(), MatchRules.evaluate(npm, "1.0.0", true), "hors Maven : égalité des chaînes inchangée");
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

    private static void mavenAvant(String anterieure, String posterieure) {
        assertEquals(Optional.of(-1), VersionComparator.compare("Maven", anterieure, posterieure), anterieure + " < " + posterieure);
        assertEquals(Optional.of(1), VersionComparator.compare("Maven", posterieure, anterieure), posterieure + " > " + anterieure);
    }

    private static void mavenEgales(String version, String equivalente) {
        assertEquals(Optional.of(0), VersionComparator.compare("Maven", version, equivalente), version + " = " + equivalente);
        assertEquals(Optional.of(0), VersionComparator.compare("Maven", equivalente, version), equivalente + " = " + version);
    }

    /** Maven (décision E3, 2026-11-19) : toute comparaison suit {@code ComparableVersion} ; cas usuels. */
    @Test
    void mavenCasUsuels() {
        mavenAvant("1.2.0", "1.3.0");
        mavenAvant("1.9", "1.10");
        mavenAvant("1", "1.1");
        mavenAvant("2.0.9", "2.1.0");
        mavenEgales("1", "1.0");
        mavenEgales("1.0", "1.0.0");
        mavenAvant("1.2147483647", "1.2147483648");
        mavenAvant("1.99999999999999999999", "1.100000000000000000000");
    }

    /** Qualificatifs connus de Maven et leurs alias : alpha < beta < milestone < rc < snapshot < (publiée) = ga = final < sp. */
    @Test
    void mavenQualificatifsConnus() {
        List<String> ordre = List.of("1-alpha", "1-beta", "1-milestone", "1-rc", "1-snapshot", "1", "1-sp");
        for (int rang = 0; rang + 1 < ordre.size(); rang++) {
            mavenAvant(ordre.get(rang), ordre.get(rang + 1));
        }
        mavenEgales("1-ga", "1");
        mavenEgales("1-final", "1");
        mavenEgales("1.0.0.RELEASE", "1");
        mavenEgales("1-a1", "1-alpha-1");
        mavenEgales("1-b2", "1-beta-2");
        mavenEgales("1-m3", "1-milestone-3");
        mavenEgales("1-cr1", "1-rc1");
        mavenAvant("1.0-rc1", "1.0");
        mavenAvant("2.0-beta9", "2.0");
        mavenAvant("1.0-SNAPSHOT", "1.0");
        mavenAvant("1.0", "1.0-sp1");
    }

    /**
     * Qualificatifs inconnus de Maven ({@code jre}, {@code android}, mais aussi {@code dev}, {@code pre},
     * {@code post}, {@code a} sans chiffre) : après la version publiée et {@code sp}, entre eux par ordre lexical.
     */
    @Test
    void mavenQualificatifsInconnus() {
        mavenAvant("32.0.0-android", "32.0.0-jre");
        mavenAvant("1.0", "1.0-foo");
        mavenAvant("1.0-sp", "1.0-foo");
        mavenAvant("1-abc", "1-abd");
        mavenAvant("1-foo2", "1-foo10");
        mavenAvant("31.1-jre", "32.0.0-android");
        mavenEgales("32.1.3-jre", "32.1.3-jre");
        mavenAvant("1.0", "1.0-dev");
        mavenAvant("1.0", "1.0-pre");
        mavenAvant("1.0", "1.0.post1");
        mavenAvant("1", "1-a");
        mavenAvant("2.6.3", "2.6.3-CUSTOM");
    }

    /** La casse ne change pas l'ordre Maven. */
    @Test
    void mavenCasse() {
        mavenEgales("1.0-ALPHA1", "1.0-alpha-1");
        mavenEgales("1.0-RC1", "1.0-rc1");
        mavenEgales("32.0.0-JRE", "32.0.0-jre");
        mavenAvant("1.0-Snapshot", "1.0");
    }

    /** Séparateurs {@code .} et {@code -} de Maven ; {@code _} n'en est pas un : version « à vérifier ». */
    @Test
    void mavenSeparateurs() {
        mavenEgales("1.ga", "1-ga");
        mavenEgales("1.foo", "1-foo");
        mavenAvant("1-foo", "1-1");
        mavenAvant("1-1", "1.1");
        mavenAvant("1-ga.1", "1-sp.1");
        mavenAvant("1-sp-1", "1-ga-1");
        assertTrue(VersionComparator.compare("Maven", "32_0_0-jre", "32.0.0-android").isEmpty());
        assertTrue(VersionComparator.compare("Maven", "1.0~rc1", "1.0").isEmpty());
        assertTrue(VersionComparator.compare("Maven", "latest", "1.0").isEmpty(), "une version non numérique reste non interprétable");
        assertTrue(VersionComparator.compare("Maven", "1.0", null).isEmpty());
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

    /**
     * Écosystème ni déclaré ni lisible dans la clé PURL : ordre générique, comme hors Maven ; un écosystème blanc
     * laisse la clé PURL décider.
     */
    @Test
    void ecosystemeInconnuSuitLOrdreGenerique() {
        List<VersionRange> plage = List.of(new VersionRange("ECOSYSTEM", "0", "32.0.0-android", null));
        for (String cle : new String[] {"com.google.guava:guava", "pkg:/guava", "pkg:maven", null}) {
            AffectedPackage inconnu = new AffectedPackage(cle, true, null, "guava", plage, List.of());
            assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(inconnu, "32.0.0-jre", true), cle);
            assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(inconnu, "31.1", true), cle);
        }
        AffectedPackage blanc = new AffectedPackage("pkg:maven/com.google.guava/guava", true, "  ", "guava", plage, List.of());
        assertEquals(Optional.empty(), MatchRules.evaluate(blanc, "32.0.0-jre", true), "écosystème blanc : clé PURL Maven");
    }

    /** Sans plage : rien à conclure sans version énumérée, hors plage si les versions énumérées ne la citent pas. */
    @Test
    void sansPlageVersionsEnumereesOuRien() {
        AffectedPackage rien = new AffectedPackage(KEY, true, "Maven", "x", List.of(), null);
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(rien, "1.0", true));
        assertEquals(Optional.empty(), MatchRules.versionAffected(rien, "1.0"));
        AffectedPackage enumeres = new AffectedPackage(KEY, true, "Maven", "x", List.of(), List.of("2.0"));
        assertEquals(Optional.empty(), MatchRules.evaluate(enumeres, "1.0", true));
        assertEquals(Optional.of(false), MatchRules.versionAffected(enumeres, "1.0"));
        AffectedPackage gitEtHors = new AffectedPackage(KEY, true, "Maven", "x", List.of(new VersionRange("GIT", "a", "b", null),
                new VersionRange("ECOSYSTEM", "0", "1.0", null)), List.of());
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(gitEtHors, "0.5", true), "une plage contenant la version l'emporte");
        assertEquals(Optional.of(Level.A_VERIFIER), MatchRules.evaluate(gitEtHors, "1.5", true), "plage GIT : rien à conclure");
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
     * L'ordre Maven reçoit la version normalisée : préfixe {@code v} et métadonnées {@code +…} retirés ;
     * une version avec {@code _} reste « à vérifier ».
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

    /**
     * Hors Maven, l'ordre générique est inchangé (décision E3) : npm, PyPI, crates.io, Go et écosystème
     * absent gardent leurs qualificatifs connus et le niveau « à vérifier » pour un qualificatif inconnu.
     */
    @Test
    void ordreGeneriqueInchangeHorsMaven() {
        for (String eco : new String[] {"npm", "PyPI", "crates.io", "Go", "NuGet", null}) {
            assertEquals(Optional.of(-1), VersionComparator.compare(eco, "1.0-rc1", "1.0"), eco);
            assertEquals(Optional.of(-1), VersionComparator.compare(eco, "1.0.dev1", "1.0"), eco + " : dev précède la version publiée");
            assertEquals(Optional.of(1), VersionComparator.compare(eco, "1.0.post1", "1.0"), eco);
            assertEquals(Optional.of(0), VersionComparator.compare(eco, "1.0", "1.0.0"), eco);
            assertEquals(Optional.of(1), VersionComparator.compare(eco, "v2.1.0+build.7", "2.0.9"), eco);
            assertEquals(Optional.of(0), VersionComparator.compare(eco, "1.0_1", "1.0.1"), eco + " : _ reste un séparateur");
            assertTrue(VersionComparator.compare(eco, "32.0.0-jre", "32.0.0-android").isEmpty(), eco);
            assertTrue(VersionComparator.compare(eco, "1.0-foo", "1.0").isEmpty(), eco);
        }
        AffectedPackage npm = new AffectedPackage("pkg:npm/exemple", true, "npm", "exemple",
                List.of(new VersionRange("SEMVER", "0", "1.0.0", null)), List.of());
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(npm, "1.0.0-rc.1", true));
        assertEquals(Optional.of(Level.EXACT), MatchRules.evaluate(npm, "1.0.0-dev", true), "dev précède la version publiée hors Maven");
        AffectedPackage maven = new AffectedPackage(KEY, true, "Maven", "org.exemple:lib",
                List.of(new VersionRange("ECOSYSTEM", "0", "1.0.0", null)), List.of());
        assertEquals(Optional.empty(), MatchRules.evaluate(maven, "1.0.0-dev", true), "dev suit la version publiée pour Maven");
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
