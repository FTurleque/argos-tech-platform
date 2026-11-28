package io.argos.releaseradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.RadarFixtures.Cycle;
import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/**
 * Connecteur endoflife.date (RAD) face aux réponses limites servies par un serveur simulé : identifiant refusé, 404,
 * erreur HTTP, réponse trop volumineuse, source injoignable, réponse illisible, cycles mal formés.
 */
class ClientEndoflifeTest {

    private static final int CYCLES_MAX = 500;
    private static final int TAILLE_REDUITE = 10;
    private static final int TAILLE_MAX = 1_048_576;
    private static final int ERREUR_SERVEUR = 500;
    private static final Duration DELAI = Duration.ofSeconds(5);
    private static final String PRODUITS = "/api/v1/products/";

    private ServeurHttpSimule serveur;

    @BeforeEach
    void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    @Test
    void identifiantDeProduitInvalideRefuseSansAppel() {
        ClientEndoflife client = client(serveur.base(), TAILLE_MAX);
        for (String produit : new String[] {null, "", "Majuscules", "../etc", "-tiret", "a".repeat(101)}) {
            assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles(produit), String.valueOf(produit));
        }
        assertEquals(List.of(), serveur.received("/"));
    }

    @Test
    void produitInconnuEtErreurHttpDistingues() {
        ClientEndoflife client = client(serveur.base(), TAILLE_MAX);
        assertEquals("produit inconnu d'endoflife.date",
                assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("inconnu")).getMessage());
        serveur.programmer(PRODUITS + "panne", Stub.status(ERREUR_SERVEUR));
        assertEquals("réponse HTTP 500",
                assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("panne")).getMessage());
    }

    @Test
    void reponseTropVolumineuseRefusee() {
        serveur.programmer(PRODUITS + "volumineux", Stub.succes(RadarFixtures.reponse("volumineux", cycle("1"))));
        ClientEndoflife client = client(serveur.base(), TAILLE_REDUITE);
        assertEquals("réponse trop volumineuse",
                assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("volumineux")).getMessage());
    }

    @Test
    void sourceInjoignableSignalee() {
        ClientEndoflife client = client("http://localhost:1/", TAILLE_MAX);
        assertTrue(assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("quarkus")).getMessage()
                .startsWith("source injoignable"));
    }

    @Test
    void reponseIllisibleOuSansListeDeCyclesRefusee() {
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire("{illisible"));
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire(""));
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire("{\"result\": {\"releases\": {}}}"));
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire("{\"result\": {}}"));
    }

    @Test
    void cyclesMalFormesIgnoresEtTextesNettoyes() throws ClientEndoflife.SourceIndisponible {
        List<ClientEndoflife.Cycle> cycles = lire("""
                {"result": {"releases": [
                  {"name": "  3.2\\u0007 ", "releaseDate": "2025-01-10", "eolFrom": "date-invalide", "isEol": true,
                   "latest": {"name": "3.2.5", "date": "2026-02-01"}},
                  {"name": 12},
                  {"name": "   "},
                  {"name": "%s"},
                  {"releaseDate": "2024-01-01"}
                ]}}
                """.formatted("x".repeat(101)));

        assertEquals(1, cycles.size());
        ClientEndoflife.Cycle cycle = cycles.get(0);
        assertEquals("3.2", cycle.cycle());
        assertEquals(LocalDate.parse("2025-01-10"), cycle.sortie());
        assertNull(cycle.finSupport(), "date illisible ignorée");
        assertTrue(cycle.enFinSupport());
        assertEquals("3.2.5", cycle.derniereVersion());
        assertEquals(LocalDate.parse("2026-02-01"), cycle.derniereVersionLe());
    }

    @Test
    void auPlusCinqCentsCyclesRetenus() throws ClientEndoflife.SourceIndisponible {
        String releases = IntStream.range(0, CYCLES_MAX + 3).mapToObj(rang -> "{\"name\": \"" + rang + "\"}")
                .collect(Collectors.joining(","));
        assertEquals(CYCLES_MAX, lire("{\"result\": {\"releases\": [" + releases + "]}}").size());
    }

    /** Client hors conteneur, sur l'adresse de base et la taille maximale données. */
    private static ClientEndoflife client(String base, int tailleMax) {
        ClientEndoflife client = new ClientEndoflife();
        client.base = base;
        client.delai = DELAI;
        client.tailleMax = tailleMax;
        client.initialiser();
        return client;
    }

    private static List<ClientEndoflife.Cycle> lire(String corps) throws ClientEndoflife.SourceIndisponible {
        return ClientEndoflife.lire(corps.getBytes(StandardCharsets.UTF_8));
    }

    private static Cycle cycle(String nom) {
        return new Cycle(nom, LocalDate.parse("2025-01-01"), null, false, nom + ".0", LocalDate.parse("2025-06-01"));
    }
}
