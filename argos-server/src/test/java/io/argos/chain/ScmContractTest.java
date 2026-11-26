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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.StatutImport;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.scm.ParametresForges;
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
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    RepositoryFiles repositoryFiles;

    /** Jetons fictifs de la configuration de test. */
    @ConfigProperty(name = "argos.test.scm.gitlab-jeton")
    String jetonGitlab;

    @ConfigProperty(name = "argos.test.scm.github-jeton")
    String jetonGithub;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    /** Adapter construit avec des jetons de test, pour vérifier leur transmission. */
    private RepositoryFiles avecJetons(long tailleMax) {
        ParametresForges forges = new ParametresForges(Duration.ofSeconds(1), StubServer.base() + "/gitlab",
                StubServer.base() + "/github", Optional.of(jetonGitlab), Optional.of(jetonGithub));
        return new RepositoryFiles(forges, tailleMax, Clock.systemUTC());
    }

    private static Stub fixture(int status, String path, Map<String, String> headers) {
        return new Stub(status, Fixtures.resource(path), headers);
    }

    private static String texte(byte[] octets) {
        return new String(octets, StandardCharsets.UTF_8);
    }

    @Test
    void gitlabArtefactCycloneDxDuJobEtJetonPrivateToken() {
        StubServer.programmer(GITLAB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of("Content-Type", "application/json")));

        SbomDocument sbom = avecJetons(1_048_576).lecture(GITLAB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        assertEquals(Fixtures.resource("scm/gitlab/gl-sbom-npm-npm.cdx.json"), texte(sbom.contenu()));
        StubServer.Received requete = StubServer.received("/gitlab/").get(0);
        assertEquals("GET", requete.method());
        assertEquals(List.of(jetonGitlab), entete(requete, "PRIVATE-TOKEN"));
        assertTrue(entete(requete, "Authorization").isEmpty(), "GitLab : jeton dans PRIVATE-TOKEN seulement");
    }

    @Test
    void gitlabFichierBrutEt404Absent() {
        StubServer.programmer("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/package.json/raw?ref=main",
                fixture(200, "scm/gitlab/manifeste-package.json", Map.of("Content-Type", "text/plain; charset=utf-8")));
        StubServer.programmer("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/requirements.txt/raw?ref=main",
                fixture(404, "scm/gitlab/404-file.json", Map.of("Content-Type", "application/json")));

        assertEquals(Fixtures.resource("scm/gitlab/manifeste-package.json"),
                texte(repositoryFiles.lecture(GITLAB).file("package.json").orElseThrow()));
        assertTrue(repositoryFiles.lecture(GITLAB).file("requirements.txt").isEmpty(), "404 GitLab : fichier absent");
    }

    @Test
    void gitlabSansJobDeSbomAucunAppel() {
        RepositoryRef sansJob = new RepositoryRef("GITLAB", "groupe/front", "main", null, "bom.json");
        assertTrue(repositoryFiles.lecture(sansJob).latestSbom().isEmpty());
        assertTrue(StubServer.received("/gitlab/").isEmpty());
    }

    @Test
    void githubContenuBrutAvecJetonBearer() {
        StubServer.programmer(GITHUB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of()));

        SbomDocument sbom = avecJetons(1_048_576).lecture(GITHUB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        StubServer.Received requete = StubServer.received("/github/").get(0);
        assertEquals(List.of("application/vnd.github.raw+json"), entete(requete, "Accept"));
        assertEquals(List.of("Bearer " + jetonGithub), entete(requete, "Authorization"));
        assertTrue(entete(requete, "PRIVATE-TOKEN").isEmpty());
    }

    @Test
    void githubSpdxEtCycloneDxXmlReconnus() {
        StubServer.programmer(GITHUB_SBOM, fixture(200, "scm/github/dependency-graph-sbom.json", Map.of()));
        assertEquals(SbomDocument.Format.SPDX_JSON, repositoryFiles.lecture(GITHUB).latestSbom().orElseThrow().format());

        StubServer.programmer(GITHUB_SBOM, fixture(200, "scm/github/bom.cdx.xml", Map.of()));
        assertEquals(SbomDocument.Format.CYCLONEDX_XML, repositoryFiles.lecture(GITHUB).latestSbom().orElseThrow().format());

        StubServer.programmer(GITHUB_SBOM, Stub.succes("SPDXVersion: SPDX-2.3\nDataLicense: CC0-1.0\n"));
        assertEquals(SbomDocument.Format.SPDX_TAG_VALUE, repositoryFiles.lecture(GITHUB).latestSbom().orElseThrow().format());

        StubServer.programmer(GITHUB_SBOM, Stub.succes("{\"name\":\"pas une SBOM\"}"));
        assertEquals(SbomDocument.Format.INCONNU, repositoryFiles.lecture(GITHUB).latestSbom().orElseThrow().format());
    }

    @Test
    void github404AbsentEt403QuotaEpuise() {
        StubServer.programmer(GITHUB_SBOM, fixture(404, "scm/github/404.json", Map.of("Content-Type", "application/json")));
        StubServer.programmer("/github/repos/org/app", Stub.succes("{\"full_name\":\"org/app\"}"));
        assertTrue(repositoryFiles.lecture(GITHUB).latestSbom().isEmpty(), "404 GitHub sur un dépôt existant : SBOM absente");

        long reset = Instant.now().plusSeconds(900).getEpochSecond();
        StubServer.programmer(GITHUB_SBOM, fixture(403, "scm/github/403-rate-limit.json",
                Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", Long.toString(reset))));
        QuotaEpuise quota = assertThrows(QuotaEpuise.class, () -> repositoryFiles.lecture(GITHUB).latestSbom());
        assertEquals(reset, quota.reprise().getEpochSecond());
    }

    @Test
    void redirectionNonSuivieEtJetonAbsentDuMessage() {
        StubServer.programmer(GITHUB_SBOM, new Stub(302, "", Map.of("Location", StubServer.base() + "/ailleurs/bom.json")));

        ScmException erreur = assertThrows(ScmException.class, () -> avecJetons(1_048_576).lecture(GITHUB).latestSbom());

        assertTrue(erreur.getMessage().contains("302"));
        assertFalse(erreur.getMessage().contains(jetonGithub), "le jeton n'apparaît dans aucun message");
        assertTrue(StubServer.received("/ailleurs").isEmpty(), "la redirection n'est pas suivie");
    }

    @Test
    void reponseAuDelaDeLaTailleMaximaleRefusee() {
        StubServer.programmer(GITHUB_SBOM, fixture(200, "scm/gitlab/gl-sbom-npm-npm.cdx.json", Map.of()));
        ScmException erreur = assertThrows(ScmException.class, () -> avecJetons(64).lecture(GITHUB).latestSbom());
        assertTrue(erreur.getMessage().contains("taille maximale"));
    }

    @Test
    void cheminsHostilesNeSortentPasDuDepot() {
        RepositoryRef hostile = new RepositoryRef("GITHUB", "org/app", "main", null, "../../../orgs/x/../bom.json");
        StubServer.programmer("/github/repos/org/app/contents/orgs/x/bom.json?ref=main", Stub.succes("{}"));
        assertTrue(repositoryFiles.lecture(hostile).latestSbom().isPresent());

        assertThrows(ScmException.class,
                () -> repositoryFiles.lecture(new RepositoryRef("GITHUB", "../..", "main", null, null)).file("package.json"));
        assertThrows(ScmException.class,
                () -> repositoryFiles.lecture(new RepositoryRef("BITBUCKET", "org/app", "main", null, null)).file("package.json"));
        assertEquals(1, StubServer.received("/github/").size(), "aucun appel pour un dépôt invalide ou une forge inconnue");
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

    @Test
    void contenuServiTelQuel() {
        byte[] octets = Fixtures.resource("scm/github/bom.cdx.xml").getBytes(StandardCharsets.UTF_8);
        StubServer.programmer(GITHUB_SBOM, Stub.octets(octets));
        assertArrayEquals(octets, repositoryFiles.lecture(GITHUB).latestSbom().orElseThrow().contenu());
    }

    private static List<String> entete(StubServer.Received requete, String nom) {
        return requete.headers().entrySet().stream().filter(entree -> entree.getKey().equalsIgnoreCase(nom))
                .flatMap(entree -> entree.getValue().stream()).toList();
    }
}
