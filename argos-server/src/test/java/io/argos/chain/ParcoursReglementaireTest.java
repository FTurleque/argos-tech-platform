package io.argos.chain;

import static io.argos.chain.ParcoursIaTest.ollama;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.alerting.RappelsReglementaires;
import io.argos.regintel.CollecteReglementaire;
import io.argos.testsupport.Api;
import io.argos.testsupport.ArtisteFixtures;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Parcours de bout en bout de la veille réglementaire (tâche 5.2) : un décret de test est collecté dans
 * l'archive JORF simulée, rattaché au domaine « Carrière » par le dictionnaire de l'espace, sa fiche est
 * préparée par la passerelle IA (API Ollama simulée), corrigée puis validée par l'experte ; elle devient
 * visible d'un utilisateur du domaine par l'API REST et par le serveur MCP, et produit un rappel avant
 * son entrée en vigueur. Aucune requête externe.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ParcoursReglementaireTest {

    @Inject
    TestDb db;

    @Inject
    CollecteReglementaire collecte;

    @Inject
    RappelsReglementaires rappels;

    private UUID espace;
    private UUID ines;
    private UUID jules;

    @BeforeEach
    void preparer() {
        db.reset();
        espace = Api.nouvelEspace("hugo", "Espace RH parcours");
        Api.ajouterMembre("hugo", espace.toString(), "ines", "CONTRIBUTEUR");
        Api.ajouterMembre("hugo", espace.toString(), "jules", "LECTEUR");
        ines = Api.compte("ines");
        jules = Api.compte("jules");
        ArtisteFixtures.activer("hugo", "developpeur");
        ArtisteFixtures.activer("ines", "expert-metier");
        ArtisteFixtures.activer("jules", "developpeur", "expert-metier");
    }

    @Test
    void decretCollecteQualifiePrepareCorrigeValideEtDiffuse() {
        String e = "/api/v1/reglementaire/espaces/" + espace;
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("libelle", "Carrière"))
                .when().put(e + "/domaines/carriere").then().statusCode(200);
        Api.en("hugo").contentType(ContentType.JSON)
                .body(Map.of("regles", List.of(Map.of("type", "CODE", "valeur", "LEGITEXT000044416551"))))
                .when().put(e + "/domaines/carriere/regles").then().statusCode(200);
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("comptes", List.of(ines.toString())))
                .when().put(e + "/domaines/carriere/experts").then().statusCode(200);
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        StubServer.on(VeilleReglementaireTest.CHAT, ollama(
                "{\"resume\": \"Le décret modifie les règles d'avancement de grade.\", \"dateEffet\": \"2027-01-01\"}"));

        // 1. Collecte du décret de test et rattachement au domaine par le dictionnaire.
        CollecteReglementaire.Rapport r = collecte.collecter(LocalDate.of(2026, 11, 20), Instant.now());
        assertEquals(2, r.textesNouveaux());
        assertEquals(1, r.fichesPreparees(), "seul le décret relève du dictionnaire ; la proposition IA pour l arrêté est non conforme au schéma");

        // 2. Fiche préparée par l'IA, en attente : visible de l'experte seule, ni en REST ni en MCP pour jules.
        String id = Api.en("ines").when().get("/api/v1/reglementaire/fiches").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].texte.identifiant", equalTo(VeilleReglementaireTest.CARRIERE))
                .body("[0].domaines[0].code", equalTo("carriere"))
                .body("[0].mentionIa", equalTo("produit par l'IA"))
                .body("[0].etatValidation", equalTo("EN_ATTENTE_VALIDATION"))
                .extract().path("[0].id");
        Api.en("jules").when().get("/api/v1/reglementaire/fiches").then().statusCode(200).body("$", hasSize(0));
        assertEquals(0, (int) Mcp.appeler("jules", "get_regulatory_changes", Map.of()).path("result.structuredContent.nombre"));

        // 3. L'experte corrige la date d'effet, puis valide.
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("dateEffet", "2027-02-01"))
                .when().put("/api/v1/reglementaire/fiches/" + id).then().statusCode(200);
        Api.en("ines").when().post("/api/v1/reglementaire/fiches/" + id + "/validation").then().statusCode(200)
                .body("etatValidation", equalTo("VALIDEE"));

        // 4. Visible d'un utilisateur du domaine par l'API REST, avec sa mention de validation et l'historique.
        Api.en("jules").when().get("/api/v1/reglementaire/fiches?domaine=carriere").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].dateEffet", equalTo("2027-02-01"))
                .body("[0].mentionValidation", equalTo("validée par l'expert métier"));
        Api.en("jules").when().get("/api/v1/reglementaire/fiches/" + id).then().statusCode(200)
                .body("historique.action", org.hamcrest.Matchers.contains("PREPARATION", "CORRECTION", "VALIDATION"));

        // 5. Visible par le serveur MCP, avec les garde-fous du profil expert-metier.
        ExtractableResponse<Response> mcp = Mcp.appeler("jules", "get_regulatory_changes", Map.of("domain", "carriere"));
        String b = "result.structuredContent.";
        assertEquals(1, (int) mcp.path(b + "nombre"), mcp.asString());
        assertEquals(VeilleReglementaireTest.CARRIERE, mcp.path(b + "fiches[0].referenceOfficielle"));
        assertEquals("2027-02-01", mcp.path(b + "fiches[0].effet"));
        assertEquals("2026-11-20", mcp.path(b + "fiches[0].publication"));
        assertEquals("Le décret modifie les règles d'avancement de grade.", mcp.path(b + "fiches[0].resumeValide"));
        List<String> gardeFous = mcp.path(b + "gardeFous");
        assertTrue(gardeFous.stream().anyMatch(g -> g.contains("avis juridique")), gardeFous.toString());
        assertFalse(mcp.asString().contains(VeilleReglementaireTest.PAIE));

        // 6. Rappel J-30 de l'entrée en vigueur aux utilisateurs du domaine.
        List<UUID> crees = rappels.executer(LocalDate.of(2027, 1, 2), Instant.now());
        assertEquals(2, crees.size(), "l'experte et l'utilisateur au profil expert-metier");
        assertEquals(1, db.count("SELECT count(*) FROM alerte WHERE nature = 'RAPPEL' AND compte_id = '" + jules
                + "' AND cle_radar LIKE 'REGLEMENTAIRE|" + id + "|2027-02-01|J-30'"));
    }
}
