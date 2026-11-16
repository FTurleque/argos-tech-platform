package io.argos.catalog.miseenpage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
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

    @Test
    void uneProprieteInconnueEstRefusee() {
        String json = "[{\"identifiant\":\"classique\",\"libelle\":\"Classique\",\"couleur\":\"rouge\"}]";
        assertThrows(UnrecognizedPropertyException.class, () -> CatalogueMisesEnPage.analyser(new ObjectMapper(),
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void unIdentifiantEnDoubleEstRefuse() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new CatalogueMisesEnPage(
                List.of(new MiseEnPage("classique", "Classique", null), new MiseEnPage("classique", "Bis", null)),
                Set.of()));
        assertTrue(e.getMessage().contains("deux fois"));
    }

    @Test
    void unIdentifiantNulOuMalFormeEstRefuseAvecUnMessageExplicite() {
        IllegalStateException nul = assertThrows(IllegalStateException.class, () -> new CatalogueMisesEnPage(
                List.of(new MiseEnPage("classique", "Classique", null), new MiseEnPage(null, "Sans identifiant", null)),
                Set.of()));
        assertTrue(nul.getMessage().contains("identifiant absent ou mal formé"));
        assertThrows(IllegalStateException.class, () -> new CatalogueMisesEnPage(
                List.of(new MiseEnPage("classique", "Classique", null), new MiseEnPage("a".repeat(65), "Long", null)),
                Set.of()));
        assertThrows(IllegalStateException.class, () -> new CatalogueMisesEnPage(
                List.of(new MiseEnPage("classique", "", null)), Set.of()));
    }
}
