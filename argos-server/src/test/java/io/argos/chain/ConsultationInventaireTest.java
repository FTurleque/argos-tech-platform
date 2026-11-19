package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Consultation de l'inventaire par projet (INV-06 ; décisions F1, F5 et G5 du porteur du projet, 2026-11-19) :
 * liste des projets visibles ({@code GET /api/v1/projects}), synthèse sans composants
 * ({@code GET /api/v1/projects/{id}/inventory/summary}) et composants inventoriés paginés
 * ({@code GET /api/v1/projects/{id}/inventory/components}) ; cloisonnement entre espaces, RLS sous
 * {@code argos_app}, bornes de pagination, tri stable, projet inconnu ou d'un autre espace, contrat OpenAPI.
 */
@QuarkusTest
class ConsultationInventaireTest {

    @Inject
    TestDb db;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    InventoryQueries queries;

    private UUID espaceA;
    private UUID espaceB;

    @BeforeEach
    void reset() {
        db.reset();
        espaceA = Api.nouvelEspace("alice", "Équipe A");
        espaceB = Api.nouvelEspace("jules", "Équipe B");
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** SBOM de {@code n} composants npm {@code paquet-NN} (le premier est direct, les autres transitifs). */
    private void importer(UUID projet, int n) {
        List<String[]> composants = new ArrayList<>();
        for (int i = n; i >= 1; i--) {
            String nom = "paquet-%02d".formatted(i);
            composants.add(new String[] {nom, "pkg:npm/" + nom + "@1.0." + i});
        }
        String sbom = Fixtures.cyclonedx("1.6", "2026-11-18T00:00:00Z", composants, List.of("paquet-01"));
        assertEquals("IMPORTED", inventories.importSbom(projet, bytes(sbom), "ci").status());
    }

    @Test
    void inv06S1ProjetsVisiblesLimitesAuxEspacesDeLAppelant() {
        UUID paie = projects.create(espaceA, "Paie", null, null);
        UUID compta = projects.create(espaceA, "Comptabilité", null, null);
        UUID secret = projects.create(espaceB, "Secret", null, null);

        Api.en("alice").when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("total", equalTo(2))
                .body("page", equalTo(0))
                .body("size", equalTo(InventoryQueries.DEFAULT_PAGE_SIZE))
                .body("items.name", contains("Comptabilité", "Paie"))
                .body("items.id", contains(compta.toString(), paie.toString()))
                .body("items.workspaceId", contains(espaceA.toString(), espaceA.toString()))
                .body("items.workspaceName", contains("Équipe A", "Équipe A"));
        Api.en("jules").when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("total", equalTo(1))
                .body("items.id", contains(secret.toString()))
                .body("items.workspaceName", contains("Équipe B"));
        given().when().get("/api/v1/projects").then().statusCode(401);
    }

    /** Un compte devenu membre d'un second espace voit les projets des deux, triés par nom puis espace. */
    @Test
    void projetsDePlusieursEspacesTriesParNomPuisEspace() {
        Api.ajouterMembre("jules", espaceB.toString(), "alice", "LECTEUR");
        UUID apiA = projects.create(espaceA, "api", null, null);
        UUID apiB = projects.create(espaceB, "api", null, null);
        UUID web = projects.create(espaceB, "web", null, null);

        Api.en("alice").when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("total", equalTo(3))
                .body("items.id", contains(apiA.toString(), apiB.toString(), web.toString()));
        Api.en("alice").queryParam("page", 1).queryParam("size", 2).when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("total", equalTo(3))
                .body("page", equalTo(1))
                .body("size", equalTo(2))
                .body("items.id", contains(web.toString()));
    }

    /** RLS : la requête ne filtre pas par espace ; sous {@code argos_app}, un compte ne lit que ses projets. */
    @Test
    void rlsGardeFouSurLaListeDesProjetsEtLesComposants() {
        UUID secret = projects.create(espaceB, "Secret", null, null);
        importer(secret, 3);
        UUID alice = Api.compte("alice");

        InventoryQueries.ProjectPage page = queries.projects(alice, 0, 50);
        assertEquals(0, page.total());
        assertTrue(page.items().isEmpty());
        InventoryQueries.ComponentPage composants = queries.components(secret, alice, 0, 50);
        assertEquals(0, composants.total(), "composants d'un autre espace invisibles sous argos_app");
        assertEquals(3, queries.components(secret, Api.compte("jules"), 0, 50).total());
    }

    @Test
    void inv06S2ComposantsPaginesTriesParNomPuisVersion() {
        UUID projet = projects.create(espaceA, "api", null, null);
        importer(projet, 25);

        Api.en("alice").queryParam("page", 0).queryParam("size", 10)
                .when().get("/api/v1/projects/" + projet + "/inventory/components")
                .then().statusCode(200)
                .body("projectId", equalTo(projet.toString()))
                .body("total", equalTo(25))
                .body("page", equalTo(0))
                .body("size", equalTo(10))
                .body("items", hasSize(10))
                .body("items[0].name", equalTo("paquet-01"))
                .body("items[0].version", equalTo("1.0.1"))
                .body("items[0].purl", equalTo("pkg:npm/paquet-01@1.0.1"))
                .body("items[0].packageKey", equalTo("pkg:npm/paquet-01"))
                .body("items[0].transitive", equalTo(false))
                .body("items[1].transitive", equalTo(true))
                .body("items[9].name", equalTo("paquet-10"));
        Api.en("alice").queryParam("page", 2).queryParam("size", 10)
                .when().get("/api/v1/projects/" + projet + "/inventory/components")
                .then().statusCode(200)
                .body("total", equalTo(25))
                .body("items.name", contains("paquet-21", "paquet-22", "paquet-23", "paquet-24", "paquet-25"));
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory/components")
                .then().statusCode(200)
                .body("size", equalTo(InventoryQueries.DEFAULT_PAGE_SIZE))
                .body("items", hasSize(25));
        // L'inventaire complet reste restitué par le point d'accès existant (clients actuels).
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200)
                .body("inventory.components", hasSize(25));
    }

