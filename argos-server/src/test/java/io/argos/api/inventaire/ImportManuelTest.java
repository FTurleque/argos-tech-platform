package io.argos.api.inventaire;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;

/**
 * Import manuel par l'API (INV-13 ; décision du porteur du projet, 2026-11-26) : SBOM, manifestes et relance depuis
 * les dépôts, réservés aux contributeurs et administrateurs de l'espace du projet.
 */
@QuarkusTest
class ImportManuelTest {

    private static final String GITHUB_SBOM = "/github/repos/org/app/contents/sbom/bom.json?ref=main";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projets;

    private UUID espace;
    private UUID projet;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Équipe A");
        Api.assurerCompte("jules");
        Api.ajouterMembre("alice", espace.toString(), "hugo", "CONTRIBUTEUR");
        Api.ajouterMembre("alice", espace.toString(), "ines", "LECTEUR");
        projet = projets.create(espace, "app", null, null);
    }

    private static String sbom(String... noms) {
        List<String[]> composants = java.util.Arrays.stream(noms)
                .map(nom -> new String[] {nom, "pkg:npm/" + nom + "@1.0.0"}).toList();
        return Fixtures.cyclonedx("1.6", "2026-11-25T00:00:00Z", composants, List.of(noms[0]));
    }

    private String chemin(String suite) {
        return "/api/v1/projects/" + projet + "/inventory" + suite;
    }

    @Test
    void inv13S1ImportDUneSbomConforme() {
        Api.enTantQue("hugo").contentType(ContentType.JSON).body(sbom("alpha", "beta", "gamma"))
                .when().post(chemin("/sbom"))
                .then().statusCode(200).body("status", equalTo("IMPORTED")).body("origin", equalTo("SBOM"))
                .body("components", equalTo(3));
        Api.enTantQue("ines").when().get(chemin("/components")).then().statusCode(200).body("total", equalTo(3));
    }

    @Test
    void inv13S2SbomNonConforme() {
        Api.enTantQue("alice").contentType(ContentType.JSON).body(sbom("alpha")).when().post(chemin("/sbom"))
                .then().statusCode(200).body("status", equalTo("IMPORTED"));

        Api.enTantQue("hugo").contentType(ContentType.JSON).body("{\"bomFormat\":\"SPDX\"}").when().post(chemin("/sbom"))
                .then().statusCode(200).body("status", equalTo("REJECTED")).body("reason", not(emptyString()));

        Api.enTantQue("hugo").when().get(chemin("/components")).then().statusCode(200).body("total", equalTo(1));
        Api.enTantQue("hugo").when().get(chemin("/failures")).then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    void inv13S3DocumentTropVolumineux() {
        String volumineux = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"bourrage\":\"" + "x".repeat(21_000_000) + "\"}";
        Api.enTantQue("hugo").contentType(ContentType.JSON).body(volumineux).when().post(chemin("/sbom"))
                .then().statusCode(200).body("status", equalTo("REJECTED"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM inventory WHERE project_id = ?", projet));
    }

    @Test
    void inv13S4ImportDeManifestes() {
        String paquet = "{\"dependencies\":{\"express\":\"4.18.2\"}}";
        Api.enTantQue("hugo").contentType(ContentType.JSON)
                .body(Map.of("manifestes", List.of(Map.of("chemin", "package.json", "contenu", paquet))))
                .when().post(chemin("/manifests"))
                .then().statusCode(200).body("status", equalTo("IMPORTED")).body("origin", equalTo("MANIFEST"));

        Api.enTantQue("hugo").contentType(ContentType.JSON).body(Map.of("manifestes", List.of()))
                .when().post(chemin("/manifests"))
                .then().statusCode(400).body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo("manifestes"));
    }

    @Test
    void inv13S5RelanceDepuisLesDepots() {
        Api.enTantQue("hugo").when().post(chemin("/refresh")).then().statusCode(409).body("code", equalTo("PROJET_SANS_DEPOT"));

        projets.ajouterDepot(projet, new RepositoryRef("GITHUB", "org/app", "main", null, "sbom/bom.json"), null);
        StubServer.programmer(GITHUB_SBOM, Stub.succes(sbom("alpha", "beta")));

        Api.enTantQue("hugo").when().post(chemin("/refresh"))
                .then().statusCode(200)
                .body("depots", hasSize(1)).body("depots[0].repository", equalTo("org/app"))
                .body("depots[0].result.status", equalTo("IMPORTED"));
        Api.enTantQue("hugo").when().get(chemin("/components")).then().statusCode(200).body("total", equalTo(2));
    }

    @Test
    void inv13S6Lecteur() {
        Api.enTantQue("ines").contentType(ContentType.JSON).body(sbom("alpha")).when().post(chemin("/sbom")).then().statusCode(403);
        Api.enTantQue("ines").when().post(chemin("/refresh")).then().statusCode(403);
        Api.enTantQue("jules").when().post(chemin("/refresh")).then().statusCode(404);
        Api.enTantQue("hugo").when().post("/api/v1/projects/" + UUID.randomUUID() + "/inventory/refresh").then().statusCode(404);

        assertEquals(2, refus("ines", "ROLE_INSUFFISANT"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM inventory"));
    }

    /** Refus journalisés pour le compte de l'utilisateur, avec le motif donné. */
    private long refus(String utilisateur, String motif) {
        return baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = ? AND motif = ?", Api.compte(utilisateur), motif);
    }
}
