package io.argos.api.veille;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.aigateway.ReponseIa;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.regintel.FicheAnalyse;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Veille restituée comme l'union des profils actifs ({@code GET /api/v1/me/veille}) et garde-fous appliqués
 * par le serveur, dans l'API et le serveur MCP (PRF-02, PRF-06). Jetons réels, lectures sous la RLS du rôle
 * {@code argos_app} : un avis de sécurité réel (collecte OSV simulée, rapprochement du moteur d'impact) et
 * des fiches réglementaires de l'espace.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class VeilleProfilsTest {

    private static final String LIB = "pkg:maven/org.exemple/veille";
    private static final String AVIS = "GHSA-VEILLE-PRF";
    private static final String VALIDEE = "JORFTEXT000097100001";
    private static final String EN_ATTENTE = "JORFTEXT000097100002";

    @Inject
    TestDb baseDeTest;

    @Inject
    ReglementaireFixtures reg;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    private UUID projet;
    private UUID ficheEnAttente;
    private UUID ficheValidee;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        for (String utilisateur : List.of("ines", "jules", "oscar")) {
            baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = '" + Api.compte(utilisateur) + "'");
        }
        UUID espace = Api.nouvelEspace("ines", "Veille des profils");
        Api.ajouterMembre("ines", espace.toString(), "jules", "LECTEUR");
        Api.ajouterMembre("ines", espace.toString(), "oscar", "CONTRIBUTEUR");

        projet = projects.create(espace, "paie-app", null, null);
        inventories.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"c", LIB + "@1.2.0"}), List.of("c"))
                .getBytes(StandardCharsets.UTF_8), "ci");
        Fixtures.stubOsv(AVIS, "2026-11-10T00:00:00Z",
                Fixtures.osv(AVIS, "2026-11-10T00:00:00Z", "Maven", "org.exemple:veille", LIB, "0", "1.3.0"));
        synchronizer.synchronize("OSV");

        UUID oscar = Api.compte("oscar");
        reg.domaine(espace, "paie", "Paie", Api.compte("ines"));
        reg.expert(espace, "paie", oscar);
        ficheValidee = reg.fiche(reg.texte(VALIDEE, "Décret relatif à la paie", LocalDate.of(2026, 11, 2), LocalDate.of(2027, 1, 1)),
                espace, "paie", "VALIDEE", "Résumé relu par l'expert.", oscar);
        ficheEnAttente = reg.fiche(reg.texte(EN_ATTENTE, "Arrêté relatif à la paie", LocalDate.of(2026, 11, 3), null),
                espace, "paie", "EN_ATTENTE_VALIDATION", "Résumé proposé.", oscar);
    }

    private static void activer(String utilisateur, String... profils) {
        Api.en(utilisateur).contentType(ContentType.JSON)
                .body(Map.of("profilsActifs", List.of(profils)))
                .when().put("/api/v1/preferences/profils").then().statusCode(200);
    }

    private static JsonPath veille(String utilisateur) {
        return Api.en(utilisateur).when().get("/api/v1/me/veille").then().statusCode(200).extract().jsonPath();
    }

    private static List<String> identifiants(JsonPath veille) {
        return veille.getList("elements.identifiant");
    }

    /**
     * PRF-02 : un utilisateur developpeur + expert-metier reçoit l'union des deux veilles, chaque élément une
     * seule fois, avec les rubriques du profil dont il relève et ses mentions obligatoires.
     */
    @Test
    void prf02UnionDesDeuxProfilsSansDoublon() {
        activer("jules", "developpeur", "expert-metier");

        JsonPath veille = veille("jules");
        assertEquals(List.of("developpeur", "expert-metier"), veille.getList("profilsActifs"));
        List<String> ids = identifiants(veille);
        assertEquals(new HashSet<>(ids).size(), ids.size(), "aucun doublon : " + ids);
        assertEquals(List.of(VALIDEE, AVIS), ids, "du plus récent au plus ancien : fiche publiée le 2026-11-02, avis"
                + " publié le 2026-01-01 ; la fiche en attente n'est pas restituée");

        assertTrue(identifiants(veille).containsAll(List.of(VALIDEE, AVIS)), veille.prettify());
        String avis = "elements.find { it.identifiant == '" + AVIS + "' }.";
        assertEquals("AVIS_SECURITE", veille.getString(avis + "type"));
        assertEquals(List.of("developpeur"), veille.getList(avis + "profils"));
        assertEquals(List.of("securite"), veille.getList(avis + "rubriques.identifiant"));
        assertEquals(List.of(projet.toString()), veille.getList(avis + "projets.projetId"));
        assertTrue(veille.getList(avis + "gardeFous", String.class).stream().anyMatch(gardeFou -> gardeFou.contains("impact")));

        String fiche = "elements.find { it.identifiant == '" + VALIDEE + "' }.";
        assertEquals("FICHE_REGLEMENTAIRE", veille.getString(fiche + "type"));
        assertEquals(List.of("expert-metier"), veille.getList(fiche + "profils"));
        assertEquals(List.of("reglementation"), veille.getList(fiche + "rubriques.identifiant"));
        // ADR-0023 et Licence Ouverte : mention de paternité de la source ; résumé relu, sans mention IA.
        assertTrue(veille.getString(fiche + "mentionSource").startsWith(FicheAnalyse.SOURCE), veille.prettify());
        assertNull(veille.getString(fiche + "mentionIa"), veille.prettify());
        assertEquals("Réglementation", veille.getString(fiche + "rubriques[0].libelle"));
        assertEquals(FicheAnalyse.MENTION_VALIDEE, veille.getString(fiche + "mentionValidation"));
        assertEquals("2027-01-01", veille.getString(fiche + "dateEffet"));
        assertTrue(veille.getString(fiche + "lien").startsWith("http"));
        assertTrue(veille.getList(fiche + "gardeFous", String.class).stream().anyMatch(gardeFou -> gardeFou.contains("avis juridique")));
    }

    /** PRF-02-S2 : avec le seul profil developpeur, la fiche qui ne relève que d'expert-metier n'est pas restituée. */
    @Test
    void prf02S2ElementDUnProfilInactif() {
        activer("jules", "developpeur");
        assertEquals(List.of(AVIS), identifiants(veille("jules")));

        activer("jules", "expert-metier");
        assertEquals(List.of(VALIDEE), identifiants(veille("jules")));
    }

    /**
     * PRF-06-S1 : une fiche non validée n'est restituée ni dans la veille ni par le serveur MCP, à un
     * développeur comme à l'expert du domaine, qui la voit pourtant dans la file de relecture.
     */
    @Test
    void prf06S1FicheNonValideeNiVeilleNiMcp() {
        activer("jules", "developpeur", "expert-metier");
        activer("oscar", "developpeur", "expert-metier");
        assertEquals(1, Api.en("oscar").queryParam("etat", "EN_ATTENTE_VALIDATION")
                .when().get("/api/v1/reglementaire/fiches").then().statusCode(200)
                .extract().jsonPath().getList("$").size(), "l'expert voit la fiche en attente dans la file de relecture");

        for (String utilisateur : List.of("jules", "oscar")) {
            assertFalse(identifiants(veille(utilisateur)).contains(EN_ATTENTE), utilisateur + " : veille");
            ExtractableResponse<Response> mcp = Mcp.appeler(utilisateur, "get_regulatory_changes", Map.of());
            assertEquals(Boolean.FALSE, mcp.path("result.isError"), mcp.asString());
            assertFalse(mcp.asString().contains(EN_ATTENTE), utilisateur + " : MCP");
            assertTrue(mcp.asString().contains(VALIDEE), utilisateur + " : MCP");
        }
    }

    /**
     * ADR-0023 : une fiche validée dont le résumé vient encore du modèle porte la mention « produit par l'IA »
     * dans la veille ; un avis de sécurité, sans résumé, n'en porte pas.
     */
    @Test
    void ficheValideeAuResumeIaPorteLaMentionIa() {
        reg.origineResume(ficheValidee, "IA");
        activer("jules", "developpeur", "expert-metier");

        JsonPath veille = veille("jules");
        String fiche = "elements.find { it.identifiant == '" + VALIDEE + "' }.";
        assertEquals(ReponseIa.MENTION, veille.getString(fiche + "mentionIa"), veille.prettify());
        assertTrue(veille.getString(fiche + "mentionSource").startsWith(FicheAnalyse.SOURCE), veille.prettify());
        String avis = "elements.find { it.identifiant == '" + AVIS + "' }.";
        assertNull(veille.getString(avis + "mentionIa"), veille.prettify());
        assertNull(veille.getString(avis + "mentionSource"), veille.prettify());
    }

    /**
     * PRF-06-S2 : validée par l'expert référent, la fiche est restituée avec la mention de sa validation, dans
     * la veille d'un utilisateur dont un profil actif couvre ce domaine et par le serveur MCP.
     */
    @Test
    void prf06S2FicheValidee() {
        Api.en("oscar").when().post("/api/v1/reglementaire/fiches/" + ficheEnAttente + "/validation")
                .then().statusCode(200);
        activer("jules", "expert-metier");

        JsonPath veille = veille("jules");
        String fiche = "elements.find { it.identifiant == '" + EN_ATTENTE + "' }.";
        assertEquals(FicheAnalyse.MENTION_VALIDEE, veille.getString(fiche + "mentionValidation"));
        assertEquals(List.of("reglementation"), veille.getList(fiche + "rubriques.identifiant"));

        ExtractableResponse<Response> mcp = Mcp.appeler("jules", "get_regulatory_changes", Map.of());
        List<Map<String, Object>> fiches = mcp.path("result.structuredContent.fiches");
        Map<String, Object> validee = fiches.stream().filter(candidate -> EN_ATTENTE.equals(candidate.get("referenceOfficielle")))
                .findFirst().orElseThrow(() -> new AssertionError(mcp.asString()));
        assertEquals(FicheAnalyse.MENTION_VALIDEE, validee.get("mentionValidation"));

        activer("jules", "developpeur");
        assertFalse(identifiants(veille("jules")).contains(EN_ATTENTE), "PRF-02-S2 : profil inactif");
    }

    /** Contrat MCP : le schéma de sortie de get_regulatory_changes publie la mention de validation. */
    @Test
    void contratMcpMentionDeValidation() {
        List<Map<String, Object>> outils = Mcp.lister("jules").path("result.tools");
        Map<String, Object> outil = outils.stream().filter(candidat -> "get_regulatory_changes".equals(candidat.get("name")))
                .findFirst().orElseThrow();
        assertTrue(String.valueOf(outil.get("outputSchema")).contains("mentionValidation"), String.valueOf(outil));
    }

    /** Sans compte authentifié, la veille est refusée. */
    @Test
    void veilleSansJeton() {
        io.restassured.RestAssured.given().when().get("/api/v1/me/veille").then().statusCode(401);
    }
}
