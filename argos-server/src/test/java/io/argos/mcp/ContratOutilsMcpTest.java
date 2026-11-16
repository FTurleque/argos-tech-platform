package io.argos.mcp;

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
 * Contrat du serveur MCP (tâches 2.1, 3.2 et 3.4) : transport Streamable HTTP sans état, huit
 * outils de consultation seulement, schémas d'entrée et de sortie par outil, outil inconnu refusé.
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

    /** Transport sans état : aucune session n'est créée, la négociation avec état est refusée. */
    @Test
    void transportSansEtat() {
        ExtractableResponse<Response> r = Mcp.appeler("karim", "get_project_context", Map.of());
        assertEquals(200, r.statusCode(), r.asString());
        assertNull(r.header("Mcp-Session-Id"));
        assertEquals(Boolean.FALSE, r.path("result.isError"));

        ExtractableResponse<Response> init = Mcp.envoyer(Api.en("karim"), "initialize", null, Map.of(
                "protocolVersion", "2025-06-18", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "ancien-client", "version", "1")));
        assertNull(init.header("Mcp-Session-Id"));
        assertNotNull(init.path("error"), init.asString());
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
