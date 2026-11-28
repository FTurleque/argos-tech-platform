package io.argos.api.inventaire;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.InventoryService;
import io.argos.inventory.StatutImport;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.TestDb;

/**
 * Administration des projets et de leurs dépôts par l'API (INV-12 ; décision du porteur du projet, 2026-11-26) :
 * création, rattachement, détachement réservés à l'administrateur de l'espace ; refus journalisés. Les saisies invalides
 * (INV-12-S6) sont vérifiées par {@code SaisieProjetTest}.
 */
@QuarkusTest
class AdministrationProjetsTest {

    private static final String PROJETS = "/api/v1/projects";

    @Inject
    TestDb baseDeTest;

    @Inject
    InventoryService inventaire;

    private UUID espace;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Équipe A");
        Api.assurerCompte("jules");
    }

    private static Map<String, Object> depot(String forge, String identifiant) {
        return Map.of("forge", forge, "depot", identifiant, "branche", "main");
    }

    private String creer(String utilisateur, Map<String, Object> corps, int statut) {
        return Api.enTantQue(utilisateur).contentType(ContentType.JSON).body(corps).when().post(PROJETS)
                .then().statusCode(statut).extract().path("id");
    }

    private String creerPortail() {
        return creer("alice", Map.of("espaceId", espace.toString(), "nom", " portail ", "depot", depot("github", "org/portail")), 201);
    }

    @Test
    void inv12S1CreationDUnProjetAvecSonPremierDepot() {
        String projet = creerPortail();

        Api.enTantQue("alice").when().get(PROJETS).then().statusCode(200)
                .body("items.id", contains(projet)).body("items.name", contains("portail"));
        Api.enTantQue("alice").when().get(PROJETS + "/" + projet + "/inventory/summary").then().statusCode(200)
                .body("status", equalTo("NO_SBOM"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_repository WHERE project_id = ? AND provider = 'GITHUB'"
                + " AND repository = 'org/portail' AND branch = 'main' AND principal", UUID.fromString(projet)));
    }

    @Test
    void creationSansDepot() {
        Api.enTantQue("alice").contentType(ContentType.JSON).body(Map.of("espaceId", espace.toString(), "nom", "api"))
                .when().post(PROJETS).then().statusCode(201)
                .body("nom", equalTo("api")).body("espaceId", equalTo(espace.toString()));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM project_repository"));
    }

    @Test
    void inv12S2RattachementDUnSecondDepot() {
        String projet = creerPortail();

        Api.enTantQue("alice").contentType(ContentType.JSON).body(depot("GITLAB", "groupe/api"))
                .when().post(PROJETS + "/" + projet + "/repositories")
                .then().statusCode(201).body("principal", equalTo(false));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM project_repository WHERE project_id = ?", UUID.fromString(projet)));

        Api.enTantQue("alice").contentType(ContentType.JSON).body(depot("GITLAB", "groupe/api"))
                .when().post(PROJETS + "/" + projet + "/repositories")
                .then().statusCode(409).body("code", equalTo("DEPOT_DEJA_RATTACHE"));
    }

    @Test
    void inv12S3DetachementDUnDepot() {
        UUID projet = UUID.fromString(creerPortail());
        String second = Api.enTantQue("alice").contentType(ContentType.JSON).body(depot("GITLAB", "groupe/api"))
                .when().post(PROJETS + "/" + projet + "/repositories").then().statusCode(201).extract().path("id");
        String sbom = Fixtures.cyclonedx("1.6", "2026-11-18T00:00:00Z", List.<String[]>of(new String[] {"left-pad",
                "pkg:npm/left-pad@1.3.0"}), List.of("left-pad"));
        assertEquals(StatutImport.IMPORTED, inventaire.importSbom(projet, sbom.getBytes(StandardCharsets.UTF_8), "ci").status());

        Api.enTantQue("alice").when().delete(PROJETS + "/" + projet + "/repositories/" + second).then().statusCode(204);
        Api.enTantQue("alice").when().get(PROJETS + "/" + projet + "/inventory/summary").then().statusCode(200)
                .body("summary.repositories", hasSize(1));
        Api.enTantQue("alice").when().delete(PROJETS + "/" + projet + "/repositories/" + second).then().statusCode(404);
    }

    @Test
    void inv12S4RoleInsuffisant() {
        Api.ajouterMembre("alice", espace.toString(), "hugo", "CONTRIBUTEUR");
        String projet = creerPortail();

        creer("hugo", Map.of("espaceId", espace.toString(), "nom", "intrus"), 403);
        Api.enTantQue("hugo").contentType(ContentType.JSON).body(depot("GITHUB", "org/autre"))
                .when().post(PROJETS + "/" + projet + "/repositories").then().statusCode(403);

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_repository"));
        assertEquals(2, refus("hugo", "ROLE_INSUFFISANT"));
    }

    @Test
    void inv12S5EspaceEtranger() {
        String projet = creerPortail();

        creer("jules", Map.of("espaceId", espace.toString(), "nom", "intrus"), 404);
        Api.enTantQue("jules").contentType(ContentType.JSON).body(depot("GITHUB", "org/autre"))
                .when().post(PROJETS + "/" + projet + "/repositories").then().statusCode(404);
        Api.enTantQue("jules").when().delete(PROJETS + "/" + projet + "/repositories/" + UUID.randomUUID()).then().statusCode(404);

        assertEquals(3, refus("jules", "NON_MEMBRE"));
        given().contentType(ContentType.JSON).body(Map.of("espaceId", espace.toString(), "nom", "x"))
                .when().post(PROJETS).then().statusCode(401);
    }

    /** Refus journalisés pour le compte de l'utilisateur, avec le motif donné. */
    private long refus(String utilisateur, String motif) {
        return baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = ? AND motif = ?", Api.compte(utilisateur), motif);
    }
}
