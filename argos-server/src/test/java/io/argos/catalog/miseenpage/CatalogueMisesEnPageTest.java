package io.argos.catalog.miseenpage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Catalogue des mises en page (AFF-01, AFF-04, ADR-0023). */
class CatalogueMisesEnPageTest {

    @Test
    void leCatalogueInitialComprendLesQuatreMisesEnPage() {
        CatalogueMisesEnPage catalogue = new CatalogueMisesEnPage(new ObjectMapper(), Optional.empty());
        assertEquals(List.of("classique", "journal", "fantaisie", "gazette"),
                catalogue.publiees().stream().map(MiseEnPage::identifiant).toList());
    }

    @Test
    void uneMiseEnPageRetireeNEstPlusPubliee() {
        CatalogueMisesEnPage catalogue = new CatalogueMisesEnPage(new ObjectMapper(), Optional.of(Set.of("fantaisie")));
        assertFalse(catalogue.estPubliee("fantaisie"));
        assertTrue(catalogue.estPubliee("gazette"));
        assertFalse(catalogue.estPubliee(null));
    }

    @Test
    void laMiseEnPageParDefautNePeutPasEtreRetiree() {
        assertThrows(IllegalStateException.class,
                () -> new CatalogueMisesEnPage(new ObjectMapper(), Optional.of(Set.of("classique"))));
    }
}
