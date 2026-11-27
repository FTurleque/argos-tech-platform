package io.argos.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.DataSourceEnPanne;

/** Journal des appels d'outils MCP hors du conteneur (MCP-06) : une base injoignable n'interrompt pas l'appelant. */
class JournalAppelsMcpTest {

    @Test
    void appelMcpNonJournaliseSansInterrompreLAppelant() {
        JournalAppelsMcp enPanne = new JournalAppelsMcp();
        enPanne.dataSource = DataSourceEnPanne.creer();

        assertDoesNotThrow(() -> enPanne.enregistrer(null, "sujet", "outil", "{}", 1, JournalAppelsMcp.Issue.ERREUR));
    }
}
