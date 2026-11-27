package io.argos.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Dédoublonnage des composants écrits par lot et empreinte des documents lus (INV-14). */
class EcritureComposantsTest {

    private static final String CLE = "pkg:npm/exemple";

    private static ParsedInventory.Item composant(String version, Boolean transitif) {
        return new ParsedInventory.Item(CLE, CLE + "@" + version, version, transitif);
    }

    @Test
    void doublonsFusionnesDirectDesQuUneOccurrenceLEst() {
        List<ParsedInventory.Item> uniques = EcritureComposants.dedoublonner(List.of(composant("1.0.0", true),
                composant("1.0.0", false), composant("2.0.0", null), composant("2.0.0", true)));
        assertEquals(2, uniques.size());
        assertEquals(Boolean.FALSE, uniques.get(0).transitive());
        assertEquals(Boolean.TRUE, uniques.get(1).transitive());
    }

    @Test
    void versionAbsenteEtVideConfondues() {
        ParsedInventory.Item sansVersion = new ParsedInventory.Item(CLE, CLE, null, null);
        ParsedInventory.Item versionVide = new ParsedInventory.Item(CLE, CLE, "", null);
        assertEquals(1, EcritureComposants.dedoublonner(List.of(sansVersion, versionVide)).size());
    }

    @Test
    void transitifInconnuSansOccurrenceConnue() {
        assertEquals(Optional.empty(), EcritureComposants.fusionnerTransitif(null, null));
        assertEquals(Optional.of(Boolean.TRUE), EcritureComposants.fusionnerTransitif(null, true));
        assertEquals(Optional.of(Boolean.FALSE), EcritureComposants.fusionnerTransitif(true, false));
    }

    @Test
    void empreinteDesManifestesIndependanteDeLOrdre() {
        Map<String, byte[]> premier = new LinkedHashMap<>();
        premier.put("package.json", "{}".getBytes(StandardCharsets.UTF_8));
        premier.put("pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> second = new LinkedHashMap<>();
        second.put("pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8));
        second.put("package.json", "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(EmpreinteDocument.manifestes(premier), EmpreinteDocument.manifestes(second));
        assertTrue(EmpreinteDocument.manifestes(premier).matches("[0-9a-f]{64}"));
    }

    @Test
    void empreinteDistingueNatureEtContenu() {
        byte[] document = "{}".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(EmpreinteDocument.sbom(document), EmpreinteDocument.manifestes(Map.of("package.json", document)));
        assertNotEquals(EmpreinteDocument.sbom(document), EmpreinteDocument.sbom("{ }".getBytes(StandardCharsets.UTF_8)));
        Map<String, byte[]> decoupe = Map.of("a", "bc".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> autreDecoupe = Map.of("ab", "c".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(EmpreinteDocument.manifestes(decoupe), EmpreinteDocument.manifestes(autreDecoupe));
    }
}
