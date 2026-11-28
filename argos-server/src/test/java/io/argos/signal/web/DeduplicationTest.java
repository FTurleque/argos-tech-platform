package io.argos.signal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.argos.ingestion.web.LecteurFlux.ElementFlux;

/** Empreinte de déduplication d'une actualité (WEB-05) : un même article reçu par deux flux a la même empreinte. */
class DeduplicationTest {

    private static final Instant PUBLICATION = Instant.parse("2026-11-15T09:00:00Z");

    private static ElementFlux element(String identifiant, String titre, String lien, String texte) {
        return new ElementFlux(identifiant, titre, lien, texte, PUBLICATION);
    }

    /** WEB-05 : le même article dans deux flux, lien écrit autrement (casse de l'hôte, barre finale, utm_*). */
    @Test
    void memeArticleDansDeuxFluxAMemeEmpreinte() {
        ElementFlux fluxA = element("https://blog.exemple.test/angular-19", "Angular 19",
                "https://blog.exemple.test/angular-19", "Sortie d'Angular 19.");
        ElementFlux fluxB = element("https://BLOG.exemple.test/angular-19/?utm_source=b", "Angular 19",
                "https://BLOG.exemple.test/angular-19/?utm_source=b", "Sortie d'Angular 19.");

        assertEquals("https://blog.exemple.test/angular-19", Deduplication.normaliser(fluxB.lien()));
        assertEquals(Deduplication.article(fluxA), Deduplication.article(fluxB));
    }

    @Test
    void lienNormaliseConserveLesParametresUtiles() {
        assertEquals("https://exemple.test/a?page=2", Deduplication.normaliser("HTTPS://Exemple.test:443/a/?utm_medium=x&page=2#haut"));
        assertEquals("http://exemple.test:8080/", Deduplication.normaliser("http://exemple.test:8080"));
        assertNotEquals(Deduplication.normaliser("https://exemple.test/a?page=2"),
                Deduplication.normaliser("https://exemple.test/a?page=3"));
    }

    @Test
    void articleSansLienIdentifieParSonIdentifiantPuisParSonContenu() {
        assertEquals(Deduplication.article(element("urn:1", "Titre A", null, "Texte A")),
                Deduplication.article(element("urn:1", "Titre B", null, "Texte B")), "même identifiant, même article");
        assertNotEquals(Deduplication.article(element("urn:1", "Titre", null, "Texte")),
                Deduplication.article(element("urn:2", "Titre", null, "Texte")));
        assertEquals(Deduplication.article(element(null, "Titre", null, "Texte")),
                Deduplication.article(element(null, "Titre", null, "Texte")));
        assertNotEquals(Deduplication.article(element(null, "Titre", null, "Texte")),
                Deduplication.article(element(null, "Titre", null, "Autre texte")));
    }
}
