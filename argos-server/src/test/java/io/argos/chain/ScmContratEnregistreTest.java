package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
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
import io.argos.scm.AccesRefuse;
import io.argos.scm.DepotIntrouvable;
import io.argos.scm.ParametresForges;
import io.argos.scm.QuotaEpuise;
import io.argos.scm.RepositoryFiles;
import io.argos.scm.RepositoryRef;
import io.argos.scm.SbomDocument;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Contrat enregistré de {@code scm-connector} (tâche 3.2, INV-01, INV-04) : chaque réponse servie
 * par le serveur HTTP local est une réponse de {@code api.github.com} ou de {@code gitlab.com}
 * enregistrée le 2026-11-18 avec ses en-têtes ({@code fixtures/scm/enregistrees/}, voir
 * {@code fixtures/NOTICE}), sauf les limites de débit et l'artefact SBOM GitLab, composés d'après la
 * documentation des forges faute de pouvoir les provoquer sans authentification. Aucune forge réelle
 * n'est appelée pendant le test ; les jetons sont les valeurs fictives de la configuration de test.
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

    @ConfigProperty(name = "argos.test.scm.gitlab-jeton")
    String jetonGitlab;

    @ConfigProperty(name = "argos.test.scm.github-jeton")
    String jetonGithub;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    /** Adapter dont les jetons sont les valeurs fictives de la configuration de test. */
    private RepositoryFiles forges() {
        ParametresForges forges = new ParametresForges(Duration.ofSeconds(1), StubServer.base() + "/gitlab",
                StubServer.base() + "/github", Optional.of(jetonGitlab), Optional.of(jetonGithub));
        return new RepositoryFiles(forges, 20_971_520, Clock.systemUTC());
    }

    private static void sert(String chemin, String reponse) {
        StubServer.programmer(chemin, Fixtures.reponseEnregistree(reponse));
    }

    private static List<String> entete(StubServer.Received requete, String nom) {
        return requete.headers().entrySet().stream().filter(entree -> entree.getKey().equalsIgnoreCase(nom))
                .flatMap(entree -> entree.getValue().stream()).toList();
    }

    // --- GitHub -------------------------------------------------------------------------------

    @Test
    void githubSbomTrouveeEtTransmiseAvecJetonBearer() {
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "200-sbom-cyclonedx.http");

        SbomDocument sbom = forges().lecture(GITHUB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        assertEquals(Fixtures.corpsEnregistre(ENREGISTREMENTS_GITHUB + "200-sbom-cyclonedx.http"),
                new String(sbom.contenu(), StandardCharsets.UTF_8));
        List<StubServer.Received> requetes = StubServer.received("/github/");
        assertEquals(1, requetes.size(), "SBOM trouvée : aucun appel supplémentaire");
        assertEquals("GET", requetes.get(0).method());
        assertEquals(List.of("Bearer " + jetonGithub), entete(requetes.get(0), "Authorization"));
        assertEquals(List.of("application/vnd.github.raw+json"), entete(requetes.get(0), "Accept"));
    }

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
                .body("inventory.components", hasSize(5))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/uuid' }.version", equalTo("8.3.2"))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/uuid' }.transitive", equalTo(false))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/undici-types' }.transitive", equalTo(true));
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
                .body("inventory.components", hasSize(2))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/is-obj' }.version", equalTo("3.0.0"))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/bundle-dependencies' }.version", equalTo("1.0.2"));
        assertEquals(1, StubServer.received(GITHUB_DEPOT).stream().filter(requete -> requete.pathAndQuery().equals(GITHUB_DEPOT)).count(),
                "trois 404 GitHub dans un même rafraîchissement : une seule vérification du dépôt");
    }

    @Test
    void githubDepotIntrouvable() {
        RepositoryRef absent = new RepositoryRef("GITHUB", "argos-poc-inexistant-7f3a/depot-inexistant", "main", null, "bom.json");
        String depot = "/github/repos/argos-poc-inexistant-7f3a/depot-inexistant";
        sert(depot + "/contents/bom.json?ref=main", ENREGISTREMENTS_GITHUB + "404-depot-contenu.http");
        sert(depot, ENREGISTREMENTS_GITHUB + "404-depot.http");

        DepotIntrouvable erreur = assertThrows(DepotIntrouvable.class, () -> forges().lecture(absent).latestSbom());

        assertTrue(erreur.getMessage().contains("argos-poc-inexistant-7f3a/depot-inexistant"));
        assertFalse(erreur.getMessage().contains(jetonGithub), "le jeton n'apparaît dans aucun message");
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

    @Test
    void githubJetonRefuse() {
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "401-jeton-refuse.http");

        AccesRefuse erreur = assertThrows(AccesRefuse.class, () -> forges().lecture(GITHUB).latestSbom());

        assertTrue(erreur.getMessage().contains("(HTTP 401)"), erreur.getMessage());
        assertTrue(erreur.getMessage().contains("jeton d'accès refusé"));
        assertFalse(erreur.getMessage().contains(jetonGithub), "le jeton n'apparaît dans aucun message");
        assertEquals(1, StubServer.received("/github/").size(), "aucune vérification du dépôt après un 401");
    }

    @Test
    void githubLimiteDeDebitPrincipaleEtSecondaire() {
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "403-limite-debit.http");
        Instant avant = Instant.now();
        QuotaEpuise principale = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITHUB).latestSbom());
        assertBorne(principale.reprise(), avant.plusSeconds(900));

        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "403-limite-secondaire.http");
        avant = Instant.now();
        QuotaEpuise secondaire = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITHUB).latestSbom());
        assertBorne(secondaire.reprise(), avant.plusSeconds(60));

        // Limite secondaire signalée par son seul message : ni Retry-After ni quota principal épuisé.
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "403-limite-secondaire-sans-delai.http");
        avant = Instant.now();
        QuotaEpuise sansDelai = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITHUB).latestSbom());
        assertBorne(sansDelai.reprise(), avant.plusSeconds(3600));
    }

    // --- GitLab -------------------------------------------------------------------------------

    @Test
    void gitlabSbomTrouveeAvecJetonPrivateToken() {
        // Artefact composé : aucun artefact de job n'est lisible anonymement sur gitlab.com (403 enregistré).
        StubServer.programmer(GITLAB_SBOM, new StubServer.Stub(200, Fixtures.resource("scm/gitlab/gl-sbom-npm-npm.cdx.json"),
                Map.of("Content-Type", "application/json")));

        SbomDocument sbom = forges().lecture(GITLAB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        StubServer.Received requete = StubServer.received("/gitlab/").get(0);
        assertEquals(List.of(jetonGitlab), entete(requete, "PRIVATE-TOKEN"));
        assertTrue(entete(requete, "Authorization").isEmpty());
    }

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
                .body("inventory.components", hasSize(4))
                .body("inventory.components.find { it.packageKey == 'pkg:npm/decamelize' }.version", equalTo("1.1.1"));
        assertTrue(StubServer.received("/gitlab/").stream().noneMatch(requete -> requete.pathAndQuery().equals(GITLAB_PROJET)),
                "GitLab : le corps du 404 suffit, aucune vérification du projet");
    }

    @Test
    void gitlabProjetIntrouvable() {
        RepositoryRef absent = new RepositoryRef("GITLAB", "argos-poc-inexistant-7f3a/depot-inexistant", "main", null, null);
        sert("/gitlab/api/v4/projects/argos-poc-inexistant-7f3a%2Fdepot-inexistant/repository/files/package.json/raw?ref=main",
                ENREGISTREMENTS_GITLAB + "404-projet-fichier.http");

        DepotIntrouvable erreur = assertThrows(DepotIntrouvable.class, () -> forges().lecture(absent).file("package.json"));

        assertTrue(erreur.getMessage().contains("GITLAB"));
        assertFalse(erreur.getMessage().contains(jetonGitlab), "le jeton n'apparaît dans aucun message");
    }

    @Test
    void gitlabJetonRefuseEtArtefactInterdit() {
        sert(GITLAB_PACKAGE, ENREGISTREMENTS_GITLAB + "401-jeton-refuse.http");
        AccesRefuse refuse = assertThrows(AccesRefuse.class, () -> forges().lecture(GITLAB).file("package.json"));
        assertTrue(refuse.getMessage().contains("(HTTP 401)"), refuse.getMessage());
        assertFalse(refuse.getMessage().contains(jetonGitlab));

        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "403-artefact-interdit.http");
        AccesRefuse interdit = assertThrows(AccesRefuse.class, () -> forges().lecture(GITLAB).latestSbom());
        assertTrue(interdit.getMessage().contains("(HTTP 403)"), interdit.getMessage());
    }

    @Test
    void gitlabLimiteDeDebitInventaireConserve() {
        UUID espace = Api.nouvelEspace("alice", "Équipe A");
        UUID projet = projects.create(espace, "js-npm", GITLAB, List.of("package.json"));
        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "404-artefact-absent.http");
        sert(GITLAB_PACKAGE, ENREGISTREMENTS_GITLAB + "200-manifeste-package-json.http");
        assertEquals("MANIFEST", inventories.importerProjet(projet).depots().get(0).result().origin());

        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "429-limite-debit.http");
        Instant avant = Instant.now();
        QuotaEpuise quota = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITLAB).latestSbom());
        assertBorne(quota.reprise(), avant.plusSeconds(120));

        InventoryService.ImportResult resultat = inventories.importerProjet(projet).depots().get(0).result();
        assertEquals(StatutImport.ERROR, resultat.status());
        assertTrue(resultat.reason().contains("quota"), resultat.reason());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.origin", equalTo("MANIFEST"))
                .body("inventory.components", hasSize(4));
    }

    @Test
    void entetesDeTransportNonRejoues() {
        Fixtures.ReponseHttp reponse = Fixtures.lireReponse(ENREGISTREMENTS_GITHUB + "200-depot.http");
        assertEquals(200, reponse.status());
        assertFalse(reponse.entetes().keySet().stream().anyMatch(cle -> cle.equalsIgnoreCase("content-length")),
                "les en-têtes de transport ne sont pas rejoués");
    }

    /** La reprise annoncée tombe à deux secondes près sur l'instant attendu. */
    private static void assertBorne(Instant reprise, Instant attendu) {
        assertTrue(Math.abs(Duration.between(attendu, reprise).toSeconds()) <= 2,
                "reprise " + reprise + " au lieu de " + attendu);
    }
}
