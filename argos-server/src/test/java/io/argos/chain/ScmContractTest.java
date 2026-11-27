package io.argos.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.StatutImport;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Contrat de {@code scm-connector} dans la chaîne d'inventaire (tâche 3.2, INV-01, INV-04) : import depuis GitLab et
 * GitHub simulés par le serveur HTTP local avec les réponses enregistrées de {@code fixtures/scm/}, inventaire
 * enregistré et restitué par l'API. Aucune forge réelle n'est appelée. Le connecteur seul est vérifié sans Quarkus
 * par {@code RepositoryFilesTest}.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ScmContractTest {

    private static final RepositoryRef GITLAB = new RepositoryRef("GITLAB", "groupe/front", "main", "dependency_scanning",
            "gl-sbom-npm-npm.cdx.json");
    private static final RepositoryRef GITHUB = new RepositoryRef("GITHUB", "org/app", "main", null, "sbom/bom.json");
    private static final String GITLAB_SBOM =
            "/gitlab/api/v4/projects/groupe%2Ffront/jobs/artifacts/main/raw/gl-sbom-npm-npm.cdx.json?job=dependency_scanning";
    private static final String GITHUB_SBOM = "/github/repos/org/app/contents/sbom/bom.json?ref=main";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    private static Stub fixture(int status, String path, Map<String, String> headers) {
        return new Stub(status, Fixtures.resource(path), headers);
    }

    @Test
    void inventaireDepuisGitlabAvecTransitivite() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "front", GITLAB, List.of("package.json"));
        StubServer.programmer(GITLAB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of()));

        InventoryService.ImportResult resultat = inventories.importerProjet(projet).depots().get(0).result();

        assertEquals(StatutImport.IMPORTED, resultat.status());
        assertEquals("SBOM", resultat.origin());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("SBOM"))
                .body("inventory.sourceDate", equalTo("2026-11-15T06:12:44Z"))
                .body("inventory.components.items", hasSize(3))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/express' }.transitive", equalTo(false))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/body-parser' }.transitive", equalTo(true));
    }

    @Test
    void sbomSpdxRefuseeAvecMotifEtInventaireConserve() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "app", GITHUB, List.of("requirements.txt"));
        StubServer.programmer(GITHUB_SBOM, fixture(404, "scm/github/404.json", Map.of()));
        StubServer.programmer("/github/repos/org/app", Stub.succes("{\"full_name\":\"org/app\"}"));
        StubServer.programmer("/github/repos/org/app/contents/requirements.txt?ref=main",
                fixture(200, "scm/github/requirements.txt", Map.of()));

        InventoryService.ImportResult repli = inventories.importerProjet(projet).depots().get(0).result();
        assertEquals("MANIFEST", repli.origin());

        StubServer.programmer(GITHUB_SBOM, fixture(200, "scm/github/dependency-graph-sbom.json", Map.of()));
        InventoryService.ImportResult refus = inventories.importerProjet(projet).depots().get(0).result();

        assertEquals(StatutImport.REJECTED, refus.status());
        assertTrue(refus.reason().contains("SPDX JSON"));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory/failures")
                .then().statusCode(200)
                .body("items[0].reason", containsString("SPDX JSON non acceptée"))
                .body("items[0].sourceRef", equalTo("GITHUB:org/app@main"));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components.items", hasSize(2));
    }
}
