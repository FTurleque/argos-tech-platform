package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.SourceSynchronizer;
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
 * Test de migration V19 (changement automatiser-chaine-veille, tâche 2.1 ; INV-10-S4) : la section « reprise des
 * données » de {@code V19__depots_projet_et_occurrences.sql}, lue telle quelle sur le classpath, est rejouée sur un
 * jeu de données ramené à l'état antérieur à V19 (projet configuré par ses colonnes {@code scm_*}, inventaire,
 * composants et impact, sans dépôt ni occurrence). Le projet est rattaché à son dépôt principal ; inventaire,
 * composants et impacts sont inchangés ; la reprise rejouée une seconde fois est sans effet.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class MigrationDepotsTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";
    private static final String DEBUT = "-- [reprise des données : début]";
    private static final String FIN = "-- [reprise des données : fin]";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    private UUID espace;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Équipe A");
    }

    private static String repriseV19() throws IOException {
        try (InputStream in = MigrationDepotsTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V19__depots_projet_et_occurrences.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int debut = sql.indexOf(DEBUT);
            int fin = sql.indexOf(FIN);
            assertTrue(debut >= 0 && fin > debut, "section de reprise délimitée dans V19");
            return sql.substring(debut + DEBUT.length(), fin);
        }
    }

    private String instantane(UUID projet) {
        return baseDeTest.string("SELECT string_agg(id::text || ' ' || purl || ' ' || coalesce(transitive::text, '?'), ',' ORDER BY id)"
                + " FROM component WHERE project_id = '" + projet + "'")
                + " | " + baseDeTest.string("SELECT string_agg(id::text || ' ' || component_id::text || ' ' || level, ',' ORDER BY id)"
                + " FROM project_impact WHERE project_id = '" + projet + "'")
                + " | " + baseDeTest.string("SELECT origin || ' ' || source_date::text || ' ' || imported_at::text || ' ' || ignored_count"
                + " FROM inventory WHERE project_id = '" + projet + "'");
    }

    @Test
    void inv10S4ProjetExistantAUnSeulDepot() throws IOException {
        RepositoryRef depot = new RepositoryRef("GITLAB", "groupe/historique", "main", "sbom", "bom.json");
        UUID projet = projects.create(espace, "historique", depot, List.of("package.json"));
        UUID sansDepot = projects.create(espace, "manuel", null, null);
        String sbom = Fixtures.cyclonedx("1.6", Instant.now().minus(3, ChronoUnit.DAYS).toString(), List.of(
                new String[] {"lib", LIB + "@1.2.0"}, new String[] {"autre", "pkg:npm/autre@1.0.0"}), List.of("lib"));
        inventories.importSbom(projet, sbom.getBytes(StandardCharsets.UTF_8), "ci");
        inventories.importSbom(sansDepot, sbom.getBytes(StandardCharsets.UTF_8), "manuel");
        String json = Fixtures.osv("GHSA-MIG19", "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.3.0");
        Fixtures.stubOsv("GHSA-MIG19", "2026-11-10T00:00:00Z", json);
        synchronizer.synchronize("OSV");
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM project_impact"));
        // État antérieur à V19 : ni dépôt, ni dernier import de dépôt, ni occurrence.
        baseDeTest.exec("DELETE FROM component_occurrence");
        baseDeTest.exec("DELETE FROM repository_inventory");
        baseDeTest.exec("DELETE FROM project_repository");
        String avant = instantane(projet);
        String avantSansDepot = instantane(sansDepot);

        baseDeTest.exec(repriseV19());

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_repository WHERE project_id = '" + projet + "' AND principal"
                + " AND provider = 'GITLAB' AND repository = 'groupe/historique' AND branch = 'main' AND sbom_job = 'sbom'"
                + " AND sbom_path = 'bom.json' AND manifest_paths = 'package.json'"), "projet rattaché à son dépôt principal");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM project_repository WHERE project_id = '" + sansDepot + "'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM repository_inventory ri JOIN inventory i ON i.project_id = ri.project_id"
                + " WHERE ri.project_id = '" + projet + "' AND ri.origin = i.origin AND ri.source_date = i.source_date"
                + " AND ri.import_id = i.import_id AND ri.source_ref = 'ci'"), "l'inventaire devient le dernier import du dépôt");
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM component_occurrence o JOIN project_repository r ON r.id = o.repository_id"
                + " WHERE o.project_id = '" + projet + "'"));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM component_occurrence WHERE project_id = '" + sansDepot
                + "' AND repository_id IS NULL"), "projet sans dépôt : occurrences sans dépôt");
        assertEquals(avant, instantane(projet), "inventaire, composants et impacts inchangés");
        assertEquals(avantSansDepot, instantane(sansDepot));

        baseDeTest.exec(repriseV19());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_repository"), "reprise rejouée sans effet");
        assertEquals(4, baseDeTest.count("SELECT count(*) FROM component_occurrence"));

        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory/summary")
                .then().statusCode(200)
                .body("summary.componentCount", equalTo(2))
                .body("summary.repositories", hasSize(1))
                .body("summary.repositories[0].repository", equalTo("groupe/historique"))
                .body("summary.repositories[0].principal", equalTo(true))
                .body("summary.repositories[0].origin", equalTo("SBOM"));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/inventory/components")
                .then().statusCode(200)
                .body("items.find { it.purl == '" + LIB + "@1.2.0' }.repositories.repository", hasSize(1));
    }
}
