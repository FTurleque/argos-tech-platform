package io.argos.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.releaseradar.CyclesDeVie;
import io.argos.testsupport.Api;
import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.RadarFixtures.Cycle;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;

/** Production des suggestions de l'IA après la synchronisation des cycles de vie (RAD-08). */
@QuarkusTest
class SuggestionsRadarTest {

    private static final String CHAT = "/ollama/api/chat";
    private static final String POSTGRES = "pkg:generic/postgresql";
    private static final String PRODUIT = "postgresql";
    private static final String JUSTIFICATION = "La version 14 a atteint sa fin de support le jour publié.";

    @Inject
    TestDb baseDeTest;

    @Inject
    CyclesDeVie cycles;

    @Inject
    SuggestionsRadar suggestions;

    private final LocalDate aujourdhui = LocalDate.now();
    private UUID espace;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("nina", "Socle");
        RadarFixtures.publier(PRODUIT, RadarFixtures.reponse(PRODUIT,
                new Cycle("14", LocalDate.of(2021, 9, 30), aujourdhui.minusDays(1), true, "14.25", LocalDate.of(2026, 11, 9)),
                new Cycle("17", LocalDate.of(2024, 9, 26), aujourdhui.plusYears(3), false, "17.12", LocalDate.of(2026, 11, 9))));
        cycles.synchroniser(List.of(PRODUIT), Instant.now());
    }

    private static Stub ollama(String contenu) {
        try {
            return Stub.succes(new ObjectMapper().writeValueAsString(Map.of("model", "qwen2.5:1.5b",
                    "message", Map.of("role", "assistant", "content", contenu), "done", true,
                    "prompt_eval_count", 30, "eval_count", 12)));
        } catch (JsonProcessingException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    private static Stub avis(int avis) {
        return ollama("{\"avis\": [" + avis + "], \"justification\": \"" + JUSTIFICATION + "\"}");
    }

    private long enAttente() {
        return baseDeTest.count("SELECT count(*) FROM standard_suggestion WHERE espace_id = ? AND etat = 'EN_ATTENTE'"
                + " AND statut = 'HOLD' AND origine = 'IA'", espace);
    }

    @Test
    void rad08S1VersionRecommandeeEnFinDeSupport() {
        RadarFixtures.standard(baseDeTest, espace, POSTGRES, "PostgreSQL", "ADOPT", "14", "Version courante", PRODUIT);
        StubServer.programmer(CHAT, avis(1));

        assertEquals(1, suggestions.produire(aujourdhui).suggestions());

        assertEquals(1, enAttente());
        assertEquals(JUSTIFICATION, baseDeTest.string("SELECT justification FROM standard_suggestion WHERE espace_id = '"
                + espace + "'"));
        assertEquals("ADOPT", baseDeTest.string("SELECT statut FROM standard_technologie WHERE espace_id = ?", espace),
                "le référentiel est inchangé");
        assertTrue(StubServer.received(CHAT).get(0).body().contains("PostgreSQL"), "la technologie est transmise");
    }

    @Test
    void rad08S2SuggestionDejaEnAttente() {
        RadarFixtures.standard(baseDeTest, espace, POSTGRES, "PostgreSQL", "ADOPT", "14", "Version courante", PRODUIT);
        StubServer.programmer(CHAT, avis(1), avis(1));

        suggestions.produire(aujourdhui);
        assertEquals(0, suggestions.produire(aujourdhui).suggestions());

        assertEquals(1, enAttente());
        assertEquals(1, StubServer.received(CHAT).size(), "aucune demande pour une suggestion déjà en attente");
    }

    @Test
    void rad08S3PasserelleIndisponibleOuAvisContraire() {
        RadarFixtures.standard(baseDeTest, espace, POSTGRES, "PostgreSQL", "ADOPT", "14", "Version courante", PRODUIT);
        StubServer.programmer(CHAT, Stub.status(503), avis(2), ollama("{\"avis\": \"oui\"}"));

        assertEquals(0, suggestions.produire(aujourdhui).suggestions());
        assertEquals(0, suggestions.produire(aujourdhui).suggestions());
        assertEquals(0, suggestions.produire(aujourdhui).suggestions());

        assertEquals(0, enAttente());
    }

    @Test
    void rad08S4VersionEncoreSupportee() {
        RadarFixtures.standard(baseDeTest, espace, POSTGRES, "PostgreSQL", "ADOPT", "17", "Version courante", PRODUIT);

        assertEquals(0, suggestions.produire(aujourdhui).suggestions());

        assertEquals(0, StubServer.received(CHAT).size());
    }

    @Test
    void uneVersionDejaEnHoldNEstPasSoumise() {
        RadarFixtures.standard(baseDeTest, espace, POSTGRES, "PostgreSQL", "HOLD", "14", "Abandonné", PRODUIT);

        assertEquals(0, suggestions.produire(aujourdhui).suggestions());

        assertEquals(0, StubServer.received(CHAT).size());
    }
}
