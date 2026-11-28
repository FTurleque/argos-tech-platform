package io.argos.signal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.argos.ingestion.web.RecuperationRefusee;

/** Forme canonique des adresses de veille web (WEB-01) : casse, port par défaut et fragment normalisés. */
class SourcesWebTest {

    @Test
    void adresseCanonique() {
        assertEquals("https://exemple.test/flux", SourcesWeb.normaliser("HTTPS://Exemple.TEST:443/flux"));
        assertEquals("http://exemple.test/", SourcesWeb.normaliser("http://EXEMPLE.test:80"));
        assertEquals("http://exemple.test:8080/a/b?x=1", SourcesWeb.normaliser("http://exemple.test:8080/a/./b?x=1#frag"));
        assertEquals("https://exemple.test/Chemin/?utm_source=x", SourcesWeb.normaliser("https://exemple.test/Chemin/?utm_source=x"),
                "chemin et paramètres conservés tels quels");
        assertEquals(RecuperationRefusee.Motif.ADRESSE_INVALIDE, assertThrows(RecuperationRefusee.class,
                () -> SourcesWeb.normaliser("http://user:secret@exemple.test/")).motif());
        assertEquals(RecuperationRefusee.Motif.ADRESSE_INVALIDE, assertThrows(RecuperationRefusee.class,
                () -> SourcesWeb.normaliser("ftp://exemple.test/")).motif());
    }
}
