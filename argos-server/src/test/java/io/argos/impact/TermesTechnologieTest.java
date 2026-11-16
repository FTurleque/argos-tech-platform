package io.argos.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Nom de technologie déduit d'une clé de paquet et recherche par mot entier (WEB-06). */
class TermesTechnologieTest {

    @Test
    void termeDeduitDeLaCle() {
        assertEquals(Optional.of("angular"), RapprochementActualites.terme("pkg:npm/@angular/core"));
        assertEquals(Optional.of("react"), RapprochementActualites.terme("pkg:npm/react"));
        assertEquals(Optional.of("spring-core"), RapprochementActualites.terme("pkg:maven/org.springframework/spring-core"));
        assertEquals(Optional.empty(), RapprochementActualites.terme("pkg:npm/ms"));
        assertEquals(Optional.empty(), RapprochementActualites.terme("pas-une-cle"));
    }

    @Test
    void motEntierSansTenirCompteDeLaCasse() {
        assertTrue(RapprochementActualites.mentionne("Angular 18 est disponible", "angular"));
        assertTrue(RapprochementActualites.mentionne("Migration vers spring-core 6.2.", "spring-core"));
        assertFalse(RapprochementActualites.mentionne("AngularJS arrive en fin de vie", "angular"));
        assertFalse(RapprochementActualites.mentionne("Preact 11", "react"));
    }
}
