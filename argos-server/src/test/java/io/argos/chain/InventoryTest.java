package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryFiles;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Inventaire SBOM (tâches 3.1 à 3.4) : import CycloneDX, transitivité, remplacement, âge,
 * absence de SBOM, repli sur les manifestes, rejet motivé ; récupération GitLab / GitHub simulée.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class InventoryTest {

    @Inject
    TestDb db;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    RepositoryFiles repositoryFiles;

    private UUID workspace;

    @BeforeEach
    void reset() {
        db.reset();
        workspace = Api.nouvelEspace("alice", "Équipe A");
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private io.restassured.specification.RequestSpecification member() {
        return Api.en("alice");
    }

    @Test
    void migrationIdentiteStableDesComposants() {
        UUID project = projects.create(workspace, "api", null, null);
        String sbom = Fixtures.cyclonedx("1.5", "2026-11-10T00:00:00Z",
                java.util.Collections.singletonList(new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"}), List.of("a"));
        inventories.importSbom(project, bytes(sbom), "test");
        String id = db.string("SELECT id::text FROM component");
        inventories.importSbom(project, bytes(sbom), "test");
        assertEquals(id, db.string("SELECT id::text FROM component"), "un composant inchangé garde son identité");
        IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> db.exec("INSERT INTO component(id, project_id, package_key, purl, version, import_id) SELECT gen_random_uuid(), "
                        + "project_id, package_key, purl, version, import_id FROM component"));
        assertTrue(e.getCause().getMessage().contains("component_identity"));
    }

    @Test
    void inv01S1EtInv02S1ImportSbomValideAvecTransitive() {
        UUID project = projects.create(workspace, "api", null, null);
        String sbom = Fixtures.cyclonedx("1.6", "2026-11-10T08:00:00Z", List.of(
                new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"},
                new String[] {"b", "pkg:maven/org.exemple/b@2.1.0"}), List.of("a"));

        InventoryService.ImportResult result = inventories.importSbom(project, bytes(sbom), "ci");

        assertEquals("IMPORTED", result.status());
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200)
                .body("status", equalTo("AVAILABLE"))
                .body("inventory.origin", equalTo("SBOM"))
                .body("inventory.sourceDate", equalTo("2026-11-10T08:00:00Z"))
                .body("inventory.specVersion", equalTo("1.6"))
                .body("inventory.components", hasSize(2))
                .body("inventory.components.find { it.packageKey == 'pkg:maven/org.exemple/a' }.version", equalTo("1.0.0"))
                .body("inventory.components.find { it.packageKey == 'pkg:maven/org.exemple/a' }.transitive", equalTo(false))
                .body("inventory.components.find { it.packageKey == 'pkg:maven/org.exemple/b' }.purl",
                        equalTo("pkg:maven/org.exemple/b@2.1.0"))
                .body("inventory.components.find { it.packageKey == 'pkg:maven/org.exemple/b' }.transitive", equalTo(true));
    }

    @Test
    void inv01S2NouvelleSbomRemplaceLaPrecedente() {
        UUID project = projects.create(workspace, "api", null, null);
        inventories.importSbom(project, bytes(Fixtures.cyclonedx("1.5", "2026-11-01T00:00:00Z", List.of(
                new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"},
                new String[] {"old", "pkg:maven/org.exemple/old@1.0.0"}), List.of("a", "old"))), "ci#1");

        inventories.importSbom(project, bytes(Fixtures.cyclonedx("1.5", "2026-11-10T00:00:00Z", java.util.Collections.singletonList(
                new String[] {"a", "pkg:maven/org.exemple/a@1.1.0"}), List.of("a"))), "ci#2");

        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200)
                .body("inventory.components", hasSize(1))
                .body("inventory.components[0].purl", equalTo("pkg:maven/org.exemple/a@1.1.0"))
                .body("inventory.sourceDate", equalTo("2026-11-10T00:00:00Z"));
    }

    @Test
    void inv03S1EtS2AgeDeLaSbomEtProjetSansSbom() {
        UUID withSbom = projects.create(workspace, "avec", null, null);
        Instant generated = Instant.now().minus(12, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        inventories.importSbom(withSbom, bytes(Fixtures.cyclonedx("1.5", generated.toString(),
                java.util.Collections.singletonList(new String[] {"a", "pkg:npm/lodash@4.17.20"}), List.of("a"))), "ci");
        UUID withoutSbom = projects.create(workspace, "sans", null, null);

        member().when().get("/api/v1/projects/" + withSbom + "/inventory")
                .then().statusCode(200).body("inventory.ageDays", equalTo(12)).body("inventory.obsolete", equalTo(false));
        member().when().get("/api/v1/projects/" + withoutSbom + "/inventory")
                .then().statusCode(200).body("status", equalTo("NO_SBOM")).body("inventory", nullValue());
    }

    @Test
    void sbomDePlusDeTrenteJoursObsolete() {
        UUID project = projects.create(workspace, "ancien", null, null);
        inventories.importSbom(project, bytes(Fixtures.cyclonedx("1.5", Instant.now().minus(31, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"a", "pkg:npm/lodash@4.17.20"}), List.of("a"))), "ci");
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200).body("inventory.obsolete", equalTo(true)).body("inventory.ageDays", equalTo(31));
    }

    @Test
    void inv04S1EtS2RepliSurLesManifestesPuisSbom() {
        RepositoryRef repo = new RepositoryRef("GITLAB", "groupe/front", "main", "sbom", "bom.json");
        UUID project = projects.create(workspace, "front", repo, List.of("package.json", "requirements.txt"));
        StubServer.on("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/package.json/raw?ref=main",
                Stub.ok("{\"dependencies\":{\"lodash\":\"4.17.20\"}}"));

        InventoryService.ImportResult fallback = inventories.refresh(project);

        assertEquals("IMPORTED", fallback.status());
        assertEquals("MANIFEST", fallback.origin());
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200).body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components[0].purl", equalTo("pkg:npm/lodash@4.17.20"));

        StubServer.on("/gitlab/api/v4/projects/groupe%2Ffront/jobs/artifacts/main/raw/bom.json?job=sbom",
                Stub.ok(Fixtures.cyclonedx("1.6", "2026-11-15T00:00:00Z",
                        java.util.Collections.singletonList(new String[] {"l", "pkg:npm/lodash@4.17.21"}), List.of("l"))));

        InventoryService.ImportResult sbom = inventories.refresh(project);

        assertEquals("SBOM", sbom.origin());
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200).body("inventory.origin", equalTo("SBOM"))
                .body("inventory.components[0].version", equalTo("4.17.21"));
    }

    @Test
    void inv05S1SbomNonConformeRefuseeEtMotifConsultable() {
        UUID project = projects.create(workspace, "api", null, null);
        inventories.importSbom(project, bytes(Fixtures.cyclonedx("1.5", "2026-11-01T00:00:00Z",
                java.util.Collections.singletonList(new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"}), List.of("a"))), "ci#1");

        InventoryService.ImportResult rejected = inventories.importSbom(project,
                bytes("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.4\",\"components\":[{\"purl\":\"pkg:npm/x@1\"}]}"), "ci#2");

        assertEquals("REJECTED", rejected.status());
        member().when().get("/api/v1/projects/" + project + "/inventory/failures")
                .then().statusCode(200).body("[0].reason", containsString("1.4")).body("[0].sourceRef", equalTo("ci#2"));
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200).body("inventory.components", hasSize(1))
                .body("inventory.components[0].packageKey", equalTo("pkg:maven/org.exemple/a"));
    }

    @Test
    void inventaireDUnAutreEspaceRefuseEtJournalise() {
        UUID other = Api.nouvelEspace("jules", "Équipe B");
        UUID project = projects.create(other, "secret", null, null);
        member().when().get("/api/v1/projects/" + project + "/inventory").then().statusCode(404);
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("alice")
                + "' AND ressource = 'GET /api/v1/projects/" + project + "/inventory' AND motif = 'NON_MEMBRE'"));
    }

    @Test
    void contratGitLabEtGitHub() {
        RepositoryRef gitlab = new RepositoryRef("GITLAB", "groupe/api", "main", "cyclonedx", "reports/bom.json");
        StubServer.on("/gitlab/api/v4/projects/groupe%2Fapi/jobs/artifacts/main/raw/reports/bom.json?job=cyclonedx",
                Stub.ok("{\"bomFormat\":\"CycloneDX\"}"));
        assertEquals("{\"bomFormat\":\"CycloneDX\"}",
                new String(repositoryFiles.latestSbom(gitlab).orElseThrow(), StandardCharsets.UTF_8));
        assertTrue(repositoryFiles.file(gitlab, "package.json").isEmpty(), "404 : fichier absent");

        RepositoryRef github = new RepositoryRef("GITHUB", "org/app", "main", null, "sbom/bom.json");
        StubServer.on("/github/repos/org/app/contents/sbom/bom.json?ref=main", Stub.ok("{\"bomFormat\":\"CycloneDX\"}"));
        assertTrue(repositoryFiles.latestSbom(github).isPresent());
        StubServer.Received request = StubServer.received("/github/repos/org/app/contents/sbom/bom.json").get(0);
        assertEquals(List.of("application/vnd.github.raw+json"), request.headers().get("Accept"));
        Map<String, List<String>> headers = StubServer.received("/gitlab/").get(0).headers();
        assertTrue(headers.keySet().stream().noneMatch(h -> h.equalsIgnoreCase("PRIVATE-TOKEN")), "aucun jeton configuré, aucun envoyé");
    }
}
