package io.argos.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Journal des appels MCP en base (MCP-06) : écriture réelle dans {@code mcp_appel}, paramètres en JSON, sujet OIDC
 * oublié pour un compte connu, outil et durée bornés. Les bornes et les pannes des journaux d'audit sont vérifiées hors
 * du conteneur ({@code JournalRefusTest}, {@code JournalAppelsMcpTest}, {@code JournalAppelsIaTest}).
 */
@QuarkusTest
class JournauxAuditTest {

    private static final int LONGUEUR_MAX_OUTIL = 100;

    @Inject
    TestDb baseDeTest;

    @Inject
    JournalAppelsMcp journalMcp;

    @BeforeEach
    void viderLesJournaux() {
        baseDeTest.reset();
    }

    @Test
    void appelMcpAvecCompteOublieLeSujetEtBorneOutilEtDuree() {
        UUID compte = UUID.randomUUID();
        journalMcp.enregistrer(compte, "sujet-ignore", "o".repeat(LONGUEUR_MAX_OUTIL + 1), "{}", -5,
                JournalAppelsMcp.Issue.PARAMETRES_INVALIDES);
        journalMcp.enregistrer(null, "sujet-garde", "search_signals", "{\"q\": 1}", Long.MAX_VALUE, JournalAppelsMcp.Issue.SUCCES);

        assertNull(baseDeTest.string("SELECT sujet_oidc FROM mcp_appel WHERE compte_id IS NOT NULL"));
        assertEquals(String.valueOf(LONGUEUR_MAX_OUTIL),
                baseDeTest.string("SELECT length(outil) FROM mcp_appel WHERE compte_id IS NOT NULL"));
        assertEquals("0", baseDeTest.string("SELECT duree_ms FROM mcp_appel WHERE compte_id IS NOT NULL"));
        assertEquals("sujet-garde", baseDeTest.string("SELECT sujet_oidc FROM mcp_appel WHERE compte_id IS NULL"));
        assertEquals(String.valueOf(Integer.MAX_VALUE), baseDeTest.string("SELECT duree_ms FROM mcp_appel WHERE compte_id IS NULL"));
    }
}
