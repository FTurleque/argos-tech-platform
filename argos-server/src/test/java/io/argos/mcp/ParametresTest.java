package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Validation des paramètres d'outils MCP avant tout appel métier (MCP-03, RAD-07-S1 côté MCP). */
class ParametresTest {

    /** Un PURL mal encodé est refusé comme paramètre invalide (RAD-07-S1 côté MCP). */
    @Test
    void purlMalEncodeRefuseCommeParametreInvalide() {
        assertThrows(ParametreInvalide.class, () -> Parametres.cle(null, "pkg:maven/%zz/x"));
    }

    /** Un écosystème inconnu ou un nom absent est refusé ; un PURL ou un couple écosystème et nom donne la clé. */
    @Test
    void cleDuPaquet() {
        assertThrows(ParametreInvalide.class, () -> Parametres.cle("Inconnu", "x"));
        assertThrows(ParametreInvalide.class, () -> Parametres.cle("Maven", " "));
        assertEquals(Parametres.cle(null, "pkg:maven/org.exemple/lib@1.0.0"), Parametres.cle("Maven", "org.exemple:lib"));
    }
}
