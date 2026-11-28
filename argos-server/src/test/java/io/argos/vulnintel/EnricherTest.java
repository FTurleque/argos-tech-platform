package io.argos.vulnintel;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.DataSourceEnPanne;

/** Enrichisseur (COL-04) : ses lectures échouent proprement sur une base injoignable. */
class EnricherTest {

    @Test
    void lecturesDeLEnrichisseurEnEchecSurBaseInjoignable() {
        Enricher enrichisseur = new Enricher();
        enrichisseur.dataSource = DataSourceEnPanne.creer();

        assertThrows(IllegalStateException.class, () -> enrichisseur.forSignal(UUID.randomUUID()));
    }
}
