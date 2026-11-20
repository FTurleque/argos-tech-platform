package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import io.argos.inventory.ImportPlanifie;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;

/**
 * Import planifié des inventaires (changement automatiser-chaine-veille, tâche 2.3 ; INV-07, INV-08 ; décision J3 du
 * porteur du projet, 2026-11-19). Les forges sont simulées par le serveur HTTP local avec les réponses enregistrées
 * de {@code fixtures/scm/enregistrees/} (SBOM GitHub du dépôt public CycloneDX/cyclonedx-node-npm) et l'artefact
 * GitLab composé de {@code fixtures/scm/gitlab/} (voir {@code fixtures/NOTICE}) ; l'erreur 503 est composée. Les
 * jetons des forges valent les valeurs fictives de la configuration de test, transmises aux forges simulées :
 * aucun accès à Internet. La tâche planifiée est appelée avec un instant donné (ADR-0031).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
@TestProfile(ImportPlanifieTest.JetonsFictifs.class)
class ImportPlanifieTest {

    /** Jetons fictifs des forges, pour vérifier qu'ils n'apparaissent dans aucun motif d'échec. */
    public static class JetonsFictifs implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.scm.gitlab.token", "jeton-fictif-gitlab-argos",
                    "argos.scm.github.token", "jeton-fictif-github-argos");
        }
    }

    private static final RepositoryRef GITLAB = new RepositoryRef("GITLAB", "gitlab-org/security-products/tests/js-npm",
            "master", "gemnasium-dependency_scanning", "gl-sbom-npm-npm.cdx.json");
    private static final String GITLAB_SBOM = "/gitlab/api/v4/projects/gitlab-org%2Fsecurity-products%2Ftests%2Fjs-npm"
            + "/jobs/artifacts/master/raw/gl-sbom-npm-npm.cdx.json?job=gemnasium-dependency_scanning";
    private static final RepositoryRef GITHUB = new RepositoryRef("GITHUB", "CycloneDX/cyclonedx-node-npm", "main", null,
            "demo/dev-dependencies/example-results/flat/bom.1.6.json");
    private static final String GITHUB_SBOM = "/github/repos/CycloneDX/cyclonedx-node-npm"
            + "/contents/demo/dev-dependencies/example-results/flat/bom.1.6.json?ref=main";

    @Inject
    TestDb baseDeTest;

    @Inject
    DataSource dataSource;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    ImportPlanifie importPlanifie;

    @ConfigProperty(name = "argos.inventory.import.cron")
    String cron;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    private UUID espace;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Équipe A");
    }

    private static void servirForgesNominales() {
        StubServer.programmer(GITLAB_SBOM, new Stub(200, Fixtures.resource("scm/gitlab/gl-sbom-npm-npm.cdx.json"),
                Map.of("Content-Type", "application/json"), 0));
        StubServer.programmer(GITHUB_SBOM, Fixtures.reponseEnregistree("scm/enregistrees/github/200-sbom-cyclonedx.http"));
    }

    private long composants(UUID projet) {
        return baseDeTest.count("SELECT count(*) FROM component WHERE project_id = '" + projet + "'");
    }

    @Test
    void inv07S1PassageQuotidien() {
        UUID web = projects.create(espace, "web", GITLAB, List.of("package.json"));
        UUID outil = projects.create(espace, "outil", GITHUB, List.of("package.json"));
        servirForgesNominales();

        ImportPlanifie.Bilan bilan = importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z"));

        assertEquals(2, bilan.projets());
        assertEquals(2, bilan.importes());
        assertEquals(0, bilan.erreurs());
        JsonPath gitlab = Api.enTantQue("alice").when().get("/api/v1/projects/" + web + "/inventory/summary")
                .then().statusCode(200).extract().jsonPath();
        assertEquals("SBOM", gitlab.getString("summary.origin"));
        assertEquals("2026-11-15T06:12:44Z", gitlab.getString("summary.sourceDate"), "date de la source enregistrée");
        assertNotNull(gitlab.getString("summary.importedAt"), "date d'import enregistrée");
        assertEquals("GITLAB:gitlab-org/security-products/tests/js-npm@master",
                baseDeTest.string("SELECT source_ref FROM repository_inventory WHERE project_id = '" + web + "'"), "origine enregistrée");
        JsonPath github = Api.enTantQue("alice").when().get("/api/v1/projects/" + outil + "/inventory/summary")
                .then().statusCode(200).extract().jsonPath();
        assertEquals("SBOM", github.getString("summary.origin"));
        assertEquals(5, github.getInt("summary.componentCount"));
        assertNotNull(github.getString("summary.repositories[0].importedAt"));
        assertEquals("GITHUB:CycloneDX/cyclonedx-node-npm@main",
                baseDeTest.string("SELECT source_ref FROM repository_inventory WHERE project_id = '" + outil + "'"));
        List<StubServer.Received> requetes = new ArrayList<>(StubServer.received("/gitlab/"));
        requetes.addAll(StubServer.received("/github/"));
        assertTrue(requetes.stream().allMatch(requete -> "GET".equals(requete.method())), "forges interrogées en lecture seule");
    }

    @Test
    void inv07S2ForgeEnErreurPourUnDepot() {
        UUID web = projects.create(espace, "web", GITLAB, List.of("package.json"));
        UUID outil = projects.create(espace, "outil", GITHUB, List.of("package.json"));
        servirForgesNominales();
        importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z"));
        long avant = composants(web);
        String importAvant = baseDeTest.string("SELECT import_id::text FROM inventory WHERE project_id = '" + web + "'");
        StubServer.programmer(GITLAB_SBOM, Stub.status(503));
        baseDeTest.exec("DELETE FROM component_occurrence WHERE project_id = '" + outil + "'");
        baseDeTest.exec("DELETE FROM component WHERE project_id = '" + outil + "'");

        ImportPlanifie.Bilan bilan = importPlanifie.importer(Instant.parse("2026-11-21T02:00:00Z"));

        assertEquals(1, bilan.importes());
        assertEquals(1, bilan.erreurs());
        assertEquals(5, composants(outil), "le second projet est importé");
        assertEquals(avant, composants(web), "l'inventaire en place du premier projet est conservé");
        assertEquals(importAvant, baseDeTest.string("SELECT import_id::text FROM inventory WHERE project_id = '" + web + "'"));
        String motif = baseDeTest.string("SELECT reason FROM inventory_import_failure WHERE project_id = '" + web + "'");
        assertTrue(motif.contains("503"), motif);
        assertFalse(motif.contains("jeton-fictif"), "aucun jeton dans le motif");
        assertEquals(baseDeTest.string("SELECT id::text FROM project_repository WHERE project_id = '" + web + "'"),
                baseDeTest.string("SELECT repository_id::text FROM inventory_import_failure WHERE project_id = '" + web + "'"));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + web + "/inventory/summary")
                .then().statusCode(200)
                .body("summary.repositories[0].lastFailure.reason", org.hamcrest.Matchers.containsString("503"));
        StubServer.Received requete = StubServer.received(GITLAB_SBOM).get(0);
        assertTrue(requete.headers().entrySet().stream().anyMatch(entete -> entete.getKey().equalsIgnoreCase("PRIVATE-TOKEN")),
                "le jeton est transmis à la forge, jamais consigné");
    }

    /** INV-07-S3 : aucune route n'accepte une notification de pipeline ou un webhook de forge. */
    @Test
    void inv07S3AucunPointDEntreeEntrant() {
        UUID projet = projects.create(espace, "web", GITLAB, List.of("package.json"));
        servirForgesNominales();
        String corps = "{\"object_kind\":\"pipeline\",\"project\":{\"path_with_namespace\":\"gitlab-org/security-products/tests/js-npm\"}}";
        List<String> chemins = List.of("/api/v1/projects/" + projet + "/inventory", "/api/v1/projects/" + projet + "/inventory/import",
                "/api/v1/webhooks/gitlab", "/api/v1/webhooks/github", "/api/v1/hooks", "/webhook", "/api/v1/inventory/import");
        for (String chemin : chemins) {
            int statut = given().contentType("application/json").header("X-Gitlab-Event", "Pipeline Hook").body(corps)
                    .when().post(chemin).then().extract().statusCode();
            assertEquals(401, statut, "client non authentifié refusé : " + chemin);
            int authentifie = Api.enTantQue("alice").contentType("application/json").body(corps).when().post(chemin)
                    .then().extract().statusCode();
            assertTrue(authentifie == 404 || authentifie == 405, "aucune route d'import : " + chemin + " → " + authentifie);
        }
        assertEquals(0, composants(projet), "aucun import déclenché");
        assertTrue(StubServer.received("/gitlab/").isEmpty(), "aucune forge interrogée");

        Map<String, Map<String, Object>> chemins2 = Api.enTantQue("alice").queryParam("format", "json").when().get("/q/openapi")
                .then().statusCode(200).extract().jsonPath().getMap("paths");
        chemins2.forEach((chemin, operations) -> {
            assertFalse(chemin.toLowerCase().matches(".*(hook|pipeline).*"), "route entrante inattendue : " + chemin);
            if (chemin.contains("/inventory")) {
                assertEquals(List.of("get"), operations.keySet().stream().filter(cle -> !cle.equals("parameters")).toList(),
                        "l'inventaire n'est exposé qu'en lecture : " + chemin);
            }
        });
    }

    /** INV-07-S4 : cadence lue dans la configuration, au fuseau configuré, sans recompilation. */
    @Test
    void inv07S4CadenceConfiguree() throws NoSuchMethodException {
        Method tache = ImportPlanifie.class.getDeclaredMethod("planifie");
        Scheduled planification = tache.getAnnotation(Scheduled.class);
        assertEquals("inventory-import-quotidien", planification.identity());
        assertEquals("{argos.inventory.import.cron}", planification.cron(), "expression lue dans la configuration");
        assertEquals("{argos.alerting.fuseau}", planification.timeZone(), "fuseau lu dans la configuration");
        assertEquals(Scheduled.ConcurrentExecution.SKIP, planification.concurrentExecution());
        assertEquals("0 0 3 * * ?", cron, "03:00 par défaut ([PROPOSITION] P-12)");

        // Expression réglée à 02:00 (ARGOS_INVENTORY_IMPORT_CRON) : échéance suivante à 02:00 au fuseau configuré.
        CronParser parseur = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        ZonedDateTime depuis = ZonedDateTime.of(2026, 11, 19, 15, 0, 0, 0, ZoneId.of(fuseau));
        ZonedDateTime suivante = ExecutionTime.forCron(parseur.parse("0 0 2 * * ?")).nextExecution(depuis).orElseThrow();
        assertEquals(ZonedDateTime.of(2026, 11, 20, 2, 0, 0, 0, ZoneId.of(fuseau)), suivante);
        assertEquals(ZonedDateTime.of(2026, 11, 20, 3, 0, 0, 0, ZoneId.of(fuseau)),
                ExecutionTime.forCron(parseur.parse(cron)).nextExecution(depuis).orElseThrow());
    }

    @Test
    void inv08S1ImportManuelApresLePassageQuotidien() {
        UUID projet = projects.create(espace, "web", GITLAB, List.of("package.json"));
        servirForgesNominales();
        importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z"));
        assertTrue(composants(projet) > 1);

        InventoryService.ImportResult manuel = inventories.importSbom(projet, Fixtures.cyclonedx("1.6", "2026-11-19T10:00:00Z",
                java.util.Collections.singletonList(new String[] {"m", "pkg:npm/manuel@1.0.0"}), List.of("m"))
                .getBytes(StandardCharsets.UTF_8), "manuel");

        assertEquals("IMPORTED", manuel.status());
        assertEquals(1, composants(projet), "l'import manuel remplace l'inventaire de référence (INV-01)");
        assertEquals("pkg:npm/manuel@1.0.0", baseDeTest.string("SELECT purl FROM component WHERE project_id = '" + projet + "'"));
        assertEquals("manuel", baseDeTest.string("SELECT source_ref FROM repository_inventory WHERE project_id = '" + projet + "'"));
    }

    /** INV-08-S2 : le verrou du projet sérialise les imports ; un import attend que l'autre ait fini. */
    @Test
    void inv08S2ImportsSerialisesParLeVerrouDuProjet() throws Exception {
        UUID projet = projects.create(espace, "web", null, null);
        ExecutorService fil = Executors.newSingleThreadExecutor();
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement verrouillage = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                verrouillage.setString(1, "inventory:" + projet);
                verrouillage.execute();
            }
            Future<InventoryService.ImportResult> manuel = fil.submit(() -> inventories.importSbom(projet,
                    Fixtures.cyclonedx("1.6", "2026-11-19T10:00:00Z", java.util.Collections.singletonList(
                            new String[] {"m", "pkg:npm/manuel@1.0.0"}), List.of("m")).getBytes(StandardCharsets.UTF_8), "manuel"));
            try {
                manuel.get(1, TimeUnit.SECONDS);
                throw new AssertionError("l'import aurait dû attendre le verrou du projet");
            } catch (java.util.concurrent.TimeoutException attendu) {
                assertEquals(0, composants(projet), "rien n'est écrit tant que le verrou est tenu");
            }
            verrou.rollback();
            verrou.setAutoCommit(true);
            assertEquals("IMPORTED", manuel.get(20, TimeUnit.SECONDS).status());
        } finally {
            fil.shutdownNow();
        }
        assertEquals(1, composants(projet));
    }

    /**
     * INV-08-S2 : import manuel en cours (il attend le verrou du projet) quand le passage planifié atteint le même
     * projet ; les deux s'exécutent l'un après l'autre et l'inventaire final est celui de l'import terminé en dernier
     * (le passage planifié, arrivé second dans la file du verrou), sans mélange des composants.
     */
    @Test
    void inv08S2ImportManuelEtPassagePlanifieSimultanes() throws Exception {
        UUID projet = projects.create(espace, "outil", GITHUB, List.of("package.json"));
        servirForgesNominales();
        CompletableFuture<InventoryService.ImportResult> manuel;
        CompletableFuture<ImportPlanifie.Bilan> planifie;
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement verrouillage = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                verrouillage.setString(1, "inventory:" + projet);
                verrouillage.execute();
            }
            manuel = CompletableFuture.supplyAsync(() -> inventories.importSbom(projet, Fixtures.cyclonedx("1.6",
                    "2026-11-19T10:00:00Z", List.of(new String[] {"m", "pkg:npm/manuel@1.0.0"},
                            new String[] {"n", "pkg:npm/autre@2.0.0"}), List.of("m")).getBytes(StandardCharsets.UTF_8), "manuel"));
            attendreImportsEnAttente(1);
            planifie = CompletableFuture.supplyAsync(() -> importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z")));
            attendreImportsEnAttente(2);
            verrou.rollback();
            verrou.setAutoCommit(true);
        }

        assertEquals("IMPORTED", manuel.get(30, TimeUnit.SECONDS).status());
        assertEquals(1, planifie.get(30, TimeUnit.SECONDS).importes());
        assertEquals(5, composants(projet), "inventaire de l'import terminé en dernier");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM component WHERE project_id = '" + projet
                + "' AND purl IN ('pkg:npm/manuel@1.0.0', 'pkg:npm/autre@2.0.0')"), "aucun composant de l'autre import");
        assertEquals("GITHUB:CycloneDX/cyclonedx-node-npm@main",
                baseDeTest.string("SELECT source_ref FROM repository_inventory WHERE project_id = '" + projet + "'"));
    }

    /** Attend (10 s au plus) que {@code nombre} transactions attendent un verrou consultatif. */
    private void attendreImportsEnAttente(int nombre) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (baseDeTest.count("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted") < nombre) {
            if (System.nanoTime() > limite) {
                throw new AssertionError(nombre + " import(s) en attente du verrou attendu(s)");
            }
            Thread.sleep(50);
        }
    }
}
