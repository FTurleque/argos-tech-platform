package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.impact.ImpactQueries;
import io.argos.impact.MatchRules;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryQueries;
import io.argos.inventory.StatutImport;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.commun.ContexteBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Projet à plusieurs dépôts (changement automatiser-chaine-veille, tâche 2.2 ; INV-10 ; décision J6 du porteur du
 * projet, 2026-11-19) : inventaire du projet = union des inventaires de ses dépôts, composant unique avec ses dépôts,
 * dépôt refusé gardant son dernier import, dépôt détaché, niveau d'impact fondé sur la meilleure occurrence
 * ([PROPOSITION] P-7), cloisonnement entre espaces et RLS des nouvelles tables sous {@code argos_app}. Forges
 * simulées par le serveur HTTP local : aucun accès à Internet.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ProjetMultiDepotsTest {

    private static final RepositoryRef FRONT = new RepositoryRef("GITLAB", "groupe/front", "main", "sbom", "bom.json");
    private static final String FRONT_SBOM = "/gitlab/api/v4/projects/groupe%2Ffront/jobs/artifacts/main/raw/bom.json?job=sbom";
    private static final RepositoryRef BACK = new RepositoryRef("GITHUB", "org/back", "main", null, "sbom/bom.json");
    private static final String BACK_SBOM = "/github/repos/org/back/contents/sbom/bom.json?ref=main";
    private static final String LIB = "pkg:maven/org.exemple/lib";

    @Inject
    TestDb baseDeTest;

    @Inject
    DataSource dataSource;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    InventoryQueries queries;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    ImpactQueries impacts;

    private UUID espaceA;
    private UUID espaceB;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        espaceA = Api.nouvelEspace("alice", "Équipe A");
        espaceB = Api.nouvelEspace("jules", "Équipe B");
    }

    /** SBOM CycloneDX 1.6 générée à {@code date}, le premier composant direct, les autres transitifs. */
    private static String sbom(Instant date, String... purls) {
        List<String[]> composants = new ArrayList<>();
        for (int rang = 0; rang < purls.length; rang++) {
            composants.add(new String[] {"c" + rang, purls[rang]});
        }
        return Fixtures.cyclonedx("1.6", date.toString(), composants, List.of("c0"));
    }

    private static Instant recent() {
        return Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private UUID projetDeuxDepots(UUID espace) {
        UUID projet = projects.create(espace, "plateforme", FRONT, List.of("package.json"));
        projects.ajouterDepot(projet, BACK, List.of("package.json"));
        return projet;
    }

    private UUID depot(UUID projet, String nom) {
        return projects.depots(projet).stream().filter(depot -> depot.ref().repository().equals(nom)).findFirst().orElseThrow().id();
    }

    @Test
    void inv10S1UnionDeDeuxDepots() {
        UUID projet = projetDeuxDepots(espaceA);
        Instant dateFront = recent();
        Instant dateBack = recent().minus(1, ChronoUnit.DAYS);
        StubServer.programmer(FRONT_SBOM, Stub.succes(sbom(dateFront, "pkg:npm/a@1.0.0", "pkg:npm/b@2.0.0")));
        // b : transitif dans front, direct dans back ; il est direct pour le projet.
        StubServer.programmer(BACK_SBOM, Stub.succes(sbom(dateBack, "pkg:npm/b@2.0.0", "pkg:maven/org.exemple/c@3.0.0")));

        InventoryService.ImportProjet resultat = inventories.importerProjet(projet);

        assertEquals(List.of(StatutImport.IMPORTED, StatutImport.IMPORTED), resultat.depots().stream().map(depot -> depot.result().status()).toList());
        assertEquals("groupe/front", resultat.depots().get(0).repository(), "dépôt principal d'abord");
        String chemin = "/api/v1/projects/" + projet + "/inventory";
        Api.enTantQue("alice").when().get(chemin + "/components")
                .then().statusCode(200)
                .body("total", equalTo(3))
                .body("items.purl", contains("pkg:npm/a@1.0.0", "pkg:npm/b@2.0.0", "pkg:maven/org.exemple/c@3.0.0"))
                .body("items.find { it.purl == 'pkg:npm/b@2.0.0' }.repositories.repository", contains("groupe/front", "org/back"))
                .body("items.find { it.purl == 'pkg:npm/b@2.0.0' }.transitive", equalTo(false))
                .body("items.find { it.purl == 'pkg:npm/a@1.0.0' }.repositories.repository", contains("groupe/front"))
                .body("items.find { it.purl == 'pkg:maven/org.exemple/c@3.0.0' }.repositories.provider", contains("GITHUB"));
        Api.enTantQue("alice").when().get(chemin + "/summary")
                .then().statusCode(200)
                .body("summary.componentCount", equalTo(3))
                .body("summary.origin", equalTo("SBOM"))
                .body("summary.sourceDate", equalTo(dateFront.toString()))
                .body("summary.repositories", hasSize(2))
                .body("summary.repositories[0].repository", equalTo("groupe/front"))
                .body("summary.repositories[0].provider", equalTo("GITLAB"))
                .body("summary.repositories[0].branch", equalTo("main"))
                .body("summary.repositories[0].principal", equalTo(true))
                .body("summary.repositories[0].origin", equalTo("SBOM"))
                .body("summary.repositories[0].sourceDate", equalTo(dateFront.toString()))
                .body("summary.repositories[0].importedAt", notNullValue())
                .body("summary.repositories[0].lastFailure", nullValue())
                .body("summary.repositories[1].repository", equalTo("org/back"))
                .body("summary.repositories[1].principal", equalTo(false))
                .body("summary.repositories[1].sourceDate", equalTo(dateBack.toString()));
        assertEquals(3, baseDeTest.count("SELECT count(*) FROM component WHERE project_id = '" + projet + "'"),
                "un composant présent dans deux dépôts n'est enregistré qu'une fois (INV-B2)");
        assertEquals(4, baseDeTest.count("SELECT count(*) FROM component_occurrence WHERE project_id = '" + projet + "'"));
    }

    @Test
    void inv10S2ImportRefusePourUnSeulDepot() {
        UUID projet = projetDeuxDepots(espaceA);
        StubServer.programmer(FRONT_SBOM, Stub.succes(sbom(recent(), "pkg:npm/a@1.0.0", "pkg:npm/b@2.0.0")));
        StubServer.programmer(BACK_SBOM, Stub.succes(sbom(recent(), "pkg:maven/org.exemple/c@3.0.0", "pkg:npm/b@2.0.0")));
        inventories.importerProjet(projet);

        StubServer.programmer(FRONT_SBOM, Stub.succes("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.4\",\"components\":[]}"));
        StubServer.programmer(BACK_SBOM, Stub.succes(sbom(recent(), "pkg:maven/org.exemple/c@3.1.0")));

        InventoryService.ImportProjet resultat = inventories.importerProjet(projet);

        assertEquals(List.of(StatutImport.REJECTED, StatutImport.IMPORTED), resultat.depots().stream().map(depot -> depot.result().status()).toList());
        String chemin = "/api/v1/projects/" + projet + "/inventory";
        Api.enTantQue("alice").when().get(chemin + "/components")
                .then().statusCode(200)
                .body("items.purl", contains("pkg:npm/a@1.0.0", "pkg:npm/b@2.0.0", "pkg:maven/org.exemple/c@3.1.0"))
                .body("items.find { it.purl == 'pkg:npm/b@2.0.0' }.repositories.repository", contains("groupe/front"));
        Api.enTantQue("alice").when().get(chemin + "/summary")
                .then().statusCode(200)
                .body("summary.repositories[0].lastFailure.reason", containsString("1.4"))
                .body("summary.repositories[0].lastFailure.receivedAt", notNullValue())
                .body("summary.repositories[1].lastFailure", nullValue());
        assertEquals(depot(projet, "groupe/front"),
                baseDeTest.uuid("SELECT repository_id FROM inventory_import_failure WHERE project_id = '" + projet + "'"),
                "l'échec est rattaché au dépôt refusé");
    }

    @Test
    void inv10S3DepotDetacheEtImpactsDuComposant() {
        UUID projet = projetDeuxDepots(espaceA);
        StubServer.programmer(FRONT_SBOM, Stub.succes(sbom(recent(), LIB + "@1.2.0", "pkg:npm/b@2.0.0")));
        StubServer.programmer(BACK_SBOM, Stub.succes(sbom(recent(), "pkg:maven/org.exemple/c@3.0.0", "pkg:npm/b@2.0.0")));
        inventories.importerProjet(projet);
        publier("GHSA-INV10", "0", "1.3.0");
        assertEquals(1, ouverts(projet, "alice").size(), "impact sur le composant présent seulement dans front");

        assertTrue(inventories.detacherDepot(projet, depot(projet, "groupe/front")));

        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory/components")
                .then().statusCode(200)
                .body("items.purl", contains("pkg:npm/b@2.0.0", "pkg:maven/org.exemple/c@3.0.0"))
                .body("items.find { it.purl == 'pkg:npm/b@2.0.0' }.repositories.repository", contains("org/back"));
        assertTrue(ouverts(projet, "alice").isEmpty(), "l'impact du composant retiré n'est plus parmi les impacts ouverts");
        // Intégration avec le volet C (COR-11-S1) : l'impact reste enregistré, résolu par le système.
        assertEquals(1, impacts.forProject(projet, Api.compte("alice"), Set.of(queries.workspaceOf(projet).orElseThrow()),
                Set.of(io.argos.impact.EtatImpact.RESOLU)).size());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory/summary")
                .then().statusCode(200)
                .body("summary.repositories.repository", contains("org/back"))
                .body("summary.componentCount", equalTo(2));
    }

    /** P-7 : un composant trouvé dans une SBOM fraîche d'un dépôt est « exact », même si l'autre SBOM est obsolète. */
    @Test
    void niveauFondeSurLaMeilleureOccurrence() {
        UUID projet = projetDeuxDepots(espaceA);
        Instant obsolete = Instant.now().minus(40, ChronoUnit.DAYS);
        StubServer.programmer(FRONT_SBOM, Stub.succes(sbom(obsolete, LIB + "@1.2.0")));
        StubServer.programmer(BACK_SBOM, Stub.succes(sbom(recent(), LIB + "@1.2.0")));
        UUID seul = projects.create(espaceA, "obsolète seul", new RepositoryRef("GITLAB", "groupe/ancien", "main", "sbom",
                "bom.json"), List.of("package.json"));
        StubServer.programmer("/gitlab/api/v4/projects/groupe%2Fancien/jobs/artifacts/main/raw/bom.json?job=sbom",
                Stub.succes(sbom(obsolete, LIB + "@1.2.0")));
        inventories.importerProjet(projet);
        inventories.importerProjet(seul);

        publier("GHSA-P7", "0", "1.3.0");

        assertEquals(MatchRules.Level.EXACT, ouverts(projet, "alice").get(0).level(), "meilleure preuve : SBOM fraîche du dépôt back");
        assertEquals(MatchRules.Level.PROBABLE, ouverts(seul, "alice").get(0).level(), "seule preuve : SBOM obsolète");
        assertEquals(1, ouverts(projet, "alice").size(), "un seul impact pour un paquet présent dans deux dépôts (COR-07)");
    }

    /** Cloisonnement : un membre d'un autre espace ne voit ni dépôts ni occurrences ; RLS sous argos_app. */
    @Test
    void depotsCloisonnesEntreEspaces() throws SQLException {
        UUID projet = projetDeuxDepots(espaceB);
        StubServer.programmer(FRONT_SBOM, Stub.succes(sbom(recent(), "pkg:npm/a@1.0.0")));
        StubServer.programmer(BACK_SBOM, Stub.succes(sbom(recent(), "pkg:npm/b@2.0.0")));
        inventories.importerProjet(projet);
        String chemin = "/api/v1/projects/" + projet + "/inventory/summary";

        String refus = Api.enTantQue("alice").when().get(chemin).then().statusCode(404).extract().asString();
        assertTrue(!refus.contains("groupe/front") && !refus.contains("org/back"), "le refus ne révèle aucun dépôt");
        given().when().get(chemin).then().statusCode(401);
        Api.enTantQue("jules").when().get(chemin).then().statusCode(200).body("summary.repositories", hasSize(2));
        assertTrue(queries.summary(projet, Api.compte("alice")).isEmpty());

        for (String table : List.of("project_repository", "repository_inventory", "component_occurrence")) {
            assertEquals(0, compterSousArgosApp(Api.compte("alice"), table, projet), table + " invisible hors de l'espace");
            assertTrue(compterSousArgosApp(Api.compte("jules"), table, projet) >= 2, table + " visible d'un membre");
        }
    }

    private long compterSousArgosApp(UUID compte, String table, UUID projet) throws SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            connexion.setAutoCommit(false);
            try {
                ContexteBase.appliquer(connexion, new ContexteBase.Contexte(compte, null, null));
                try (PreparedStatement requete = connexion.prepareStatement("SELECT count(*) FROM " + table + " WHERE project_id = ?")) {
                    requete.setObject(1, projet);
                    try (ResultSet resultat = requete.executeQuery()) {
                        resultat.next();
                        return resultat.getLong(1);
                    }
                }
            } finally {
                connexion.rollback();
                connexion.setAutoCommit(true);
            }
        }
    }

    private void publier(String id, String introduced, String fixed) {
        String json = Fixtures.osv(id, "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", LIB, introduced, fixed);
        Fixtures.stubOsv(id, "2026-11-10T00:00:00Z", json);
        synchronizer.synchronize("OSV");
    }

    private List<ImpactQueries.ImpactView> ouverts(UUID projet, String utilisateur) {
        UUID compte = Api.compte(utilisateur);
        UUID espace = queries.workspaceOf(projet).orElseThrow();
        return impacts.forProject(projet, compte, Set.of(espace));
    }
}
