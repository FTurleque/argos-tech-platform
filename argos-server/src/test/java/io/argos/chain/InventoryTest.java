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

import io.argos.inventory.StatutImport;
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
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    RepositoryFiles repositoryFiles;

    private UUID workspace;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        workspace = Api.nouvelEspace("alice", "Équipe A");
    }

    private static byte[] bytes(String texte) {
        return texte.getBytes(StandardCharsets.UTF_8);
    }

    private io.restassured.specification.RequestSpecification member() {
        return Api.enTantQue("alice");
    }

    @Test
    void migrationIdentiteStableDesComposants() {
        UUID project = projects.create(workspace, "api", null, null);
        String sbom = Fixtures.cyclonedx("1.5", "2026-11-10T00:00:00Z",
                java.util.Collections.singletonList(new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"}), List.of("a"));
        inventories.importSbom(project, bytes(sbom), "test");
        String id = baseDeTest.string("SELECT id::text FROM component");
        inventories.importSbom(project, bytes(sbom), "test");
        assertEquals(id, baseDeTest.string("SELECT id::text FROM component"), "un composant inchangé garde son identité");
        IllegalStateException erreur = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> baseDeTest.exec(
                        "INSERT INTO component(id, project_id, package_key, purl, version, import_id) SELECT gen_random_uuid(), "
                        + "project_id, package_key, purl, version, import_id FROM component"));
        assertTrue(erreur.getCause().getMessage().contains("component_identity"));
    }

    @Test
    void inv01S1EtInv02S1ImportSbomValideAvecTransitive() {
        UUID project = projects.create(workspace, "api", null, null);
        String sbom = Fixtures.cyclonedx("1.6", "2026-11-10T08:00:00Z", List.of(
                new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"},
                new String[] {"b", "pkg:maven/org.exemple/b@2.1.0"}), List.of("a"));

        InventoryService.ImportResult result = inventories.importSbom(project, bytes(sbom), "ci");

        assertEquals(StatutImport.IMPORTED, result.status());
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200)
                .body("status", equalTo("AVAILABLE"))
                .body("inventory.origin", equalTo("SBOM"))
                .body("inventory.sourceDate", equalTo("2026-11-10T08:00:00Z"))
                .body("inventory.specVersion", equalTo("1.6"))
                .body("inventory.components.items", hasSize(2))
                .body("inventory.components.items.find { it.packageKey == 'pkg:maven/org.exemple/a' }.version", equalTo("1.0.0"))
                .body("inventory.components.items.find { it.packageKey == 'pkg:maven/org.exemple/a' }.transitive", equalTo(false))
                .body("inventory.components.items.find { it.packageKey == 'pkg:maven/org.exemple/b' }.purl",
                        equalTo("pkg:maven/org.exemple/b@2.1.0"))
                .body("inventory.components.items.find { it.packageKey == 'pkg:maven/org.exemple/b' }.transitive", equalTo(true));
    }

    /**
     * INV-01, décision D6 (2026-11-18) : les composants écartés faute de PURL sont signalés dans le résultat
     * de l'import et par l'API d'inventaire (nom, groupe, version, motif), liste bornée ; l'inventaire reste
     * fondé sur le PURL, et un nouvel import remplace le signalement.
     */
    @Test
    void inv01S3ComposantsSansPurlSignalesALImport() {
        UUID project = projects.create(workspace, "outil", null, null);
        StringBuilder comps = new StringBuilder(
                "{\"type\":\"library\",\"bom-ref\":\"log4j\",\"group\":\"org.apache.logging.log4j\",\"name\":\"log4j-core\","
                        + "\"version\":\"2.14.1\",\"cpe\":\"cpe:2.3:a:apache:log4j:2.14.1:*:*:*:*:*:*:*\"},"
                        + "{\"type\":\"library\",\"bom-ref\":\"a\",\"name\":\"a\",\"purl\":\"pkg:maven/org.exemple/a@1.0.0\"},"
                        + "{\"type\":\"library\",\"name\":\"illisible\",\"version\":\"3\",\"purl\":\"pas-un-purl\"}");
        for (int rang = 0; rang < 120; rang++) {
            comps.append(",{\"type\":\"library\",\"name\":\"interne-").append(rang).append("\"}");
        }
        String sbom = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"version\":1,"
                + "\"metadata\":{\"timestamp\":\"2026-11-16T00:00:00Z\",\"component\":{\"type\":\"application\",\"bom-ref\":\"root\",\"name\":\"outil\"}},"
                + "\"components\":[" + comps + "],\"dependencies\":[{\"ref\":\"root\",\"dependsOn\":[\"log4j\",\"a\"]}]}";

        InventoryService.ImportResult result = inventories.importSbom(project, bytes(sbom), "ci#1");

        assertEquals(StatutImport.IMPORTED, result.status());
        assertEquals(1, result.components());
        assertEquals(122, result.ignored());
        assertEquals(100, result.ignoredComponents().size());
        assertEquals("log4j-core", result.ignoredComponents().get(0).name());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM component"), "l'inventaire reste fondé sur le PURL");
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200)
                .body("inventory.components.items", hasSize(1))
                .body("inventory.ignoredCount", equalTo(122))
                .body("inventory.ignoredComponents", hasSize(100))
                .body("inventory.ignoredComponents[0].name", equalTo("log4j-core"))
                .body("inventory.ignoredComponents[0].group", equalTo("org.apache.logging.log4j"))
                .body("inventory.ignoredComponents[0].version", equalTo("2.14.1"))
                .body("inventory.ignoredComponents[0].reason", equalTo("SANS_PURL"))
                .body("inventory.ignoredComponents[1].name", equalTo("illisible"))
                .body("inventory.ignoredComponents[1].group", nullValue())
                .body("inventory.ignoredComponents[1].reason", equalTo("PURL_INVALIDE"));

        inventories.importSbom(project, bytes(Fixtures.cyclonedx("1.6", "2026-11-17T00:00:00Z",
                java.util.Collections.singletonList(new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"}), List.of("a"))), "ci#2");
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200)
                .body("inventory.ignoredCount", equalTo(0))
                .body("inventory.ignoredComponents", hasSize(0));
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
                .body("inventory.components.items", hasSize(1))
                .body("inventory.components.items[0].purl", equalTo("pkg:maven/org.exemple/a@1.1.0"))
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
        StubServer.programmer("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/package.json/raw?ref=main",
                Stub.succes("{\"dependencies\":{\"lodash\":\"4.17.20\"}}"));

        InventoryService.ImportResult fallback = inventories.importerProjet(project).depots().get(0).result();

        assertEquals(StatutImport.IMPORTED, fallback.status());
        assertEquals("MANIFEST", fallback.origin());
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200).body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components.items[0].purl", equalTo("pkg:npm/lodash@4.17.20"));

        StubServer.programmer("/gitlab/api/v4/projects/groupe%2Ffront/jobs/artifacts/main/raw/bom.json?job=sbom",
                Stub.succes(Fixtures.cyclonedx("1.6", "2026-11-15T00:00:00Z",
                        java.util.Collections.singletonList(new String[] {"l", "pkg:npm/lodash@4.17.21"}), List.of("l"))));

        InventoryService.ImportResult sbom = inventories.importerProjet(project).depots().get(0).result();

        assertEquals("SBOM", sbom.origin());
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200).body("inventory.origin", equalTo("SBOM"))
                .body("inventory.components.items[0].version", equalTo("4.17.21"));
    }

    @Test
    void inv05S1SbomNonConformeRefuseeEtMotifConsultable() {
        UUID project = projects.create(workspace, "api", null, null);
        inventories.importSbom(project, bytes(Fixtures.cyclonedx("1.5", "2026-11-01T00:00:00Z",
                java.util.Collections.singletonList(new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"}), List.of("a"))), "ci#1");

        InventoryService.ImportResult rejected = inventories.importSbom(project,
                bytes("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.4\",\"components\":[{\"purl\":\"pkg:npm/x@1\"}]}"), "ci#2");

        assertEquals(StatutImport.REJECTED, rejected.status());
        member().when().get("/api/v1/projects/" + project + "/inventory/failures")
                .then().statusCode(200).body("items[0].reason", containsString("1.4")).body("items[0].sourceRef", equalTo("ci#2"));
        member().when().get("/api/v1/projects/" + project + "/inventory")
                .then().statusCode(200).body("inventory.components.items", hasSize(1))
                .body("inventory.components.items[0].packageKey", equalTo("pkg:maven/org.exemple/a"));
    }

    @Test
    void inventaireDUnAutreEspaceRefuseEtJournalise() {
        UUID other = Api.nouvelEspace("jules", "Équipe B");
        UUID project = projects.create(other, "secret", null, null);
        member().when().get("/api/v1/projects/" + project + "/inventory").then().statusCode(404);
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("alice")
                + "' AND ressource = 'GET /api/v1/projects/" + project + "/inventory' AND motif = 'NON_MEMBRE'"));
    }

    @Test
    void contratGitLabEtGitHub() {
        RepositoryRef gitlab = new RepositoryRef("GITLAB", "groupe/api", "main", "cyclonedx", "reports/bom.json");
        StubServer.programmer("/gitlab/api/v4/projects/groupe%2Fapi/jobs/artifacts/main/raw/reports/bom.json?job=cyclonedx",
                Stub.succes("{\"bomFormat\":\"CycloneDX\"}"));
        assertEquals("{\"bomFormat\":\"CycloneDX\"}",
                new String(repositoryFiles.lecture(gitlab).latestSbom().orElseThrow().contenu(), StandardCharsets.UTF_8));
        assertTrue(repositoryFiles.lecture(gitlab).file("package.json").isEmpty(), "404 : fichier absent");

        RepositoryRef github = new RepositoryRef("GITHUB", "org/app", "main", null, "sbom/bom.json");
        StubServer.programmer("/github/repos/org/app/contents/sbom/bom.json?ref=main", Stub.succes("{\"bomFormat\":\"CycloneDX\"}"));
        assertTrue(repositoryFiles.lecture(github).latestSbom().isPresent());
        StubServer.Received request = StubServer.received("/github/repos/org/app/contents/sbom/bom.json").get(0);
        assertEquals(List.of("application/vnd.github.raw+json"), request.headers().get("Accept"));
        Map<String, List<String>> headers = StubServer.received("/gitlab/").get(0).headers();
        assertTrue(headers.keySet().stream().noneMatch(entete -> entete.equalsIgnoreCase("PRIVATE-TOKEN")),
                "aucun jeton configuré, aucun envoyé");
    }
}
