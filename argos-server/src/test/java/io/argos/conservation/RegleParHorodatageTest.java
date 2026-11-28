package io.argos.conservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Nom d'une règle par horodatage et contrôle des identifiants de sa suppression (ADR-0041). */
class RegleParHorodatageTest {

    /** Règle d'essai, hors CDI : seule sa construction est éprouvée. */
    private static final class RegleEssai extends RegleParHorodatage {

        RegleEssai(String table, String colonne) {
            super("essai", table, colonne);
        }
    }

    @Test
    void identifiantsValidesAcceptes() {
        assertEquals("essai", new RegleEssai("ops_event", "occurred_at").nom());
    }

    @Test
    void identifiantInvalideRefuse() {
        assertThrows(IllegalArgumentException.class, () -> new RegleEssai("ops_event; DROP TABLE compte", "occurred_at"));
        assertThrows(IllegalArgumentException.class, () -> new RegleEssai("ops_event", "occurred_at < now() OR true"));
    }
}
