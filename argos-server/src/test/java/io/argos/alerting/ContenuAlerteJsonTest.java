package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.argos.impact.MatchRules;

/**
 * TE-2 : un contenu d'alerte stocké avant le typage du niveau se relit ; le JSON écrit reste identique
 * ({@code niveau} porte le nom du niveau d'impact ou, pour un rappel du radar, la version).
 */
class ContenuAlerteJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID PROJET = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static String projetStocke(String niveau) {
        return "{\"id\":\"" + PROJET + "\",\"nom\":\"Portail\",\"niveau\":\"" + niveau + "\"}";
    }

    @Test
    void unNiveauStockeSeRelitSousLEnumeration() throws JsonProcessingException {
        for (MatchRules.Level niveau : MatchRules.Level.values()) {
            ContenuAlerte.Projet projet = JSON.readValue(projetStocke(niveau.name()), ContenuAlerte.Projet.class);
            assertEquals(niveau, projet.niveauImpact());
            assertNull(projet.version());
            assertEquals(projetStocke(niveau.name()), JSON.writeValueAsString(projet));
        }
    }

    @Test
    void uneVersionDeRappelDuRadarSeRelitEtSeReecritIdentique() throws JsonProcessingException {
        ContenuAlerte.Projet projet = JSON.readValue(projetStocke("version 17"), ContenuAlerte.Projet.class);
        assertNull(projet.niveauImpact());
        assertEquals("version 17", projet.version());
        assertEquals(projetStocke("version 17"), JSON.writeValueAsString(projet));
    }

    @Test
    void unContenuCompletSeRelit() throws JsonProcessingException {
        String stocke = "{\"elements\":[{\"signalId\":null,\"titre\":\"t\",\"gravite\":\"HIGH\",\"projets\":["
                + projetStocke("EXACT") + "],\"sources\":[],\"lien\":\"l\"}],\"resumeIa\":null,\"etatResumeIa\":\"SANS_OBJET\","
                + "\"mentionResumeIa\":null}";
        ContenuAlerte contenu = JSON.readValue(stocke, ContenuAlerte.class);
        assertEquals(MatchRules.Level.EXACT, contenu.elements().get(0).projets().get(0).niveauImpact());
        assertTrue(JSON.writeValueAsString(contenu).contains("\"niveau\":\"EXACT\""));
    }

    @Test
    void unProjetSansQualificatifEstRefuse() {
        assertThrows(JsonProcessingException.class,
                () -> JSON.readValue("{\"id\":\"" + PROJET + "\",\"nom\":\"Portail\"}", ContenuAlerte.Projet.class));
    }
}
