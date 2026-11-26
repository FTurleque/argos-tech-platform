package io.argos.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.argos.catalog.PackageKey;

/**
 * Cas limites des lecteurs de documents d'inventaire (INV-01, INV-02, INV-04) : SBOM CycloneDX trop
 * volumineuse, illisible, d'une autre version ou mal formée ; composants imbriqués, doublons et écartés ;
 * manifestes {@code package.json} et {@code requirements.txt} à versions exactes ou contraintes.
 */
class ParseursInventaireTest {

    private static final long TAILLE_MAX = 1_000_000;
    private static final int TROP_DE_COMPOSANTS = ParsedInventory.MAX_SIGNALED + 5;

    // ------------------------------------------------------------------ CycloneDX : document refusé

    @Test
    void documentAuDelaDeLaTailleMaximaleRefuse() {
        byte[] document = bom("1.6", "").getBytes(StandardCharsets.UTF_8);
        InvalidDocumentException refus = assertThrows(InvalidDocumentException.class,
                () -> CycloneDxParser.parse(document, document.length - 1));
        assertTrue(refus.getMessage().contains("taille maximale"), refus.getMessage());
    }

    @Test
    void documentIllisibleOuNonObjetRefuse() {
        assertThrows(InvalidDocumentException.class, () -> lire("{pas du json"));
        assertThrows(InvalidDocumentException.class, () -> lire("[1, 2]"));
        assertThrows(InvalidDocumentException.class, () -> lire(""));
    }

    @Test
    void formatOuVersionDeSchemaNonAcceptesRefuses() {
        assertThrows(InvalidDocumentException.class, () -> lire("{\"bomFormat\": \"SPDX\", \"specVersion\": \"1.6\"}"));
        InvalidDocumentException version = assertThrows(InvalidDocumentException.class, () -> lire(bom("1.4", "")));
        assertTrue(version.getMessage().contains("'1.4'"), version.getMessage());
    }

    @Test
    void composantsNonTableauOuDateIllisibleRefuses() {
        assertThrows(InvalidDocumentException.class,
                () -> lire("{\"bomFormat\": \"CycloneDX\", \"specVersion\": \"1.5\", \"components\": {}}"));
        assertThrows(InvalidDocumentException.class,
                () -> lire("{\"bomFormat\": \"CycloneDX\", \"specVersion\": \"1.5\", \"metadata\": {\"timestamp\": \"hier\"}}"));
    }

    // ------------------------------------------------------------------ CycloneDX : lecture

    @Test
    void documentSansComposantNiDateDonneUnInventaireVide() {
        ParsedInventory inventaire = lire("{\"bomFormat\": \"CycloneDX\", \"specVersion\": \"1.5\"}");
        assertEquals("1.5", inventaire.specVersion());
        assertNull(inventaire.sourceDate());
        assertEquals(List.of(), inventaire.components());
        assertEquals(0, inventaire.ignoredCount());
    }

    @Test
    void composantsImbriquesLusEtCaractereTransitifDuGraphe() {
        ParsedInventory inventaire = lire("""
                {"bomFormat": "CycloneDX", "specVersion": "1.6",
                 "metadata": {"timestamp": "2026-11-20T10:00:00Z", "component": {"bom-ref": "racine"}},
                 "components": [
                   {"bom-ref": "direct", "purl": "pkg:maven/org.exemple/direct@1.0",
                    "components": [{"bom-ref": "imbrique", "purl": "pkg:maven/org.exemple/imbrique@2.0"}]},
                   {"purl": "pkg:npm/sans-ref", "version": "3.1.0"}
                 ],
                 "dependencies": [{"ref": "autre", "dependsOn": []}, {"ref": "racine", "dependsOn": ["direct"]}]}
                """);
        Map<String, ParsedInventory.Item> parPurl = parPurl(inventaire);

        assertEquals(Instant.parse("2026-11-20T10:00:00Z"), inventaire.sourceDate());
        assertEquals(Boolean.FALSE, parPurl.get("pkg:maven/org.exemple/direct@1.0").transitive());
        assertEquals(Boolean.TRUE, parPurl.get("pkg:maven/org.exemple/imbrique@2.0").transitive());
        ParsedInventory.Item sansReference = parPurl.get("pkg:npm/sans-ref");
        assertNull(sansReference.transitive(), "sans bom-ref, caractère transitif inconnu");
        assertEquals("3.1.0", sansReference.version(), "version lue dans le composant quand le PURL n'en porte pas");
    }

