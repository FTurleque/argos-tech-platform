package io.argos.api.veille;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Sources des abonnements {@code PERSONAL} ajoutées à la veille par profils ({@code GET /api/v1/me/veille},
 * décision Q-3 de {@code profils-veille}, tâche 3.9, PRF-02, ABO-04, ABO-05, PRF-05). Jetons réels, lectures
 * sous la RLS du rôle {@code argos_app}, deux espaces : l'espace A (ines, jules) et l'espace B (karim).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class VeilleAbonnementsPersonnelsTest {

    private static final String LIB = "pkg:maven/org.exemple/abonnement";
    /** Avis qui impacte le projet de l'espace A. */
    private static final String AVIS_A = "GHSA-ABO-A";
    /** Avis qui impacte le projet de l'espace B. */
    private static final String AVIS_B = "GHSA-ABO-B";
    private static final String VALIDEE = "JORFTEXT000097200001";

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

    private UUID espaceA;
    private UUID projetA;
    private UUID projetB;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        for (String utilisateur : List.of("ines", "jules", "karim")) {
            baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = '" + Api.compte(utilisateur) + "'");
        }
        espaceA = Api.nouvelEspace("ines", "Espace A");
        Api.ajouterMembre("ines", espaceA.toString(), "jules", "LECTEUR");
        UUID espaceB = Api.nouvelEspace("karim", "Espace B");

        projetA = projects.create(espaceA, "paie-app", null, null);
        importer(projetA, LIB + "@1.2.0");
        projetB = projects.create(espaceB, "rh-app", null, null);
        importer(projetB, "pkg:cargo/hyper@0.14.0");
        Fixtures.stubOsv(
                AVIS_A, "2026-11-10T00:00:00Z",
                Fixtures.osv(AVIS_A, "2026-11-10T00:00:00Z", "Maven", "org.exemple:abonnement", LIB, "0", "1.3.0"),
                AVIS_B, "2026-11-09T00:00:00Z",
                Fixtures.osv(AVIS_B, "2026-11-09T00:00:00Z", "crates.io", "hyper", "pkg:cargo/hyper", "0", "9.9.9"));
        synchronizer.synchronize("OSV");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_impact WHERE project_id = '" + projetA + "'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_impact WHERE project_id = '" + projetB + "'"));

        UUID ines = Api.compte("ines");
        reg.domaine(espaceA, "paie", "Paie", ines);
        reg.expert(espaceA, "paie", ines);
        reg.fiche(reg.texte(VALIDEE, "Décret relatif à la paie", LocalDate.of(2026, 11, 2), LocalDate.of(2027, 1, 1)),
                espaceA, "paie", "VALIDEE", "Résumé relu par l'expert.", ines);
    }

    private void importer(UUID projet, String purl) {
        inventories.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"c", purl}), List.of("c"))
                .getBytes(StandardCharsets.UTF_8), "ci");
    }

    private static void activer(String utilisateur, String... profils) {
        Api.en(utilisateur).contentType(ContentType.JSON)
                .body(Map.of("profilsActifs", List.of(profils)))
                .when().put("/api/v1/preferences/profils").then().statusCode(200);
    }

    private static String abonnement(String utilisateur, String scope, UUID espace, String type, String valeur) {
        String equipe = espace == null ? "" : ",\"teamWorkspaceId\":\"" + espace + "\"";
        return Api.en(utilisateur).contentType(ContentType.JSON)
                .body("{\"scope\":\"" + scope + "\"" + equipe + ",\"filters\":[{\"type\":\"" + type + "\",\"value\":\""
                        + valeur + "\"}],\"channel\":\"in_app\",\"frequency\":\"daily\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");
    }

    private static String personnel(String utilisateur, String type, String valeur) {
        return abonnement(utilisateur, "PERSONAL", null, type, valeur);
    }

    private static JsonPath veille(String utilisateur) {
        return Api.en(utilisateur).when().get("/api/v1/me/veille").then().statusCode(200).extract().jsonPath();
    }

    private static List<String> identifiants(JsonPath veille) {
        return veille.getList("elements.identifiant");
    }

    private static String element(String identifiant) {
        return "elements.find { it.identifiant == '" + identifiant + "' }.";
    }

    /**
     * PRF-02-S3 (Q-3) : un abonnement PERSONAL vise une source hors des profils actifs ; elle s'ajoute à
     * l'union, sans rubrique de profil, avec l'abonnement qui l'a retenue. Les éléments des profils gardent
     * leurs rubriques.
     */
    @Test
    void prf02S3SourceDUnAbonnementPersonnelHorsDesProfils() {
        activer("jules", "expert-metier");
        assertEquals(List.of(VALIDEE), identifiants(veille("jules")), "sans abonnement : la seule veille du profil");

        String rust = personnel("jules", "TECHNOLOGY", "rust");
        JsonPath veille = veille("jules");
        assertEquals(List.of("expert-metier"), veille.getList("profilsActifs"));
        assertEquals(List.of(VALIDEE, AVIS_B), identifiants(veille), "fiche publiée le 2026-11-02, avis le 2026-01-01");

        assertEquals("AVIS_SECURITE", veille.getString(element(AVIS_B) + "type"));
        assertEquals(List.of(), veille.getList(element(AVIS_B) + "profils"));
        assertEquals(List.of(), veille.getList(element(AVIS_B) + "rubriques"));
        assertEquals(List.of(rust), veille.getList(element(AVIS_B) + "abonnements"));
        assertEquals(List.of(), veille.getList(element(AVIS_B) + "projets"),
                "le projet impacté appartient à l'espace B, hors des espaces de jules");

        assertEquals(List.of("reglementation"), veille.getList(element(VALIDEE) + "rubriques.identifiant"));
        assertEquals(List.of(), veille.getList(element(VALIDEE) + "abonnements"));
    }

    /**
     * Dédoublonnage : un avis retenu par le profil developpeur et par deux abonnements PERSONAL est restitué une
     * fois, avec sa rubrique de profil, ses projets et les deux abonnements.
     */
    @Test
    void q3UnionSansDoublonProfilEtAbonnements() {
        activer("jules", "developpeur");
        String parPaquet = personnel("jules", "PACKAGE", LIB);
        String parTechnologie = personnel("jules", "TECHNOLOGY", "java");

        JsonPath veille = veille("jules");
        List<String> ids = identifiants(veille);
        assertEquals(new HashSet<>(ids).size(), ids.size(), "aucun doublon : " + ids);
        assertEquals(List.of(AVIS_A), ids);
        assertEquals(List.of("developpeur"), veille.getList(element(AVIS_A) + "profils"));
        assertEquals(List.of("securite"), veille.getList(element(AVIS_A) + "rubriques.identifiant"));
        assertEquals(List.of(projetA.toString()), veille.getList(element(AVIS_A) + "projets.projetId"));
        assertEquals(List.of(parPaquet, parTechnologie).stream().sorted().toList(),
                veille.getList(element(AVIS_A) + "abonnements"));
        assertTrue(veille.getList(element(AVIS_A) + "gardeFous", String.class).stream().anyMatch(gardeFou -> gardeFou.contains("impact")));
    }

    /**
     * PRF-02-S4, inter-espaces (ABO-05, PRF-05) : un abonnement n'élargit jamais les données visibles. Les
     * projets concernés restent limités aux espaces de l'appelant, un critère PROJECT hors de ses espaces ne
     * retient rien, et l'abonnement PERSONAL d'un autre utilisateur ne s'applique pas.
     */
    @Test
    void prf02S4AbonnementPersonnelDansLaLimiteDesDroits() {
        activer("karim", "expert-metier");
        personnel("karim", "TECHNOLOGY", "rust");
        JsonPath deKarim = veille("karim");
        assertEquals(List.of(AVIS_B), identifiants(deKarim), "la fiche de l'espace A n'est pas visible de karim");
        assertEquals(List.of(projetB.toString()), deKarim.getList(element(AVIS_B) + "projets.projetId"));

        activer("jules", "expert-metier");
        assertEquals(List.of(VALIDEE), identifiants(veille("jules")), "l'abonnement de karim ne s'applique pas à jules");

        personnel("jules", "PROJECT", projetB.toString());
        String brut = Api.en("jules").when().get("/api/v1/me/veille").then().statusCode(200).extract().asString();
        assertEquals(List.of(VALIDEE), identifiants(veille("jules")), "projet de l'espace B : rien n'est retenu");
        assertFalse(brut.contains(projetB.toString()), brut);
        assertFalse(brut.contains("rh-app"), brut);

        personnel("jules", "PROJECT", projetA.toString());
        JsonPath veille = veille("jules");
        assertEquals(List.of(VALIDEE, AVIS_A), identifiants(veille), "projet de l'espace A : son avis est retenu");
        assertEquals(List.of(projetA.toString()), veille.getList(element(AVIS_A) + "projets.projetId"));
    }

    /**
     * Seuls les abonnements PERSONAL actifs de l'appelant s'ajoutent : le socle TEAM reste restitué par
     * {@code GET /api/v1/me/watch}, et un abonnement PERSONAL quitté cesse de s'appliquer.
     */
    @Test
    void q3SeulsLesAbonnementsPersonnelsActifs() {
        activer("jules", "expert-metier");
        abonnement("ines", "TEAM", espaceA, "TECHNOLOGY", "rust");
        assertEquals(List.of(VALIDEE), identifiants(veille("jules")));
        assertTrue(Api.en("jules").when().get("/api/v1/me/watch").then().statusCode(200).extract().jsonPath()
                .getList("externalId").contains(AVIS_B), "le socle TEAM figure dans /me/watch");

        String rust = personnel("jules", "TECHNOLOGY", "rust");
        assertTrue(identifiants(veille("jules")).contains(AVIS_B));
        Api.en("jules").when().post("/api/v1/subscriptions/" + rust + "/leave").then().statusCode(204);
        assertEquals(List.of(VALIDEE), identifiants(veille("jules")));
    }
}
