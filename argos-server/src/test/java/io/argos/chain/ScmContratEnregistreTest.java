package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Contrat enregistré de {@code scm-connector} dans la chaîne d'inventaire (tâche 3.2, INV-01, INV-04) : chaque
 * réponse servie par le serveur HTTP local est une réponse de {@code api.github.com} ou de {@code gitlab.com}
 * enregistrée le 2026-11-18 avec ses en-têtes ({@code fixtures/scm/enregistrees/}, voir {@code fixtures/NOTICE}),
 * sauf les limites de débit, composées d'après la documentation des forges. L'import, le repli sur les manifestes et
 * l'inventaire conservé sont vérifiés en base et par l'API ; le connecteur seul l'est sans Quarkus par
 * {@code RepositoryFilesContratEnregistreTest}. Aucune forge réelle n'est appelée.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ScmContratEnregistreTest {

    private static final String ENREGISTREMENTS_GITHUB = "scm/enregistrees/github/";
    private static final String ENREGISTREMENTS_GITLAB = "scm/enregistrees/gitlab/";

    /** Dépôt public dont les réponses GitHub ont été enregistrées. */
    private static final RepositoryRef GITHUB = new RepositoryRef("GITHUB", "CycloneDX/cyclonedx-node-npm", "main", null,
            "demo/dev-dependencies/example-results/flat/bom.1.6.json");
    private static final String GITHUB_DEPOT = "/github/repos/CycloneDX/cyclonedx-node-npm";
    private static final String GITHUB_SBOM = GITHUB_DEPOT
            + "/contents/demo/dev-dependencies/example-results/flat/bom.1.6.json?ref=main";
    private static final String GITHUB_MANIFESTE_CHEMIN = "demo/bundled-dependencies/project/package.json";
    private static final String GITHUB_MANIFESTE = GITHUB_DEPOT + "/contents/" + GITHUB_MANIFESTE_CHEMIN + "?ref=main";

    /** Projet public dont les réponses GitLab ont été enregistrées. */
    private static final RepositoryRef GITLAB = new RepositoryRef("GITLAB", "gitlab-org/security-products/tests/js-npm",
            "master", "gemnasium-dependency_scanning", "gl-sbom-npm-npm.cdx.json");
    private static final String GITLAB_PROJET = "/gitlab/api/v4/projects/gitlab-org%2Fsecurity-products%2Ftests%2Fjs-npm";
    private static final String GITLAB_SBOM = GITLAB_PROJET
            + "/jobs/artifacts/master/raw/gl-sbom-npm-npm.cdx.json?job=gemnasium-dependency_scanning";
    private static final String GITLAB_PACKAGE = GITLAB_PROJET + "/repository/files/package.json/raw?ref=master";
    private static final String GITLAB_REQUIREMENTS = GITLAB_PROJET + "/repository/files/requirements.txt/raw?ref=master";

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

    private static void sert(String chemin, String reponse) {
        StubServer.programmer(chemin, Fixtures.reponseEnregistree(reponse));
    }

    // --- GitHub -------------------------------------------------------------------------------

    @Test
    void githubSbomEnregistreeImporteeDansLInventaire() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "demo", GITHUB, List.of(GITHUB_MANIFESTE_CHEMIN));
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "200-sbom-cyclonedx.http");

        InventoryService.ImportResult resultat = inventories.importerProjet(projet).depots().get(0).result();

        assertEquals(StatutImport.IMPORTED, resultat.status(), resultat.reason());
        assertEquals("SBOM", resultat.origin());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("SBOM"))
                .body("inventory.components.items", hasSize(5))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/uuid' }.version", equalTo("8.3.2"))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/uuid' }.transitive", equalTo(false))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/undici-types' }.transitive", equalTo(true));
        assertTrue(StubServer.received(GITHUB_DEPOT + "/contents/demo/bundled").isEmpty(), "SBOM présente : aucun manifeste lu");
    }

    @Test
    void githubSbomAbsenteRepliSurLeManifeste() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "demo", GITHUB,
                List.of(GITHUB_MANIFESTE_CHEMIN, "requirements.txt", "demo/requirements.txt"));
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "404-fichier-absent.http");
        sert(GITHUB_DEPOT, ENREGISTREMENTS_GITHUB + "200-depot.http");
        sert(GITHUB_MANIFESTE, ENREGISTREMENTS_GITHUB + "200-manifeste-package-json.http");
        sert(GITHUB_DEPOT + "/contents/requirements.txt?ref=main", ENREGISTREMENTS_GITHUB + "404-fichier-absent.http");
        sert(GITHUB_DEPOT + "/contents/demo/requirements.txt?ref=main", ENREGISTREMENTS_GITHUB + "404-fichier-absent.http");

        InventoryService.ImportResult resultat = inventories.importerProjet(projet).depots().get(0).result();

        assertEquals(StatutImport.IMPORTED, resultat.status(), resultat.reason());
        assertEquals("MANIFEST", resultat.origin());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components.items", hasSize(2))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/is-obj' }.version", equalTo("3.0.0"))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/bundle-dependencies' }.version", equalTo("1.0.2"));
        assertEquals(1, StubServer.received(GITHUB_DEPOT).stream().filter(requete -> requete.pathAndQuery().equals(GITHUB_DEPOT)).count(),
                "trois 404 GitHub dans un même rafraîchissement : une seule vérification du dépôt");
    }

    @Test
    void githubDepotIntrouvableRafraichissementEnErreurSansInventaire() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        RepositoryRef absent = new RepositoryRef("GITHUB", "argos-poc-inexistant-7f3a/depot-inexistant", "main", null, "bom.json");
        UUID projet = projects.create(espace, "absent", absent, List.of("package.json"));
        String depot = "/github/repos/argos-poc-inexistant-7f3a/depot-inexistant";
        sert(depot + "/contents/bom.json?ref=main", ENREGISTREMENTS_GITHUB + "404-depot-contenu.http");
        sert(depot, ENREGISTREMENTS_GITHUB + "404-depot.http");

        InventoryService.ImportResult resultat = inventories.importerProjet(projet).depots().get(0).result();

        assertEquals(StatutImport.ERROR, resultat.status());
        assertTrue(resultat.reason().contains("introuvable"), resultat.reason());
        assertTrue(StubServer.received(depot + "/contents/package.json").isEmpty(), "aucun repli sur un dépôt introuvable");
    }

    // --- GitLab -------------------------------------------------------------------------------

    @Test
    void gitlabArtefactAbsentRepliSurLeManifeste() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "js-npm", GITLAB, List.of("package.json", "requirements.txt"));
        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "404-artefact-absent.http");
        sert(GITLAB_PACKAGE, ENREGISTREMENTS_GITLAB + "200-manifeste-package-json.http");
        sert(GITLAB_REQUIREMENTS, ENREGISTREMENTS_GITLAB + "404-fichier-absent.http");

        InventoryService.ImportResult resultat = inventories.importerProjet(projet).depots().get(0).result();

        assertEquals(StatutImport.IMPORTED, resultat.status(), resultat.reason());
        assertEquals("MANIFEST", resultat.origin());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components.items", hasSize(4))
                .body("inventory.components.items.find { it.packageKey == 'pkg:npm/decamelize' }.version", equalTo("1.1.1"));
        assertTrue(StubServer.received("/gitlab/").stream().noneMatch(requete -> requete.pathAndQuery().equals(GITLAB_PROJET)),
                "GitLab : le corps du 404 suffit, aucune vérification du projet");
    }

    /** Quota GitLab épuisé (429) au rafraîchissement : import en erreur, inventaire précédent conservé. */
    @Test
    void gitlabLimiteDeDebitInventaireConserve() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "js-npm", GITLAB, List.of("package.json"));
        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "404-artefact-absent.http");
        sert(GITLAB_PACKAGE, ENREGISTREMENTS_GITLAB + "200-manifeste-package-json.http");
        assertEquals("MANIFEST", inventories.importerProjet(projet).depots().get(0).result().origin());

        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "429-limite-debit.http");

        InventoryService.ImportResult resultat = inventories.importerProjet(projet).depots().get(0).result();
        assertEquals(StatutImport.ERROR, resultat.status());
        assertTrue(resultat.reason().contains("quota"), resultat.reason());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components.items", hasSize(4));
    }
}
