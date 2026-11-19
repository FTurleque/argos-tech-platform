package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.argos.inventory.CycloneDxParser;
import io.argos.inventory.InvalidDocumentException;
import io.argos.inventory.ParsedInventory;

/**
 * Caractérisation de {@link CycloneDxParser#parse} (INV-01, INV-02) : motifs de refus dans l'ordre des
 * contrôles, composants imbriqués, version reprise du composant, transitivité inconnue et fusion des doublons
 * (le composant direct l'emporte). Sans base ni serveur.
 */
class CycloneDxCaracterisationTest {

    private static ParsedInventory lire(String json) {
        return CycloneDxParser.parse(json.getBytes(StandardCharsets.UTF_8), 1_000_000);
    }

    private static String refus(String json, long maxBytes) {
        return assertThrows(InvalidDocumentException.class,
                () -> CycloneDxParser.parse(json.getBytes(StandardCharsets.UTF_8), maxBytes)).getMessage();
    }

    private static String refus(String json) {
        return refus(json, 1_000_000);
    }

    @Test
    void motifsDeRefusDansLOrdreDesControles() {
        assertEquals("document de 12 octets au-delà de la taille maximale", refus("{pas du json", 10));
        assertEquals("JSON illisible", refus("{pas du json"));
        assertEquals("le document n'est pas un objet JSON", refus(""));
        assertEquals("le document n'est pas un objet JSON", refus("null"));
        assertEquals("le document n'est pas un objet JSON", refus("[]"));
        assertEquals("bomFormat absent ou différent de CycloneDX", refus("{\"specVersion\":\"1.2\",\"components\":3}"));
        assertEquals("version de schéma CycloneDX non acceptée : '' (acceptées : 1.5, 1.6)",
                refus("{\"bomFormat\":\"CycloneDX\",\"components\":3}"));
        assertEquals("champ components non conforme",
                refus("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":3,\"metadata\":{\"timestamp\":\"x\"}}"));
        assertEquals("metadata.timestamp illisible",
                refus("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"metadata\":{\"timestamp\":\"hier\"}}"));
    }

    @Test
    void documentMinimalSansComposantNiDate() {
        ParsedInventory inv = lire("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\"}");

        assertEquals("1.5", inv.specVersion());
        assertNull(inv.sourceDate());
        assertEquals(List.of(), inv.components());
        assertEquals(0, inv.ignoredCount());
        assertEquals(List.of(), inv.ignoredComponents());
    }

    @Test
    void composantsImbriquesVersionEtFusionDesDoublons() {
        ParsedInventory inv = lire("""
                {"bomFormat":"CycloneDX","specVersion":"1.6",
                 "metadata":{"component":{"bom-ref":"root"}},
                 "components":[
                   {"bom-ref":"t","purl":"pkg:npm/dup@1.0.0","components":[
                     {"bom-ref":"enfant","purl":"pkg:npm/enfant","version":"3.0.0"},
                     {"name":" ","group":{"objet":true},"version":"  9  "}]},
                   {"bom-ref":"d","purl":"pkg:npm/dup@1.0.0"},
                   {"purl":"pkg:npm/sans-ref@2.0.0"},
                   {"bom-ref":"t2","purl":"pkg:npm/dup@1.0.0"}
                 ],
                 "dependencies":[{"ref":"autre","dependsOn":["t"]},{"ref":"root","dependsOn":["d","enfant"]}]}""");

        assertEquals(List.of(
                new ParsedInventory.Item("pkg:npm/dup", "pkg:npm/dup@1.0.0", "1.0.0", false),
                new ParsedInventory.Item("pkg:npm/sans-ref", "pkg:npm/sans-ref@2.0.0", "2.0.0", null),
                new ParsedInventory.Item("pkg:npm/enfant", "pkg:npm/enfant", "3.0.0", false)), inv.components());
        assertEquals(List.of(new ParsedInventory.IgnoredComponent(null, null, "9", ParsedInventory.IgnoredComponent.SANS_PURL)),
                inv.ignoredComponents());
    }

    @Test
    void sansGrapheExploitableLaTransitiviteEstInconnue() {
        for (String graphe : List.of("", ",\"dependencies\":{}", ",\"metadata\":{\"component\":{\"bom-ref\":\"root\"}}",
                ",\"metadata\":{\"component\":{\"bom-ref\":\"root\"}},\"dependencies\":[{\"ref\":\"autre\",\"dependsOn\":[\"a\"]}]")) {
            ParsedInventory inv = lire("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"components\":["
                    + "{\"bom-ref\":\"a\",\"purl\":\"pkg:npm/a@1.0.0\"},{\"bom-ref\":\"a2\",\"purl\":\"pkg:npm/a@1.0.0\"}]" + graphe + "}");
            assertEquals(List.of(new ParsedInventory.Item("pkg:npm/a", "pkg:npm/a@1.0.0", "1.0.0", null)), inv.components(), graphe);
        }
    }

    @Test
    void fusionLeDernierLEmporteSaufSiLePremierEstDirect() {
        ParsedInventory inv = lire("""
                {"bomFormat":"CycloneDX","specVersion":"1.6","metadata":{"component":{"bom-ref":"root"}},
                 "components":[{"purl":"pkg:npm/x@1"},{"bom-ref":"t","purl":"pkg:npm/x@1"},{"purl":"pkg:npm/x@1?type=a"}],
                 "dependencies":[{"ref":"root","dependsOn":[]}]}""");

        assertEquals(List.of(new ParsedInventory.Item("pkg:npm/x", "pkg:npm/x@1?type=a", "1", null)), inv.components());
    }
}
