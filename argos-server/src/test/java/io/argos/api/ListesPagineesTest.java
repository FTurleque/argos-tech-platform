package io.argos.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;

/**
 * Listes d'exploitation et d'import paginées au format commun (PERF-03, ADR-0040) : page par défaut, bornes, refus
 * d'une valeur non entière, pages disjointes et stables, tri du plus récent au plus ancien.
 */
@QuarkusTest
class ListesPagineesTest {

    private static final String SOURCE = "OSV";
    private static final int EVENEMENTS = 120;
    private static final int ECHECS = 60;
    private static final Instant ORIGINE = Instant.parse("2026-11-01T00:00:00Z");

    @Inject
    TestDb baseDeTest;

    private UUID projet;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        UUID espace = Api.nouvelEspace("alice", "Espace listes paginées");
        projet = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO project (id, workspace_id, name) VALUES (?, ?, 'Projet paginé')", projet, espace);
    }

    private static OffsetDateTime minute(int rang) {
        return ORIGINE.plusSeconds(60L * rang).atOffset(ZoneOffset.UTC);
    }

    /** PERF-03-S1 : page par défaut des événements d'une source, du plus récent au plus ancien. */
    @Test
    void evenementsDUneSourcePageParDefaut() {
        for (int rang = 0; rang < EVENEMENTS; rang++) {
            baseDeTest.exec("INSERT INTO ops_event (id, kind, source_id, detail, occurred_at) VALUES (?, 'K', ?, ?, ?)",
                    UUID.randomUUID(), SOURCE, "événement " + rang, minute(rang));
        }
        Api.enTantQue("alice").when().get("/api/v1/ops/sources/" + SOURCE + "/events").then().statusCode(200)
                .body("items", hasSize(50)).body("page", equalTo(0)).body("size", equalTo(50))
                .body("total", equalTo(EVENEMENTS)).body("items[0].detail", equalTo("événement " + (EVENEMENTS - 1)));
    }

    /** PERF-03-S2 et S3 : bornes ramenées, valeur non entière refusée. */
    @Test
    void bornesEtValeurNonEntiere() {
        String chemin = "/api/v1/ops/sources/" + SOURCE + "/rejected";
        Api.enTantQue("alice").queryParam("size", 1000).queryParam("page", -3).when().get(chemin).then().statusCode(200)
                .body("size", equalTo(200)).body("page", equalTo(0)).body("total", equalTo(0));
        Api.enTantQue("alice").queryParam("size", 0).when().get(chemin).then().statusCode(200).body("size", equalTo(1));
        Api.enTantQue("alice").queryParam("size", "abc").when().get(chemin).then().statusCode(400)
                .body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo("size"));
        Api.enTantQue("alice").queryParam("page", "1.5").when().get(chemin).then().statusCode(400)
                .body("attribut", equalTo("page"));
    }

    /** PERF-03-S4 : pages disjointes et stables, même pour des lignes de même horodatage. */
    @Test
    void echecsDImportPagesDisjointes() {
        for (int rang = 0; rang < ECHECS; rang++) {
            baseDeTest.exec("INSERT INTO inventory_import_failure (id, project_id, reason, received_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), projet, "motif " + rang, minute(0));
        }
        String chemin = "/api/v1/projects/" + projet + "/inventory/failures";
        JsonPath premiere = Api.enTantQue("alice").when().get(chemin).then().statusCode(200).extract().jsonPath();
        JsonPath seconde = Api.enTantQue("alice").queryParam("page", 1).when().get(chemin).then().statusCode(200)
                .extract().jsonPath();
        List<String> idsPremiere = premiere.getList("items.id");
        List<String> idsSeconde = seconde.getList("items.id");
        assertEquals(50, idsPremiere.size());
        assertEquals(ECHECS - 50, idsSeconde.size());
        Set<String> reunis = new HashSet<>(idsPremiere);
        reunis.addAll(idsSeconde);
        assertEquals(ECHECS, reunis.size(), "pages disjointes qui réunissent tous les échecs");
        assertEquals(ECHECS, premiere.getInt("total"));
    }

    /** PERF-03-S5 : l'inventaire et les impacts d'un projet sont des pages au format commun. */
    @Test
    void inventaireEtImpactsDUnProjet() {
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/impacts").then().statusCode(200)
                .body("items", hasSize(0)).body("page", equalTo(0)).body("size", equalTo(50)).body("total", equalTo(0));
        baseDeTest.exec("INSERT INTO inventory (project_id, origin, import_id, imported_at) VALUES (?, 'SBOM', ?, now())",
                projet, UUID.randomUUID());
        for (int rang = 0; rang < 3; rang++) {
            baseDeTest.exec("INSERT INTO component (id, project_id, package_key, purl, version, import_id)"
                    + " VALUES (?, ?, ?, ?, '1.0.0', ?)", UUID.randomUUID(), projet, "pkg:npm/c" + rang,
                    "pkg:npm/c" + rang + "@1.0.0", UUID.randomUUID());
        }
        JsonPath inventaire = Api.enTantQue("alice").queryParam("size", 2).when().get("/api/v1/projects/" + projet + "/inventory")
                .then().statusCode(200).extract().jsonPath();
        assertEquals(List.of("pkg:npm/c0", "pkg:npm/c1"), inventaire.getList("inventory.components.items.packageKey"));
        assertEquals(3, inventaire.getInt("inventory.components.total"));
        assertEquals(2, inventaire.getInt("inventory.components.size"));
    }
}
