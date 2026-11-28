package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Balisage du contenu externe rendu par les outils MCP (MCP-04) : troncature sur les points de code. */
class BalisageExterneTest {

    private static final int LONGUEUR_MAX = 3;
    private static final int LONGUEUR_ORIGINALE = 5;

    /** Balisage : troncature sur les points de code, texte absent conservé absent. */
    @Test
    void balisageUnitaire() {
        BalisageExterne balisage = new BalisageExterne(LONGUEUR_MAX);
        TexteExterne texteBalise = balisage.baliser("NVD", "a😀bcd");
        assertEquals("a😀b", texteBalise.texte());
        assertTrue(texteBalise.tronque());
        assertEquals(LONGUEUR_ORIGINALE, texteBalise.longueurOriginale());
        assertNull(balisage.baliser("NVD", null));
        assertFalse(balisage.baliser("NVD", "abc").tronque());
    }
}
