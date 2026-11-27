package io.argos.releaseradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.DataSourceEnPanne;

/** Fraîcheur des cycles de vie (RAD) : aucune lecture sans produit demandé, échec signalé sur base injoignable. */
class CyclesDeVieTest {

    @Test
    void produitSansFraicheurDemandeeNeLitRien() {
        CyclesDeVie horsConteneur = new CyclesDeVie();
        horsConteneur.dataSource = DataSourceEnPanne.creer();

        assertEquals(List.of(), horsConteneur.etats(List.of()));
    }

    @Test
    void lectureDeLaFraicheurEnEchecSurBaseInjoignable() {
        CyclesDeVie horsConteneur = new CyclesDeVie();
        horsConteneur.dataSource = DataSourceEnPanne.creer();

        assertThrows(IllegalStateException.class, () -> horsConteneur.etats(List.of("biblio")));
    }
}
