package io.argos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.argos.api.veille.VeilleProfilsResource;
import io.argos.impact.MatchRules;
import io.argos.mcp.OutilsSecurite;

/** TE-1 : le niveau d'impact est écrit sous son nom dans les réponses REST et les sorties MCP. */
class NiveauJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID PROJET = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void reponseDeSuiviRest() throws JsonProcessingException {
        String json = JSON.writeValueAsString(new WatchResource.WatchImpact(PROJET, PROJET, "Portail", MatchRules.Level.A_VERIFIER));
        assertEquals(PROJET.toString(), JSON.readTree(json).get("impactId").asText());
        assertEquals("A_VERIFIER", JSON.readTree(json).get("level").asText());
    }

    @Test
    void reponseMaVeilleRest() throws JsonProcessingException {
        String json = JSON.writeValueAsString(new VeilleProfilsResource.ProjetVue(PROJET, "Portail", MatchRules.Level.EXACT));
        assertEquals("EXACT", JSON.readTree(json).get("niveau").asText());
    }

    @Test
    void sortieMcpDeSecurite() throws JsonProcessingException {
        String json = JSON.writeValueAsString(
                new OutilsSecurite.ProjetConcerne(PROJET, "Portail", "pkg:maven/a/b@1", MatchRules.Level.PROBABLE));
        assertEquals("PROBABLE", JSON.readTree(json).get("niveau").asText());
    }
}
