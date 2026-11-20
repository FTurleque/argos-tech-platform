package io.argos.projectintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Avancement avec dénominateur explicite (tâche 2.1 : PIN-02-S1, PIN-02-S2). */
class AvancementTest {

    @Test
    void pin02S1JalonAvecTotalConnu() {
        Avancement avancement = Avancement.calculer(27, 30).orElseThrow();
        assertEquals("27 sur 30 (90 %)", avancement.libelle());
        assertEquals(90, avancement.pourcentage());
        assertEquals(30, avancement.total());
    }

    @Test
    void pin02S2PasDeDenominateurPasDePourcentage() {
        assertTrue(Avancement.calculer(0, 0).isEmpty(), "jalon sans ticket : aucun pourcentage");
        assertTrue(Avancement.calculer(5, 0).isEmpty());
        assertTrue(Avancement.calculer(31, 30).isEmpty(), "chiffres incohérents : aucun pourcentage");
        assertTrue(Avancement.calculer(-1, 30).isEmpty());
    }

    @Test
    void arrondiALEntierInferieurJamaisCentPourCentAvantLaFin() {
        assertEquals("2 sur 3 (66 %)", Avancement.calculer(2, 3).orElseThrow().libelle());
        assertEquals(99, Avancement.calculer(299, 300).orElseThrow().pourcentage());
        assertEquals("30 sur 30 (100 %)", Avancement.calculer(30, 30).orElseThrow().libelle());
        assertEquals("0 sur 4 (0 %)", Avancement.calculer(0, 4).orElseThrow().libelle());
    }
}
