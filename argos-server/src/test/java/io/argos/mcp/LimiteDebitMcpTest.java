package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    TestDb db;

    @BeforeEach
    void preparer() {
        db.reset();
    }

    /** MCP-06-S1 : limite atteinte : appel refusé avec une erreur de limite, refus journalisé. */
    @Test
    void mcp06S1LimiteDepassee() {
        UUID nina = Api.compte("nina");
        for (int i = 0; i < 3; i++) {
            ExtractableResponse<Response> ok = Mcp.appeler("nina", "get_security_alerts", Map.of("limit", 5));
            assertEquals(Boolean.FALSE, ok.path("result.isError"), ok.asString());
        }
        ExtractableResponse<Response> refus = Mcp.appeler("nina", "get_security_alerts", Map.of("limit", 5));
        assertEquals(Boolean.TRUE, refus.path("result.isError"), refus.asString());
        assertTrue(((String) refus.path("result.content[0].text")).startsWith("LIMITE_DEBIT"));

        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + nina
                + "' AND motif = 'LIMITE_DEBIT' AND ressource = 'MCP tools/call get_security_alerts'"));
        assertEquals(1, db.count("SELECT count(*) FROM mcp_appel WHERE compte_id = '" + nina + "' AND issue = 'REFUS_LIMITE'"));
        // Chaque appel admis est journalisé avec l'utilisateur, l'outil, les paramètres et la durée.
        assertEquals(3, db.count("SELECT count(*) FROM mcp_appel WHERE compte_id = '" + nina + "' AND issue = 'SUCCES'"
                + " AND outil = 'get_security_alerts' AND parametres = '{\"limit\": 5}'::jsonb AND duree_ms >= 0"
                + " AND horodatage IS NOT NULL"));

        // La limite est propre à chaque utilisateur.
        Api.assurerCompte("oscar");
        assertEquals(Boolean.FALSE, Mcp.appeler("oscar", "get_security_alerts", Map.of()).path("result.isError"));
    }

    /** Le rôle applicatif {@code argos_app} n'a aucun droit sur le journal des appels. */
    @Test
    void journalHorsRoleApplicatif() {
        assertFalse(Boolean.parseBoolean(db.string("SELECT has_table_privilege('argos_app', 'mcp_appel', 'SELECT')::text")));
        assertFalse(Boolean.parseBoolean(db.string("SELECT has_table_privilege('argos_app', 'mcp_appel', 'INSERT')::text")));
    }

    /** Fenêtre glissante : un appel redevient possible une minute après le plus ancien. */
    @Test
    void fenetreGlissante() {
        LimiteurDebit limiteur = new LimiteurDebit();
        limiteur.requetesParMinute = 2;
        long[] maintenant = {1_000_000L};
        limiteur.horloge = () -> maintenant[0];
        UUID compte = UUID.randomUUID();
        assertTrue(limiteur.admettre(compte));
        maintenant[0] += 30_000;
        assertTrue(limiteur.admettre(compte));
        assertFalse(limiteur.admettre(compte));
        maintenant[0] += 30_001;
        assertTrue(limiteur.admettre(compte));
        assertFalse(limiteur.admettre(compte));
        assertTrue(limiteur.admettre(UUID.randomUUID()));
    }

    /** Les fenêtres échues sont retirées : la mémoire du limiteur ne croît pas avec les comptes inactifs. */
    @Test
    void fenetresEchuesRetirees() {
        LimiteurDebit limiteur = new LimiteurDebit();
        limiteur.requetesParMinute = 60;
        long[] maintenant = {0};
        limiteur.horloge = () -> maintenant[0];
        for (int i = 0; i < 255; i++) {
            limiteur.admettre(UUID.randomUUID());
        }
        assertEquals(255, limiteur.comptesSuivis());
        maintenant[0] = 61_000;
        limiteur.admettre(UUID.randomUUID());
        assertEquals(1, limiteur.comptesSuivis());
    }
}