    @Test
    void sansGrapheExploitableLeCaractereTransitifEstInconnu() {
        ParsedInventory sansRacine = lire(bom("1.6", "{\"bom-ref\": \"a\", \"purl\": \"pkg:maven/g/a@1\"}"));
        assertNull(sansRacine.components().get(0).transitive());

        ParsedInventory racineAbsenteDuGraphe = lire("""
                {"bomFormat": "CycloneDX", "specVersion": "1.6", "metadata": {"component": {"bom-ref": "racine"}},
                 "components": [{"bom-ref": "a", "purl": "pkg:maven/g/a@1"}], "dependencies": [{"ref": "autre"}]}
                """);
        assertNull(racineAbsenteDuGraphe.components().get(0).transitive());
    }

    @Test
    void doublonDirectLEmporteSurLeDoublonTransitif() {
        ParsedInventory inventaire = lire("""
                {"bomFormat": "CycloneDX", "specVersion": "1.6", "metadata": {"component": {"bom-ref": "racine"}},
                 "components": [
                   {"bom-ref": "direct", "purl": "pkg:maven/g/a@1"},
                   {"bom-ref": "copie", "purl": "pkg:maven/g/a@1"},
                   {"bom-ref": "copie-transitive", "purl": "pkg:maven/g/b@1"},
                   {"bom-ref": "b-direct", "purl": "pkg:maven/g/b@1"}
                 ],
                 "dependencies": [{"ref": "racine", "dependsOn": ["direct", "b-direct"]}]}
                """);
        assertEquals(2, inventaire.components().size());
        assertTrue(inventaire.components().stream().allMatch(item -> Boolean.FALSE.equals(item.transitive())));
    }

    @Test
    void composantsSansPurlOuAPurlIllisibleEcartesEtSignalesAvecLeurMotif() {
        String nomLong = "n".repeat(CycloneDxParser.MAX_FIELD + 10);
        ParsedInventory inventaire = lire(bom("1.6", """
                {"name": "%s", "group": "  ", "version": "1.0"},
                {"name": "illisible", "group": {"objet": true}, "purl": "pas-un-purl"},
                {"name": "blanc", "purl": "   "}
                """.formatted(nomLong)));

        assertEquals(List.of(), inventaire.components());
        assertEquals(3, inventaire.ignoredCount());
        ParsedInventory.IgnoredComponent sansPurl = inventaire.ignoredComponents().get(0);
        assertEquals(CycloneDxParser.MAX_FIELD, sansPurl.name().length());
        assertNull(sansPurl.group(), "groupe blanc absent");
        assertEquals(ParsedInventory.IgnoredComponent.SANS_PURL, sansPurl.reason());
        ParsedInventory.IgnoredComponent illisible = inventaire.ignoredComponents().get(1);
        assertEquals(ParsedInventory.IgnoredComponent.PURL_INVALIDE, illisible.reason());
        assertNull(illisible.group(), "groupe non textuel absent");
        assertEquals(ParsedInventory.IgnoredComponent.SANS_PURL, inventaire.ignoredComponents().get(2).reason());
    }

    @Test
    void auDelaDuMaximumSignaleSeulLeNombreEstConserve() {
        String composants = java.util.stream.IntStream.range(0, TROP_DE_COMPOSANTS)
                .mapToObj(rang -> "{\"name\": \"c" + rang + "\"}").collect(Collectors.joining(","));
        ParsedInventory inventaire = lire(bom("1.5", composants));

        assertEquals(TROP_DE_COMPOSANTS, inventaire.ignoredCount());
        assertEquals(ParsedInventory.MAX_SIGNALED, inventaire.ignoredComponents().size());
    }

