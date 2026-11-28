package io.argos.releaseradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.DataSourceEnPanne;

/** Calendrier des échéances (RAD) : appartenance d'une version à un cycle, aucune lecture sans espace demandé. */
class CalendrierEndoflifeTest {

    private static final String CLE = "pkg:maven/org.exemple/biblio";
    private static final LocalDate DEBUT = LocalDate.parse("2020-01-01");
    private static final LocalDate FIN = LocalDate.parse("2030-12-31");

    @Test
    void appartenanceDUneVersionAUnCycle() {
        assertTrue(CalendrierEndoflife.appartient(" V3.2.1 ", "3.2"));
        assertTrue(CalendrierEndoflife.appartient("3.2", "3.2"));
        assertFalse(CalendrierEndoflife.appartient("3.20", "3.2"));
        assertFalse(CalendrierEndoflife.appartient("13.2", "3.2"));
    }

    @Test
    void echeancesSansEspaceDemandeNeLisentRienMemeSurBaseInjoignable() {
        CalendrierEndoflife horsConteneur = new CalendrierEndoflife();
        horsConteneur.dataSource = DataSourceEnPanne.creer();

        assertEquals(List.of(), horsConteneur.echeances(List.of(CLE), Set.of(), DEBUT, FIN), "aucun espace : aucune lecture");
    }
}
