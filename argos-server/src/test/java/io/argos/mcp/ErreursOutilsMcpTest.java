package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.catalog.standard.ReferentielStandards;
import io.argos.testsupport.Api;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.Substitution;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Erreurs internes des outils MCP (MCP-08, AUD-QUA-20, frontière d'erreur d'arc42 §8.5) : une panne d'accès aux
 * données répond {@code INDISPONIBLE} ; une erreur imprévue est journalisée et relancée, l'extension répondant par une
 * erreur de protocole sans donnée ni message interne ; l'appel est inscrit en erreur dans les deux cas. Un PURL mal
 * encodé est un paramètre invalide (RAD-07, COL-11).
 */
@QuarkusTest
class ErreursOutilsMcpTest {

    private static final Map<String, String> DEPENDANCE = Map.of("ecosystem", "Maven", "name", "org.exemple:lib",
            "version", "1.0.0");

    @Inject
    TestDb baseDeTest;

    @Inject
    OutilsSecurite outils;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        Api.assurerCompte("karim");
    }

    /** MCP-08-S1 : une panne d'accès aux données répond INDISPONIBLE, sans donnée partielle, journalisée en erreur. */
    @Test
    void mcp08S1PanneDAccesAuxDonnees() {
        ExtractableResponse<Response> reponse;
        try (Substitution substitution = Substitution.remplacer(outils, "referentiel", referentielEnErreur(
                new IllegalStateException("panne simulée d'accès aux données")))) {
            reponse = Mcp.appeler("karim", "check_dependency", DEPENDANCE);
        }

        assertEquals(200, reponse.statusCode(), reponse.asString());
        assertEquals(Boolean.TRUE, reponse.path("result.isError"), reponse.asString());
        String texte = reponse.path("result.content[0].text");
        assertTrue(texte.startsWith("INDISPONIBLE"), texte);
        assertNull(reponse.path("result.structuredContent"), "aucune donnée partielle");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM mcp_appel WHERE outil = 'check_dependency' AND issue = 'ERREUR'"));
    }

    /** MCP-08-S2 : une erreur imprévue est relancée en erreur de protocole, sans message interne, journalisée en erreur. */
    @Test
    void mcp08S2ErreurImprevueDUnOutil() {
        ExtractableResponse<Response> reponse;
        try (Substitution substitution = Substitution.remplacer(outils, "referentiel", referentielEnErreur(
                new IllegalArgumentException("détail interne simulé")))) {
            reponse = Mcp.appeler("karim", "check_dependency", DEPENDANCE);
        }

        assertNotNull(reponse.path("error"), "erreur de protocole : " + reponse.asString());
        assertNull(reponse.path("result"), "aucun résultat partiel");
        assertFalse(reponse.asString().contains("détail interne simulé"), "aucun message interne : " + reponse.asString());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM mcp_appel WHERE outil = 'check_dependency' AND issue = 'ERREUR'"));
    }

    /** Un PURL mal encodé est refusé PARAMETRES_INVALIDES (RAD-07-S1 côté MCP). */
    @Test
    void purlMalEncodeRefuseCommeParametreInvalide() {
        ExtractableResponse<Response> reponse = Mcp.appeler("karim", "check_dependency",
                Map.of("name", "pkg:maven/%zz/x", "version", "1.0.0"));

        assertEquals(Boolean.TRUE, reponse.path("result.isError"), reponse.asString());
        String texte = reponse.path("result.content[0].text");
        assertTrue(texte.startsWith("PARAMETRES_INVALIDES"), texte);
    }

    private static ReferentielStandards referentielEnErreur(RuntimeException erreur) {
        return (cle, espaces) -> {
            throw erreur;
        };
    }
}
