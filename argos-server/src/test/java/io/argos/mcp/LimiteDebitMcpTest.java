package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/** MCP-06 : limitation de débit et journal des appels (tâche 2.4), avec une limite basse de test. */
@QuarkusTest
@TestProfile(LimiteDebitMcpTest.LimiteBasse.class)
class LimiteDebitMcpTest {

    /** Limite de 3 appels par minute (60 en exploitation, Q-1). */
    public static class LimiteBasse implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.mcp.debit.requetes-par-minute", "3");
        }
    }

    @Inject
    TestDb baseDeTest;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
    }

    /** MCP-06-S1 : limite atteinte : appel refusé avec une erreur de limite, refus journalisé. */
    @Test
    void mcp06S1LimiteDepassee() {
        UUID nina = Api.compte("nina");
        for (int tentative = 0; tentative < 3; tentative++) {
            ExtractableResponse<Response> acceptee = Mcp.appeler("nina", "get_security_alerts", Map.of("limit", 5));
            assertEquals(Boolean.FALSE, acceptee.path("result.isError"), acceptee.asString());
        }
        ExtractableResponse<Response> refus = Mcp.appeler("nina", "get_security_alerts", Map.of("limit", 5));
        assertEquals(Boolean.TRUE, refus.path("result.isError"), refus.asString());
        assertTrue(((String) refus.path("result.content[0].text")).startsWith("LIMITE_DEBIT"));

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + nina
                + "' AND motif = 'LIMITE_DEBIT' AND ressource = 'MCP tools/call get_security_alerts'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM mcp_appel WHERE compte_id = '" + nina + "' AND issue = 'REFUS_LIMITE'"));
        // Chaque appel admis est journalisé avec l'utilisateur, l'outil, les paramètres et la durée.
        assertEquals(3, baseDeTest.count("SELECT count(*) FROM mcp_appel WHERE compte_id = '" + nina + "' AND issue = 'SUCCES'"
                + " AND outil = 'get_security_alerts' AND parametres = '{\"limit\": 5}'::jsonb AND duree_ms >= 0"
                + " AND horodatage IS NOT NULL"));

        // La limite est propre à chaque utilisateur.
        Api.assurerCompte("oscar");
        assertEquals(Boolean.FALSE, Mcp.appeler("oscar", "get_security_alerts", Map.of()).path("result.isError"));
    }

    /** Les négociations {@code initialize} (sessions des versions antérieures) sont aussi limitées. */
    @Test
    void initialisationsLimitees() {
        Api.assurerCompte("nina");
        for (int tentative = 0; tentative < 3; tentative++) {
            ExtractableResponse<Response> acceptee = Mcp.initialiser(Api.en("nina"), "2025-06-18");
            assertEquals(200, acceptee.statusCode(), acceptee.asString());
            assertNotNull(acceptee.path("result.protocolVersion"), acceptee.asString());
        }
        ExtractableResponse<Response> refus = Mcp.initialiser(Api.en("nina"), "2025-06-18");
        assertNull(refus.path("result"), refus.asString());
        assertTrue(refus.asString().contains("LIMITE_DEBIT"), refus.asString());
        assertNull(refus.header("Mcp-Session-Id"), "aucune session ouverte au-delà de la limite");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE motif = 'LIMITE_DEBIT'"
                + " AND ressource = 'MCP initialize' AND sujet_oidc IS NOT NULL"));
    }

    /** Le rôle applicatif {@code argos_app} n'a aucun droit sur le journal des appels. */
    @Test
    void journalHorsRoleApplicatif() {
        assertFalse(Boolean.parseBoolean(baseDeTest.string("SELECT has_table_privilege('argos_app', 'mcp_appel', 'SELECT')::text")));
        assertFalse(Boolean.parseBoolean(baseDeTest.string("SELECT has_table_privilege('argos_app', 'mcp_appel', 'INSERT')::text")));
    }
}