    /** Même nom, plusieurs versions : ordre par version puis identifiant, identique d'un appel à l'autre. */
    @Test
    void triStableEntreDeuxAppels() {
        UUID projet = projects.create(espaceA, "api", null, null);
        String sbom = Fixtures.cyclonedx("1.6", "2026-11-18T00:00:00Z", List.of(
                new String[] {"b2", "pkg:maven/org.exemple/b@2.0"},
                new String[] {"a", "pkg:maven/org.exemple/a@1.0"},
                new String[] {"b1", "pkg:maven/org.exemple/b@1.0"}), List.of("a"));
        inventories.importSbom(projet, bytes(sbom), "ci");
        for (int i = 0; i < 2; i++) {
            Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory/components")
                    .then().statusCode(200)
                    .body("items.name", contains("org.exemple/a", "org.exemple/b", "org.exemple/b"))
                    .body("items.version", contains("1.0", "1.0", "2.0"));
        }
    }

    @Test
    void bornesDePagination() {
        UUID projet = projects.create(espaceA, "api", null, null);
        importer(projet, 3);
        String chemin = "/api/v1/projects/" + projet + "/inventory/components";

        Api.en("alice").queryParam("size", 0).when().get(chemin)
                .then().statusCode(200).body("size", equalTo(1)).body("items", hasSize(1));
        Api.en("alice").queryParam("size", -5).when().get(chemin)
                .then().statusCode(200).body("size", equalTo(1));
        Api.en("alice").queryParam("size", 100_000).when().get(chemin)
                .then().statusCode(200).body("size", equalTo(InventoryQueries.MAX_PAGE_SIZE)).body("items", hasSize(3));
        Api.en("alice").queryParam("page", -1).queryParam("size", 2).when().get(chemin)
                .then().statusCode(200).body("page", equalTo(0)).body("items.name", contains("paquet-01", "paquet-02"));
        Api.en("alice").queryParam("page", 7).when().get(chemin)
                .then().statusCode(200).body("page", equalTo(7)).body("total", equalTo(3)).body("items", empty());
        Api.en("alice").queryParam("size", 100_000).when().get("/api/v1/projects")
                .then().statusCode(200).body("size", equalTo(InventoryQueries.MAX_PAGE_SIZE));
        Api.en("alice").queryParam("page", -3).queryParam("size", 0).when().get("/api/v1/projects")
                .then().statusCode(200).body("page", equalTo(0)).body("size", equalTo(1));
    }

