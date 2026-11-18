package io.argos.mcp;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Contrat du serveur MCP (tâches 2.1, 3.2 et 3.4) : transport Streamable HTTP, sans session en
 * version 2026-07-28, avec session pour une version antérieure négociée par {@code initialize} ;
 * huit outils de consultation seulement, schémas d'entrée et de sortie par outil, outil inconnu refusé.
 */
@QuarkusTest
class ContratOutilsMcpTest {

    /** Outils exposés (proposition, What Changes). */
    static final Set<String> OUTILS = Set.of("search_signals", "check_dependency", "get_technology_standard",
            "get_project_context", "upcoming_releases", "get_security_alerts", "explain_change", "get_regulatory_changes");

    @Inject
    TestDb db;

    @BeforeEach
    void preparer() {
        db.reset();
        Api.assurerCompte("karim");
    }

    /** MCP-01-S1 : seuls des outils de consultation sont proposés. */
    @Test
    void mcp01S1SeulsDesOutilsDeConsultation() {
        ExtractableResponse<Response> r = Mcp.lister("karim");
        assertEquals(200, r.statusCode(), r.asString());
        List<Map<String, Object>> outils = r.path("result.tools");
        assertEquals(new TreeSet<>(OUTILS), new TreeSet<>(outils.stream().map(o -> (String) o.get("name")).toList()));
        for (Map<String, Object> outil : outils) {
            @SuppressWarnings("unchecked")
            Map<String, Object> annotations = (Map<String, Object>) outil.get("annotations");
            assertNotNull(annotations, outil.get("name") + " : annotations absentes");
            assertEquals(Boolean.TRUE, annotations.get("readOnlyHint"), outil.get("name") + " : lecture seule");
            assertEquals(Boolean.FALSE, annotations.get("destructiveHint"), outil.get("name") + " : non destructif");
        }
    }

    /** Tâche 3.2 : chaque outil publie un schéma d'entrée et un schéma de sortie objets. */
    @Test
    void chaqueOutilPublieSesSchemas() {
        List<Map<String, Object>> outils = Mcp.lister("karim").path("result.tools");
        for (Map<String, Object> outil : outils) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entree = (Map<String, Object>) outil.get("inputSchema");
            @SuppressWarnings("unchecked")
            Map<String, Object> sortie = (Map<String, Object>) outil.get("outputSchema");
            assertEquals("object", entree.get("type"), outil.get("name") + " : schéma d'entrée");
            assertNotNull(sortie, outil.get("name") + " : schéma de sortie");
            assertEquals("object", sortie.get("type"), outil.get("name") + " : schéma de sortie");
            assertFalse(((String) outil.get("description")).isBlank());
        }
    }

    /** MCP-01-S2 : un outil d'écriture inexistant échoue, sans aucune modification. */
    @Test
    void mcp01S2OutilInconnu() {
        long avant = lignes();
        for (String outil : List.of("delete_project", "update_subscription", "create_workspace")) {
            ExtractableResponse<Response> r = Mcp.appeler("karim", outil, Map.of("id", "x"));
            assertEquals(-32602, (int) r.path("error.code"), r.asString());
            assertTrue(((String) r.path("error.message")).contains(outil));
            assertNull(r.path("result"));
        }
        assertEquals(avant, lignes());
        assertEquals(0, db.count("SELECT count(*) FROM mcp_appel"));
    }

    /** Protocole 2026-07-28 : un appel d'outil s'exécute sans négociation préalable et sans session. */
    @Test
    void appelSansSessionEnVersion20260728() {
        ExtractableResponse<Response> r = Mcp.appeler("karim", "get_project_context", Map.of());
        assertEquals(200, r.statusCode(), r.asString());
        assertNull(r.header("Mcp-Session-Id"));
        assertEquals(Boolean.FALSE, r.path("result.isError"));
    }

    /**
     * Écart constaté (fiche {@code serveur-mcp}) : un client en version 2025-06-18 négocie par
     * {@code initialize} et obtient une session ; la session ne remplace jamais le jeton porteur.
     */
    @Test
    void initializeAncienneVersionOuvreUneSessionSansRemplacerLeJeton() {
        ExtractableResponse<Response> init = Mcp.initialiser(Api.en("karim"), "2025-06-18");
        assertEquals(200, init.statusCode(), init.asString());
        assertEquals("2025-06-18", init.path("result.protocolVersion"), init.asString());
        String session = init.header("Mcp-Session-Id");
        assertNotNull(session, "session ouverte pour un client en version 2025-06-18");

        int statut = given().header("Mcp-Session-Id", session).contentType("application/json")
                .accept("application/json, text/event-stream")
                .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}")
                .when().post("/mcp").then().extract().statusCode();
        assertEquals(401, statut, "une session présentée sans jeton est refusée");
    }

    /** Découverte sans état : version de protocole 2026-07-28 et capacité « outils » annoncées. */
    @Test
    void decouverteDuServeur() {
        ExtractableResponse<Response> r = Mcp.envoyer(Api.en("karim"), "server/discover", null, Map.of());
        assertEquals(200, r.statusCode(), r.asString());
        assertTrue(r.asString().contains(Mcp.VERSION), r.asString());
        assertNotNull(r.path("result.capabilities.tools"), r.asString());
        assertTrue(r.asString().contains("argos"), r.asString());
    }

    private long lignes() {
        return db.count("SELECT (SELECT count(*) FROM espace) + (SELECT count(*) FROM appartenance)"
                + " + (SELECT count(*) FROM project) + (SELECT count(*) FROM subscription) + (SELECT count(*) FROM compte)");
    }
}
