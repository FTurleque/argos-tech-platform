package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.impact.ComplementaryAnalysis;
import io.argos.impact.CorrelationEngine;
import io.argos.impact.ImpactQueries;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.HeaderCallerResolver;
import io.argos.testsupport.SimulatedAnalysis;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.argos.workspace.WorkspaceDirectory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Corrélation d'impact (tâches 4.1 à 4.6) : rapprochement PURL + plage, niveaux, provenance,
 * intégrité, indépendance vis-à-vis de l'IA, idempotence, cloisonnement REST et RLS.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CorrelationTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";

    @Inject
    TestDb db;

    @Inject
    DataSource dataSource;

    @Inject
    WorkspaceDirectory workspaces;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    CorrelationEngine engine;

    @Inject
    ImpactQueries impacts;

    @Inject
    SimulatedAnalysis analysis;

    private UUID spaceA;
    private UUID spaceB;

    @BeforeEach
    void reset() {
        db.reset();
        spaceA = workspaces.create("Espace A");
        spaceB = workspaces.create("Espace B");
    }

    private UUID projectWithSbom(UUID workspace, String name, Instant generated, String... purls) {
        UUID project = projects.create(workspace, name, null, null);
        List<String[]> components = new java.util.ArrayList<>();
        List<String> direct = new java.util.ArrayList<>();
        for (int i = 0; i < purls.length; i++) {
            components.add(new String[] {"c" + i, purls[i]});
            if (i == 0) {
                direct.add("c" + i);
            }
        }
        inventories.importSbom(project, Fixtures.cyclonedx("1.6", generated.toString(), components, direct)
                .getBytes(StandardCharsets.UTF_8), "ci");
        return project;
    }

    private UUID publish(String id, String purl, String introduced, String fixed) {
        String json = Fixtures.osv(id, "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", purl, introduced, fixed);
        Fixtures.stubOsv(id, "2026-11-10T00:00:00Z", json);
        synchronizer.synchronize("OSV");
        return db.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private static Instant fresh() {
        return Instant.now().minus(2, ChronoUnit.DAYS);
    }

    private long impactsOf(UUID project) {
        return db.count("SELECT count(*) FROM project_impact WHERE project_id = '" + project + "'");
    }

    @Test
    void cor01S1EtS2EtS4PlageDeVersionsEtProjetsNonConcernes() {
        UUID inRange = projectWithSbom(spaceA, "dans-la-plage", fresh(), LIB + "@1.2.0");
        UUID outOfRange = projectWithSbom(spaceA, "hors-plage", fresh(), LIB + "@1.3.0");
        UUID without = projectWithSbom(spaceA, "sans-le-paquet", fresh(), "pkg:maven/org.autre/outil@1.0.0");

        UUID signal = publish("GHSA-COR1", LIB, "0", "1.3.0");

        assertEquals(1, impactsOf(inRange));
        assertEquals(0, impactsOf(outOfRange));
        assertEquals(0, impactsOf(without));
        assertEquals(signal, db.uuid("SELECT signal_id FROM project_impact"));
        assertEquals(LIB + "@1.2.0", db.string("SELECT c.purl FROM project_impact i JOIN component c ON c.id = i.component_id"));
    }

    @Test
    void cor01S3DependanceTransitive() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), "pkg:maven/org.exemple/direct@1.0.0", LIB + "@1.2.0");

        publish("GHSA-COR3", LIB, "0", "1.3.0");

        assertEquals(1, impactsOf(project));
        assertEquals("t", db.string("SELECT transitive::text FROM project_impact").substring(0, 1));
    }

    @Test
    void cor02S1EtCor06S1NiveauxSelonLInventaire() {
        UUID exact = projectWithSbom(spaceA, "sbom-recente", fresh(), LIB + "@1.2.0");
        UUID obsolete = projectWithSbom(spaceA, "sbom-obsolete", Instant.now().minus(40, ChronoUnit.DAYS), LIB + "@1.2.0");
        UUID manifest = projects.create(spaceA, "manifeste", null, null);
        inventories.importManifests(manifest, Map.of("package.json",
                "{\"dependencies\":{\"lodash\":\"4.17.20\"}}".getBytes(StandardCharsets.UTF_8)));
        UUID npmUnknownVersion = projects.create(spaceA, "version-inconnue", null, null);
        inventories.importManifests(npmUnknownVersion, Map.of("package.json",
                "{\"dependencies\":{\"lodash\":\"^4.0.0\"}}".getBytes(StandardCharsets.UTF_8)));

        publish("GHSA-COR2", LIB, "0", "1.3.0");
        String lodash = Fixtures.osv("GHSA-LODASH", "2026-11-11T00:00:00Z", "npm", "lodash", "pkg:npm/lodash", "0", "4.17.21");
        Fixtures.stubOsv("GHSA-LODASH", "2026-11-11T00:00:00Z", lodash);
        synchronizer.synchronize("OSV");

        assertEquals("EXACT", db.string("SELECT level FROM project_impact WHERE project_id = '" + exact + "'"));
        assertEquals("PROBABLE", db.string("SELECT level FROM project_impact WHERE project_id = '" + obsolete + "'"));
        assertEquals("PROBABLE", db.string("SELECT level FROM project_impact WHERE project_id = '" + manifest + "'"));
        assertEquals("A_VERIFIER", db.string("SELECT level FROM project_impact WHERE project_id = '" + npmUnknownVersion + "'"));
    }

    @Test
    void aVerifierPourUnAvisSansPurl() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");

        publish("GHSA-NOPURL", null, "0", "1.3.0");

        assertEquals("A_VERIFIER", db.string("SELECT level FROM project_impact WHERE project_id = '" + project + "'"));
    }

    @Test
    void cor03S1EtS2ProvenanceConsultableEtIntegrite() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        UUID signal = publish("GHSA-PROV", LIB, "0", "1.3.0");
        UUID impact = db.uuid("SELECT id FROM project_impact");

        given().header(HeaderCallerResolver.USER, "alice").header(HeaderCallerResolver.WORKSPACES, spaceA.toString())
                .when().get("/api/v1/impacts/" + impact)
                .then().statusCode(200)
                .body("signalId", equalTo(signal.toString()))
                .body("signalSource", equalTo("OSV"))
                .body("signalExternalId", equalTo("GHSA-PROV"))
                .body("signalVersionId", notNullValue())
                .body("projectId", equalTo(project.toString()))
                .body("componentPurl", equalTo(LIB + "@1.2.0"))
                .body("inventoryOrigin", equalTo("SBOM"))
                .body("inventoryDate", notNullValue())
                .body("method", equalTo("PURL_RANGE_V1"))
                .body("level", equalTo("EXACT"))
                .body("provenance", notNullValue());

        ImpactQueries.IntegrityReport report = impacts.integrity();
        assertEquals(1, report.impacts());
        assertTrue(report.intact());
        given().header(HeaderCallerResolver.USER, "ops").header(HeaderCallerResolver.OPERATOR, "true")
                .when().get("/api/v1/ops/impacts/integrity")
                .then().statusCode(200).body("withoutSignal", equalTo(0)).body("withoutMethod", equalTo(0))
                .body("impacts", equalTo(1));
    }

    @Test
    void cor07S1CorrelationRejoueeSansDoublon() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        UUID signal = publish("GHSA-REPLAY", LIB, "0", "1.3.0");
        String impact = db.string("SELECT id::text FROM project_impact");

        CorrelationEngine.CorrelationReport again = engine.correlate(signal, Optional.empty());
        engine.correlateProject(project);

        assertEquals(0, again.created());
        assertEquals(1, again.updated());
        assertEquals(1, impactsOf(project));
        assertEquals(impact, db.string("SELECT id::text FROM project_impact"));
    }

    @Test
    void migrationUniciteDuTriplet() {
        projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        publish("GHSA-UNIQ", LIB, "0", "1.3.0");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> db.exec("""
                INSERT INTO project_impact(id, signal_id, signal_version_id, project_id, workspace_id, component_id, level,
                       method, inventory_origin, provenance, created_at, updated_at)
                SELECT gen_random_uuid(), signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                       inventory_origin, provenance, now(), now() FROM project_impact
                """));
        assertTrue(e.getCause().getMessage().contains("project_impact_triplet_uk"));
    }

    @Test
    void correlationDeclencheeParUnNouvelInventaire() {
        UUID signal = publish("GHSA-LATE", LIB, "0", "1.3.0");
        assertEquals(0, db.count("SELECT count(*) FROM project_impact"));

        UUID project = projectWithSbom(spaceA, "tardif", fresh(), LIB + "@1.0.0");

        assertEquals(1, impactsOf(project));
        assertEquals(signal, db.uuid("SELECT signal_id FROM project_impact"));
    }

    @Test
    void cor05S1FournisseurIaIndisponible() {
        analysis.configure(SimulatedAnalysis.Mode.UNAVAILABLE, List.of());
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");

        UUID signal = publish("GHSA-NOAI", LIB, "0", "1.3.0");

        assertEquals(1, impactsOf(project));
        assertEquals(List.of(signal), analysis.calls(), "l'analyse est sollicitée après l'enregistrement et échoue");
        CorrelationEngine.CorrelationReport report = engine.correlate(signal, Optional.empty());
        assertEquals("UNAVAILABLE", report.analysis());
        assertEquals(1, report.impactIds().size());
    }

    @Test
    void cor05S2PlafondBudgetaireAtteintSansAppel() {
        analysis.configure(SimulatedAnalysis.Mode.BUDGET_EXHAUSTED, List.of());
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");

        UUID signal = publish("GHSA-BUDGET", LIB, "0", "1.3.0");

        assertEquals(1, impactsOf(project));
        assertTrue(analysis.calls().isEmpty(), "aucun appel au fournisseur d'IA");
        assertEquals("BUDGET_EXHAUSTED", engine.correlate(signal, Optional.empty()).analysis());
    }

    @Test
    void cor04S1SuggestionIaSansCorrespondanceDeterministe() {
        UUID unrelated = projectWithSbom(spaceA, "sans-rapport", fresh(), "pkg:maven/org.autre/outil@1.0.0");
        analysis.configure(SimulatedAnalysis.Mode.AVAILABLE,
                List.of(new ComplementaryAnalysis.Suggestion(unrelated, "nom proche dans la description")));

        UUID signal = publish("GHSA-SUGG", LIB, "0", "1.3.0");
        CorrelationEngine.CorrelationReport report = engine.correlate(signal, Optional.empty());

        assertEquals("DONE", report.analysis());
        assertEquals(1, report.suggestions().size());
        assertEquals(0, impactsOf(unrelated), "une suggestion ne crée aucun impact, donc aucune alerte");
    }

    @Test
    void cor08S1EtS2ConsultationFiltreeEtRefusJournalise() {
        UUID a = projectWithSbom(spaceA, "a", fresh(), LIB + "@1.2.0");
        UUID b = projectWithSbom(spaceB, "b", fresh(), LIB + "@1.1.0");
        UUID signal = publish("GHSA-SPACES", LIB, "0", "1.3.0");
        UUID impactB = db.uuid("SELECT id FROM project_impact WHERE project_id = '" + b + "'");

        given().header(HeaderCallerResolver.USER, "alice").header(HeaderCallerResolver.WORKSPACES, spaceA.toString())
                .when().get("/api/v1/signals/" + signal + "/impacts")
                .then().statusCode(200).body("items", hasSize(1)).body("total", equalTo(1))
                .body("items[0].projectId", equalTo(a.toString()));

        String body = given().header(HeaderCallerResolver.USER, "alice").header(HeaderCallerResolver.WORKSPACES, spaceA.toString())
                .when().get("/api/v1/impacts/" + impactB)
                .then().statusCode(404).extract().asString();
        assertTrue(!body.contains(b.toString()) && !body.contains("GHSA-SPACES"), "le refus ne révèle rien de l'impact");
        assertEquals(1, db.count("SELECT count(*) FROM access_refusal WHERE user_id = 'alice' AND resource_type = 'PROJECT_IMPACT' "
                + "AND resource_id = '" + impactB + "' AND occurred_at IS NOT NULL"));

        given().header(HeaderCallerResolver.USER, "alice").header(HeaderCallerResolver.WORKSPACES, spaceA.toString())
                .when().get("/api/v1/impacts/" + UUID.randomUUID()).then().statusCode(404);
        assertEquals(1, db.count("SELECT count(*) FROM access_refusal"), "un impact inexistant n'est pas un refus");
        given().when().get("/api/v1/signals/" + signal + "/impacts").then().statusCode(401);
    }

    @Test
    void rlsGardeFouEnBase() throws Exception {
        projectWithSbom(spaceA, "a", fresh(), LIB + "@1.2.0");
        projectWithSbom(spaceB, "b", fresh(), LIB + "@1.1.0");
        publish("GHSA-RLS", LIB, "0", "1.3.0");
        db.exec("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'argos_rls_probe') THEN
                    CREATE ROLE argos_rls_probe NOLOGIN NOSUPERUSER;
                  END IF;
                END $$;
                GRANT SELECT ON project_impact TO argos_rls_probe;
                """);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("SET LOCAL ROLE argos_rls_probe");
            assertEquals(0, count(s, "SELECT count(*) FROM project_impact"), "sans périmètre déclaré, aucune ligne");
            s.execute("SELECT set_config('argos.workspaces', '" + spaceA + "', true)");
            assertEquals(1, count(s, "SELECT count(*) FROM project_impact"));
            assertEquals(0, count(s, "SELECT count(*) FROM project_impact WHERE workspace_id = '" + spaceB + "'"));
            s.execute("SELECT set_config('argos.technical', 'on', true)");
            assertEquals(2, count(s, "SELECT count(*) FROM project_impact"));
            c.rollback();
            c.setAutoCommit(true);
        }
    }

    private static long count(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