    @Test
    void parametresDePaginationNonEntiers() {
        UUID projet = projects.create(espaceA, "api", null, null);
        importer(projet, 3);
        String chemin = "/api/v1/projects/" + projet + "/inventory/components";

        Api.en("alice").queryParam("page", "abc").when().get(chemin)
                .then().statusCode(400).body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo("page"));
        Api.en("alice").queryParam("size", "1.5").when().get(chemin)
                .then().statusCode(400).body("attribut", equalTo("size"));
        Api.en("alice").queryParam("page", "abc").when().get("/api/v1/projects")
                .then().statusCode(400).body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo("page"));
        Api.en("alice").queryParam("size", "x").when().get("/api/v1/projects")
                .then().statusCode(400).body("attribut", equalTo("size"));
        // Un entier hors des bornes de int est saturé puis borné, comme toute autre valeur hors bornes.
        Api.en("alice").queryParam("size", "99999999999").when().get(chemin)
                .then().statusCode(200).body("size", equalTo(InventoryQueries.MAX_PAGE_SIZE));
        Api.en("alice").queryParam("page", "-99999999999").queryParam("size", "").when().get(chemin)
                .then().statusCode(200).body("page", equalTo(0)).body("size", equalTo(InventoryQueries.DEFAULT_PAGE_SIZE));
    }

    @Test
    void inv06S3ProjetInconnuOuDUnAutreEspaceIntrouvable() {
        UUID secret = projects.create(espaceB, "Secret", null, null);
        importer(secret, 2);
        UUID inconnu = UUID.randomUUID();

        Api.en("alice").when().get("/api/v1/projects/" + inconnu + "/inventory/components").then().statusCode(404);
        Api.en("alice").when().get("/api/v1/projects/" + secret + "/inventory/components").then().statusCode(404);
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("alice")
                + "' AND ressource = 'GET /api/v1/projects/" + secret + "/inventory/components' AND motif = 'NON_MEMBRE'"));
        given().when().get("/api/v1/projects/" + secret + "/inventory/components").then().statusCode(401);
    }

    @Test
    void projetSansInventairePageVide() {
        UUID projet = projects.create(espaceA, "neuf", null, null);
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory/components")
                .then().statusCode(200)
                .body("total", equalTo(0))
                .body("items", empty());
    }

    /**
     * Décision G5 (porteur du projet, 2026-11-19) : synthèse de l'inventaire sans la liste des composants, mêmes
     * valeurs que la réponse complète de {@code /inventory}, qui reste inchangée.
     */
    @Test
    void syntheseSansComposantsEgaleALInventaireComplet() {
        UUID projet = projects.create(espaceA, "api", null, null);
        String sbom = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"version\":1,"
                + "\"metadata\":{\"timestamp\":\"2026-11-18T00:00:00Z\",\"component\":{\"type\":\"application\",\"bom-ref\":\"root\",\"name\":\"api\"}},"
                + "\"components\":[{\"type\":\"library\",\"bom-ref\":\"a\",\"name\":\"a\",\"purl\":\"pkg:npm/a@1.0.0\"},"
                + "{\"type\":\"library\",\"bom-ref\":\"b\",\"name\":\"b\",\"purl\":\"pkg:npm/b@2.0.0\"},"
                + "{\"type\":\"library\",\"name\":\"outil-interne\",\"group\":\"fr.exemple\",\"version\":\"1.0\"}],"
                + "\"dependencies\":[{\"ref\":\"root\",\"dependsOn\":[\"a\"]}]}";
        assertEquals("IMPORTED", inventories.importSbom(projet, bytes(sbom), "ci").status());
        String chemin = "/api/v1/projects/" + projet + "/inventory";
        io.restassured.path.json.JsonPath complet = Api.en("alice").when().get(chemin)
                .then().statusCode(200).extract().jsonPath();

        Api.en("alice").when().get(chemin + "/summary")
                .then().statusCode(200)
                .body("projectId", equalTo(projet.toString()))
                .body("status", equalTo("AVAILABLE"))
                .body("summary.projectId", equalTo(projet.toString()))
                .body("summary.origin", equalTo("SBOM"))
                .body("summary.sourceDate", equalTo(complet.getString("inventory.sourceDate")))
                .body("summary.ageDays", equalTo(complet.getInt("inventory.ageDays")))
                .body("summary.obsolete", equalTo(complet.getBoolean("inventory.obsolete")))
                .body("summary.specVersion", equalTo("1.6"))
                .body("summary.importedAt", equalTo(complet.getString("inventory.importedAt")))
                .body("summary.componentCount", equalTo(2))
                .body("summary.ignoredCount", equalTo(1))
                .body("summary.ignoredComponents", hasSize(1))
                .body("summary.ignoredComponents[0].name", equalTo("outil-interne"))
                .body("summary.ignoredComponents[0].group", equalTo("fr.exemple"))
                .body("summary.ignoredComponents[0].reason", equalTo("SANS_PURL"))
                .body("summary.components", nullValue())
                .body("inventory", nullValue());
        assertEquals(2, complet.getList("inventory.components").size(), "réponse complète inchangée pour ses clients");
    }

    /**
     * INV-10 (changement automatiser-chaine-veille, tâche 2.5) : un projet sans dépôt, inventorié par import manuel,
     * a une liste de dépôts vide dans la synthèse et dans chaque composant ; les autres champs sont inchangés.
     */
    @Test
    void depotsVidesPourUnProjetSansDepot() {
        UUID projet = projects.create(espaceA, "manuel", null, null);
        importer(projet, 2);
        String chemin = "/api/v1/projects/" + projet + "/inventory";
        Api.en("alice").when().get(chemin + "/summary")
                .then().statusCode(200)
                .body("summary.componentCount", equalTo(2))
                .body("summary.repositories", empty());
        Api.en("alice").when().get(chemin + "/components")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].repositories", empty())
                .body("items[1].repositories", empty());
    }

    @Test
    void syntheseProjetSansInventaire() {
        UUID projet = projects.create(espaceA, "neuf", null, null);
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/inventory/summary")
                .then().statusCode(200)
                .body("projectId", equalTo(projet.toString()))
                .body("status", equalTo("NO_SBOM"))
                .body("summary", nullValue());
    }

    /** Cloisonnement de la synthèse : 404 pour un projet inconnu ou d'un autre espace (refus journalisé), RLS. */
    @Test
    void syntheseCloisonneeEntreEspaces() {
        UUID secret = projects.create(espaceB, "Secret", null, null);
        importer(secret, 3);
        String chemin = "/api/v1/projects/" + secret + "/inventory/summary";

        Api.en("alice").when().get("/api/v1/projects/" + UUID.randomUUID() + "/inventory/summary").then().statusCode(404);
        String refus = Api.en("alice").when().get(chemin).then().statusCode(404).extract().asString();
        assertTrue(!refus.contains("paquet-") && !refus.contains("Secret"), "le refus ne révèle rien de l'inventaire");
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("alice")
                + "' AND ressource = 'GET " + chemin + "' AND motif = 'NON_MEMBRE'"));
        given().when().get(chemin).then().statusCode(401);
        Api.en("jules").when().get(chemin).then().statusCode(200).body("summary.componentCount", equalTo(3));
        assertTrue(queries.summary(secret, Api.compte("alice")).isEmpty(), "synthèse d'un autre espace invisible sous argos_app");
        assertEquals(3, queries.summary(secret, Api.compte("jules")).orElseThrow().componentCount());
    }

    @Test
    void contratOpenApi() {
        Api.en("alice").queryParam("format", "json").when().get("/q/openapi")
                .then().statusCode(200)
                .body("paths.'/api/v1/projects'.get", notNullValue())
                .body("paths.'/api/v1/projects'.get.parameters.name", hasItems("page", "size"))
                .body("paths.'/api/v1/projects/{projectId}/inventory/components'.get", notNullValue())
                .body("paths.'/api/v1/projects/{projectId}/inventory/components'.get.parameters.name",
                        hasItems("projectId", "page", "size"))
                .body("paths.'/api/v1/projects/{projectId}/inventory/components'.get.responses.'404'", notNullValue())
                .body("paths.'/api/v1/projects/{projectId}/inventory/components'.get.responses.'400'", notNullValue())
                .body("paths.'/api/v1/projects'.get.responses.'400'", notNullValue())
                .body("paths.'/api/v1/projects'.get.parameters.find { it.name == 'size' }.schema.type", equalTo("integer"))
                .body("paths.'/api/v1/projects/{projectId}/inventory'.get", notNullValue())
                .body("paths.'/api/v1/projects/{projectId}/inventory/summary'.get", notNullValue())
                .body("paths.'/api/v1/projects/{projectId}/inventory/summary'.get.responses.'404'", notNullValue())
                .body("components.schemas.ProjectInventorySummary.properties.keySet()", hasItems("projectId", "status", "summary"))
                .body("components.schemas.InventorySummary.properties.keySet()", hasItems("projectId", "origin", "sourceDate",
                        "ageDays", "obsolete", "specVersion", "importedAt", "componentCount", "ignoredCount", "ignoredComponents",
                        "repositories"))
                .body("components.schemas.RepositorySummary.properties.keySet()", hasItems("id", "provider", "repository",
                        "branch", "principal", "origin", "sourceDate", "importedAt", "lastFailure"))
                .body("components.schemas.RepositoryFailure.properties.keySet()", hasItems("reason", "receivedAt"))
                .body("components.schemas.ComponentRepository.properties.keySet()", hasItems("id", "provider", "repository"))
                .body("components.schemas.InventorySummary.properties", not(hasKey("components")))
                .body("components.schemas.ProjectPage.properties.keySet()", hasItems("items", "page", "size", "total"))
                .body("components.schemas.ProjectItem.properties.keySet()", hasItems("id", "workspaceId", "workspaceName", "name"))
                .body("components.schemas.ComponentPage.properties.keySet()",
                        hasItems("projectId", "items", "page", "size", "total"))
                .body("components.schemas.ComponentItem.properties.keySet()",
                        hasItems("id", "name", "version", "purl", "packageKey", "transitive", "repositories"))
                .body("paths.'/api/v1/projects'.post", nullValue());
    }
}
