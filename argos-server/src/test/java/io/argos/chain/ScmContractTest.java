package io.argos.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.scm.QuotaEpuise;
import io.argos.scm.RepositoryFiles;
import io.argos.scm.RepositoryRef;
import io.argos.scm.SbomDocument;
import io.argos.scm.ScmException;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Contrat de {@code scm-connector} (tâche 3.2, INV-01, INV-04) : récupération des SBOM et des
 * manifestes sur GitLab et GitHub, API des forges simulées par le serveur HTTP local avec les
 * réponses enregistrées de {@code fixtures/scm/}. Aucune forge réelle n'est appelée.
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
    TestDb db;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    RepositoryFiles repositoryFiles;

    @BeforeEach
    void reset() {
        db.reset();
    }

    /** Adapter construit avec des jetons de test, pour vérifier leur transmission. */
    private static RepositoryFiles avecJetons(long tailleMax) {
        return new RepositoryFiles(Duration.ofSeconds(1), StubServer.base() + "/gitlab", StubServer.base() + "/github",
                Optional.of("glpat-test-argos"), Optional.of("ghp-test-argos"), tailleMax);
    }

    private static Stub fixture(int status, String path, Map<String, String> headers) {
        return new Stub(status, Fixtures.resource(path), headers, 0);
    }

    private static String texte(byte[] octets) {
        return new String(octets, StandardCharsets.UTF_8);
    }

    @Test
    void gitlabArtefactCycloneDxDuJobEtJetonPrivateToken() {
        StubServer.on(GITLAB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of("Content-Type", "application/json")));

        SbomDocument sbom = avecJetons(1_048_576).latestSbom(GITLAB).orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        assertEquals(Fixtures.resource("scm/gitlab/gl-sbom-npm-npm.cdx.json"), texte(sbom.contenu()));
        StubServer.Received requete = StubServer.received("/gitlab/").get(0);
        assertEquals("GET", requete.method());
        assertEquals(List.of("glpat-test-argos"), entete(requete, "PRIVATE-TOKEN"));
        assertTrue(entete(requete, "Authorization").isEmpty(), "GitLab : jeton dans PRIVATE-TOKEN seulement");
    }

    @Test
    void gitlabFichierBrutEt404Absent() {
        StubServer.on("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/package.json/raw?ref=main",
                fixture(200, "scm/gitlab/manifeste-package.json", Map.of("Content-Type", "text/plain; charset=utf-8")));
        StubServer.on("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/requirements.txt/raw?ref=main",
                fixture(404, "scm/gitlab/404-file.json", Map.of("Content-Type", "application/json")));

        assertEquals(Fixtures.resource("scm/gitlab/manifeste-package.json"),
                texte(repositoryFiles.file(GITLAB, "package.json").orElseThrow()));
        assertTrue(repositoryFiles.file(GITLAB, "requirements.txt").isEmpty(), "404 GitLab : fichier absent");
    }

    @Test
    void gitlabSansJobDeSbomAucunAppel() {
        RepositoryRef sansJob = new RepositoryRef("GITLAB", "groupe/front", "main", null, "bom.json");
        assertTrue(repositoryFiles.latestSbom(sansJob).isEmpty());
        assertTrue(StubServer.received("/gitlab/").isEmpty());
    }

    @Test
    void githubContenuBrutAvecJetonBearer() {
        StubServer.on(GITHUB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of()));

        SbomDocument sbom = avecJetons(1_048_576).latestSbom(GITHUB).orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        StubServer.Received requete = StubServer.received("/github/").get(0);
        assertEquals(List.of("application/vnd.github.raw+json"), entete(requete, "Accept"));
        assertEquals(List.of("Bearer ghp-test-argos"), entete(requete, "Authorization"));
        assertTrue(entete(requete, "PRIVATE-TOKEN").isEmpty());
    }

    @Test
    void githubSpdxEtCycloneDxXmlReconnus() {
        StubServer.on(GITHUB_SBOM, fixture(200, "scm/github/dependency-graph-sbom.json", Map.of()));
        assertEquals(SbomDocument.Format.SPDX_JSON, repositoryFiles.latestSbom(GITHUB).orElseThrow().format());

        StubServer.on(GITHUB_SBOM, fixture(200, "scm/github/bom.cdx.xml", Map.of()));
        assertEquals(SbomDocument.Format.CYCLONEDX_XML, repositoryFiles.latestSbom(GITHUB).orElseThrow().format());

        StubServer.on(GITHUB_SBOM, Stub.ok("SPDXVersion: SPDX-2.3\nDataLicense: CC0-1.0\n"));
        assertEquals(SbomDocument.Format.SPDX_TAG_VALUE, repositoryFiles.latestSbom(GITHUB).orElseThrow().format());

        StubServer.on(GITHUB_SBOM, Stub.ok("{\"name\":\"pas une SBOM\"}"));
        assertEquals(SbomDocument.Format.INCONNU, repositoryFiles.latestSbom(GITHUB).orElseThrow().format());
    }

    @Test
    void github404AbsentEt403QuotaEpuise() {
        StubServer.on(GITHUB_SBOM, fixture(404, "scm/github/404.json", Map.of("Content-Type", "application/json")));
        assertTrue(repositoryFiles.latestSbom(GITHUB).isEmpty(), "404 GitHub : SBOM absente");

        long reset = Instant.now().plusSeconds(900).getEpochSecond();
        StubServer.on(GITHUB_SBOM, fixture(403, "scm/github/403-rate-limit.json",
                Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", Long.toString(reset))));
        QuotaEpuise quota = assertThrows(QuotaEpuise.class, () -> repositoryFiles.latestSbom(GITHUB));
        assertEquals(reset, quota.reprise().getEpochSecond());
    }

    @Test
    void redirectionNonSuivieEtJetonAbsentDuMessage() {
        StubServer.on(GITHUB_SBOM, new Stub(302, "", Map.of("Location", StubServer.base() + "/ailleurs/bom.json"), 0));

        ScmException e = assertThrows(ScmException.class, () -> avecJetons(1_048_576).latestSbom(GITHUB));

        assertTrue(e.getMessage().contains("302"));
        assertFalse(e.getMessage().contains("ghp-test-argos"), "le jeton n'apparaît dans aucun message");
        assertTrue(StubServer.received("/ailleurs").isEmpty(), "la redirection n'est pas suivie");
    }

    @Test
    void reponseAuDelaDeLaTailleMaximaleRefusee() {
        StubServer.on(GITHUB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of()));
        ScmException e = assertThrows(ScmException.class, () -> avecJetons(64).latestSbom(GITHUB));
        assertTrue(e.getMessage().contains("taille maximale"));
    }

    @Test
    void cheminsHostilesNeSortentPasDuDepot() {
        RepositoryRef hostile = new RepositoryRef("GITHUB", "org/app", "main", null, "../../../orgs/x/../bom.json");
        StubServer.on("/github/repos/org/app/contents/orgs/x/bom.json?ref=main", Stub.ok("{}"));
        assertTrue(repositoryFiles.latestSbom(hostile).isPresent());

        assertThrows(ScmException.class,
                () -> repositoryFiles.file(new RepositoryRef("GITHUB", "../..", "main", null, null), "package.json"));
        assertThrows(ScmException.class,
                () -> repositoryFiles.file(new RepositoryRef("BITBUCKET", "org/app", "main", null, null), "package.json"));
        assertEquals(1, StubServer.received("/github/").size(), "aucun appel pour un dépôt invalide ou une forge inconnue");
    }

    @Test
    void inventaireDepuisGitlabAvecTransitivite() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "front", GITLAB, List.of("package.json"));
        StubServer.on(GITLAB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of()));

        InventoryService.ImportResult resultat = inventories.refresh(projet);

        assertEquals("IMPORTED", resultat.status());
        assertEquals("SBOM", resultat.origin());
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("SBOM"))
                .body("inventory.sourceDate", equalTo("2026-11-15T06:12:44Z"))
                .body("inventory.components", hasSize(3))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/express' }.transitive", equalTo(false))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/body-parser' }.transitive", equalTo(true));
    }

    @Test
    void sbomSpdxRefuseeAvecMotifEtInventaireConserve() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "app", GITHUB, List.of("requirements.txt"));
        StubServer.on(GITHUB_SBOM, fixture(404, "scm/github/404.json", Map.of()));
        StubServer.on("/github/repos/org/app/contents/requirements.txt?ref=main",
                fixture(200, "scm/github/requirements.txt", Map.of()));

        InventoryService.ImportResult repli = inventories.refresh(projet);
        assertEquals("MANIFEST", repli.origin());

        StubServer.on(GITHUB_SBOM, fixture(200, "scm/github/dependency-graph-sbom.json", Map.of()));
        InventoryService.ImportResult refus = inventories.refresh(projet);

        assertEquals("REJECTED", refus.status());
        assertTrue(refus.reason().contains("SPDX JSON"));
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory/failures")
                .then().statusCode(200)
                .body("[0].reason", containsString("SPDX JSON non acceptée"))
                .body("[0].sourceRef", equalTo("GITHUB:org/app@main"));
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components", hasSize(2));
    }

    @Test
    void contenuServiTelQuel() {
        byte[] octets = Fixtures.resource("scm/github/bom.cdx.xml").getBytes(StandardCharsets.UTF_8);
        StubServer.on(GITHUB_SBOM, Stub.octets(octets));
        assertArrayEquals(octets, repositoryFiles.latestSbom(GITHUB).orElseThrow().contenu());
    }

    private static List<String> entete(StubServer.Received requete, String nom) {
        return requete.headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(nom))
                .flatMap(e -> e.getValue().stream()).toList();
    }
}
