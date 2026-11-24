package io.argos.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Décodage d'une clé de paquet (I-5 du changement {@code isoler-erreurs-aux-frontieres}, AUD-QUA-20) : un PURL mal
 * encodé est illisible, sans erreur d'exécution, quel que soit le segment fautif.
 */
class PackageKeyDecodageTest {

    @ParameterizedTest
    @ValueSource(strings = {"pkg:npm/%zz@1.0.0", "pkg:maven/%zz/x", "pkg:npm/lib@%", "pkg:npm/%e9%", "pkg:maven/org.exemple/lib@1.%2"})
    void unPurlMalEncodeEstIllisible(String purl) {
        assertEquals(Optional.empty(), PackageKey.parse(purl));
    }

    @Test
    void unPurlBienEncodeResteDecode() {
        Optional<PackageKey.Purl> purl = PackageKey.parse("pkg:npm/%40scope/lib@1.0.0");
        assertTrue(purl.isPresent());
        assertEquals("pkg:npm/@scope/lib", purl.get().key());
        assertEquals("1.0.0", purl.get().version());
        assertEquals("pkg:maven/org.exemple/lib", PackageKey.parse("pkg:maven/org.exemple/lib").orElseThrow().key());
    }
}