    // ------------------------------------------------------------------ manifestes

    @Test
    void seulsPackageJsonEtRequirementsTxtSontPrisEnCharge() {
        assertTrue(ManifestParser.supports("package.json"));
        assertTrue(ManifestParser.supports("services/api/requirements.txt"));
        assertFalse(ManifestParser.supports("pom.xml"));
        assertFalse(ManifestParser.supports("docs/package.json.bak"));
        assertThrows(InvalidDocumentException.class, () -> ManifestParser.parse("build.gradle", new byte[0]));
    }

    @Test
    void packageJsonVersionsExactesPorteeEtContraintes() {
        List<ParsedInventory.Item> items = ManifestParser.parse("front/package.json", """
                {"dependencies": {"vue": "3.5.13", "@scope/outil": "v1.2.0", "plage": "^4.0.0", "etoile": "*"},
                 "devDependencies": {"ignore": "1.0.0"}}
                """.getBytes(StandardCharsets.UTF_8));

        assertEquals(4, items.size());
        ParsedInventory.Item vue = items.get(0);
        assertEquals(PackageKey.key("npm", null, "vue"), vue.packageKey());
        assertEquals(vue.packageKey() + "@3.5.13", vue.purl());
        assertNull(vue.transitive(), "un manifeste ne connaît pas les dépendances transitives");
        ParsedInventory.Item portee = items.get(1);
        assertEquals(PackageKey.key("npm", "@scope", "outil"), portee.packageKey());
        assertEquals("1.2.0", portee.version());
        assertNull(items.get(2).version(), "contrainte de plage : version inconnue");
        assertEquals(items.get(2).packageKey(), items.get(2).purl());
        assertNull(items.get(3).version());
    }

    @Test
    void packageJsonIllisibleOuSansDependanceRefuseOuVide() {
        assertThrows(InvalidDocumentException.class, () -> ManifestParser.parse("package.json", "{".getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of(), ManifestParser.parse("package.json", "{\"name\": \"vide\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void requirementsTxtVersionsEpingleesCommentairesOptionsEtExtras() {
        List<ParsedInventory.Item> items = ManifestParser.parse("requirements.txt", """
                # commentaire
                -r autre.txt
                --index-url https://exemple.invalid/simple

                Django==5.1.4
                requests[socks] == 2.32.3 ; python_version >= "3.9"
                flask>=3.0
                numpy
                !!! ligne illisible
                """.getBytes(StandardCharsets.UTF_8));

        Map<String, String> versions = items.stream().collect(Collectors.toMap(ParsedInventory.Item::packageKey,
                item -> String.valueOf(item.version())));
        assertEquals(4, items.size(), versions.toString());
        assertEquals("5.1.4", versions.get(PackageKey.key("pypi", null, "Django")));
        assertEquals("2.32.3", versions.get(PackageKey.key("pypi", null, "requests")));
        assertEquals("null", versions.get(PackageKey.key("pypi", null, "flask")));
        assertEquals("null", versions.get(PackageKey.key("pypi", null, "numpy")));
    }

    @Test
    void inventaireDeManifesteSansComposantIgnore() {
        ParsedInventory inventaire = new ParsedInventory(null, null, List.of());
        assertEquals(0, inventaire.ignoredCount());
        assertEquals(List.of(), inventaire.ignoredComponents());
    }

    private static ParsedInventory lire(String document) {
        return CycloneDxParser.parse(document.getBytes(StandardCharsets.UTF_8), TAILLE_MAX);
    }

    private static String bom(String specVersion, String composants) {
        return "{\"bomFormat\": \"CycloneDX\", \"specVersion\": \"" + specVersion + "\", \"components\": [" + composants + "]}";
    }

    private static Map<String, ParsedInventory.Item> parPurl(ParsedInventory inventaire) {
        return inventaire.components().stream().collect(Collectors.toMap(ParsedInventory.Item::purl, item -> item));
    }
}
