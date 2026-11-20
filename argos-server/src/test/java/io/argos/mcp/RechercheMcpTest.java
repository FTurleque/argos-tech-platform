package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.JeuRecherche;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Outil MCP {@code search_signals} branché sur la recherche de veille (tâche 1.3 de
 * {@code rechercher-et-filtrer}) : mêmes mots, mêmes filtres et mêmes droits que l'API REST
 * (RCH-01, RCH-03), contenu externe toujours balisé (MCP-05).
 */
@QuarkusTest
class RechercheMcpTest {

    private static final String BASE = "result.structuredContent.";

    @Inject
    TestDb baseDeTest;

    @Inject
    JeuRecherche jeu;

    private UUID espaceOscar;
    private UUID espaceMarc;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espaceOscar = Api.nouvelEspace("oscar", "Espace Paie MCP");
        espaceMarc = Api.nouvelEspace("marc", "Espace Portail MCP");
    }

    private static Instant ilYA(int jours) {
        return Instant.now().minus(Duration.ofDays(jours));
    }

    /** RCH-01 : mots recherchés, pertinence décroissante, projets du périmètre, contenu externe balisé. */
    @Test
    void rch01MotsEtPertinence() {
        UUID paie = jeu.projet(espaceOscar, "Paie");
        UUID fort = jeu.signal("GHSA-MCP-RCH-1", "Oracle correctif critique", "Correctif Oracle.", "CRITICAL", ilYA(2), null);
        UUID faible = jeu.signal("GHSA-MCP-RCH-2", "Pilote", "Un correctif pour Oracle.", "LOW", ilYA(1), null);
        jeu.impact(fort, paie);

        ExtractableResponse<Response> reponse = Mcp.appeler("oscar", "search_signals", Map.of("query", "Oracle correctif"));
        assertEquals(Boolean.FALSE, reponse.path("result.isError"), reponse.asString());
        assertEquals(2, (int) reponse.path(BASE + "nombre"));
        assertEquals(fort.toString(), reponse.path(BASE + "signaux[0].signalId"));
        assertEquals(faible.toString(), reponse.path(BASE + "signaux[1].signalId"));
        assertTrue(((Number) reponse.path(BASE + "signaux[0].pertinence")).doubleValue()
                > ((Number) reponse.path(BASE + "signaux[1].pertinence")).doubleValue());
        assertEquals("Paie", reponse.path(BASE + "signaux[0].projets[0].projet"));
        assertEquals(TexteExterne.NATURE, reponse.path(BASE + "signaux[0].titre.nature"));

        // Filtres combinés : projet, gravité minimale, horizon.
        ExtractableResponse<Response> filtre = Mcp.appeler("oscar", "search_signals",
                Map.of("query", "Oracle", "project_ids", List.of(paie.toString()), "severity", "HIGH", "horizon_days", 30));
        assertEquals(1, (int) filtre.path(BASE + "nombre"), filtre.asString());
        assertEquals(fort.toString(), filtre.path(BASE + "signaux[0].signalId"));
        assertEquals(0, (int) Mcp.appeler("oscar", "search_signals", Map.of("query", "motabsentdetoutelement"))
                .path(BASE + "nombre"));
    }

    /** RCH-03 : un impact de l'espace B contenant « Portail » n'est pas renvoyé à un utilisateur du seul espace A. */
    @Test
    void rch03MotPresentDansUnAutreEspace() {
        UUID portail = jeu.projet(espaceMarc, "Portail");
        UUID signal = jeu.signal("GHSA-MCP-RCH-3", "Faille d'une bibliothèque", "Sans le mot.", "HIGH", ilYA(2), null);
        jeu.impact(signal, portail);

        ExtractableResponse<Response> oscar = Mcp.appeler("oscar", "search_signals", Map.of("query", "Portail"));
        assertEquals(0, (int) oscar.path(BASE + "nombre"), oscar.asString());
        assertFalse(oscar.asString().contains(signal.toString()));

        ExtractableResponse<Response> marc = Mcp.appeler("marc", "search_signals", Map.of("query", "Portail"));
        assertEquals(1, (int) marc.path(BASE + "nombre"), marc.asString());
        assertEquals("Portail", marc.path(BASE + "signaux[0].projets[0].projet"));

        // Signal public trouvé par ses mots : les projets d'un autre espace ne sont jamais cités.
        ExtractableResponse<Response> public_ = Mcp.appeler("oscar", "search_signals", Map.of("query", "bibliothèque"));
        assertEquals(1, (int) public_.path(BASE + "nombre"), public_.asString());
        assertEquals(0, (int) public_.path(BASE + "signaux[0].projets.size()"));
        assertFalse(public_.asString().contains("Portail"));

        // Projet d'un autre espace en filtre : refusé et journalisé, sans rien révéler.
        ExtractableResponse<Response> refuse = Mcp.appeler("oscar", "search_signals",
                Map.of("project_ids", List.of(portail.toString())));
        assertEquals(Boolean.TRUE, refuse.path("result.isError"), refuse.asString());
        assertFalse(refuse.asString().contains("Portail"));
    }

    /** Paramètres invalides : refusés avant tout appel métier. */
    @Test
    void parametresInvalides() {
        for (Map<String, Object> arguments : List.<Map<String, Object>>of(Map.of("severity", "EXTREME"),
                Map.of("horizon_days", 0), Map.of("project_ids", List.of("pas-un-uuid")), Map.of("query", "x".repeat(501)))) {
            ExtractableResponse<Response> reponse = Mcp.appeler("oscar", "search_signals", arguments);
            assertTrue(((String) reponse.path("result.content[0].text")).startsWith("PARAMETRES_INVALIDES"), reponse.asString());
        }
    }
}
