package io.argos.projectintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Avancement avec dénominateur explicite (tâche 2.1 : PIN-02-S1, PIN-02-S2). */
class AvancementTest {

    @Test
    void pin02S1JalonAvecTotalConnu() {
        Avancement a = Avancement.de(27, 30).orElseThrow();
        assertEquals("27 sur 30 (90 %)", a.libelle());
        assertEquals(90, a.pourcentage());
        assertEquals(30, a.total());
    }

    @Test
    void pin02S2PasDeDenominateurPasDePourcentage() {
        assertTrue(Avancement.de(0, 0).isEmpty(), "jalon sans ticket : aucun pourcentage");
        assertTrue(Avancement.de(5, 0).isEmpty());
        assertTrue(Avancement.de(31, 30).isEmpty(), "chiffres incohérents : aucun pourcentage");
        assertTrue(Avancement.de(-1, 30).isEmpty());
    }

    @Test
    void arrondiALEntierInferieurJamaisCentPourCentAvantLaFin() {
        assertEquals("2 sur 3 (66 %)", Avancement.de(2, 3).orElseThrow().libelle());
        assertEquals(99, Avancement.de(299, 300).orElseThrow().pourcentage());
        assertEquals("30 sur 30 (100 %)", Avancement.de(30, 30).orElseThrow().libelle());
        assertEquals("0 sur 4 (0 %)", Avancement.de(0, 4).orElseThrow().libelle());
    }
}
