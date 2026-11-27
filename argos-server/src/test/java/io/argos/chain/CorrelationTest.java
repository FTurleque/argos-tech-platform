package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.impact.StatutAnalyse;
import io.argos.impact.ComplementaryAnalysis;
import io.argos.impact.CorrelationEngine;
import io.argos.impact.ImpactQueries;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Api;
import io.argos.testsupport.SimulatedAnalysis;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
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
    TestDb baseDeTest;

    @Inject
    DataSource dataSource;

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
        baseDeTest.reset();
        spaceA = Api.nouvelEspace("alice", "Espace A");
        spaceB = Api.nouvelEspace("jules", "Espace B");
    }

    private UUID projectWithSbom(UUID espace, String name, Instant generated, String... purls) {
        UUID project = projects.create(espace, name, null, null);
        List<String[]> components = new java.util.ArrayList<>();
        List<String> direct = new java.util.ArrayList<>();
        for (int rang = 0; rang < purls.length; rang++) {
            components.add(new String[] {"c" + rang, purls[rang]});
            if (rang == 0) {
                direct.add("c" + rang);
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
        return baseDeTest.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private static Instant fresh() {
        return Instant.now().minus(2, ChronoUnit.DAYS);
    }

    private long impactsOf(UUID project) {
        return baseDeTest.count("SELECT count(*) FROM project_impact WHERE project_id = '" + project + "'");
    }

    @Test
    void cor01S3DependanceTransitive() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), "pkg:maven/org.exemple/direct@1.0.0", LIB + "@1.2.0");

        publish("GHSA-COR3", LIB, "0", "1.3.0");

        assertEquals(1, impactsOf(project));
        assertEquals("t", baseDeTest.string("SELECT transitive::text FROM project_impact").substring(0, 1));
    }

    @Test
    void cor03S1EtS2ProvenanceConsultableEtIntegrite() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        UUID signal = publish("GHSA-PROV", LIB, "0", "1.3.0");
        UUID impact = baseDeTest.uuid("SELECT id FROM project_impact");

        Api.enTantQue("alice")
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
        Api.enTantQue("alice")
                .when().get("/api/v1/ops/impacts/integrity")
                .then().statusCode(200).body("withoutSignal", equalTo(0)).body("withoutMethod", equalTo(0))
                .body("impacts", equalTo(1));
    }

    /**
     * Un avis OSV retiré (« withdrawn ») ne produit plus d'impact actif ; son historique est conservé et l'impact
     * qu'il portait est résolu par le système, non supprimé (COR-11-S3, avis retiré ; P-3).
     */
    @Test
    void avisRetireSansImpactEtCor11S3ImpactResoluAvisRetire() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        UUID signal = publish("GHSA-WITHDRAWN", LIB, "0", "1.3.0");
        assertEquals(1, impactsOf(project));
        UUID impact = baseDeTest.uuid("SELECT id FROM project_impact");

        String retire = Fixtures.osv("GHSA-WITHDRAWN", "2026-11-12T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.3.0")
                .replaceFirst("\\{", "{\"withdrawn\":\"2026-11-12T00:00:00Z\",");
        Fixtures.stubOsv("GHSA-WITHDRAWN", "2026-11-12T00:00:00Z", retire);
        synchronizer.synchronize("OSV");

        assertEquals(0, activeImpactsOf(project), "un avis retiré ne produit pas d'impact actif");
        assertEquals(1, impactsOf(project), "l'impact n'est pas supprimé");
        assertEquals("RESOLU", stateOf(impact));
        assertEquals("AVIS_RETIRE", lastReason(impact));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM signal_version WHERE signal_id = '" + signal + "'"));

        UUID autre = projectWithSbom(spaceA, "tardif", fresh(), LIB + "@1.0.0");
        assertEquals(0, impactsOf(autre), "ni pour un inventaire importé après le retrait");
    }

    private long activeImpactsOf(UUID project) {
        return baseDeTest.count("SELECT count(*) FROM project_impact WHERE project_id = '" + project
                + "' AND state IN ('OUVERT', 'CONFIRME')");
    }

    private String stateOf(UUID impact) {
        return baseDeTest.string("SELECT state FROM project_impact WHERE id = '" + impact + "'");
    }

    /** Motif de la dernière transition faite par le système. */
    private String lastReason(UUID impact) {
        return baseDeTest.string("SELECT reason FROM project_impact_transition WHERE impact_id = '" + impact
                + "' AND actor_system ORDER BY occurred_at DESC, id LIMIT 1");
    }

    private void reimport(UUID project, String... purls) {
        List<String[]> components = new java.util.ArrayList<>();
        for (int rang = 0; rang < purls.length; rang++) {
            components.add(new String[] {"c" + rang, purls[rang]});
        }
        inventories.importSbom(project, Fixtures.cyclonedx("1.6", fresh().toString(), components, List.of("c0"))
                .getBytes(StandardCharsets.UTF_8), "ci");
    }

    /** COR-11-S3 (avis modifié) puis COR-11-S5 : résolu par le système, puis rouvert quand l'avis couvre de nouveau. */
    @Test
    void cor11S3AvisModifieEtCor11S5ImpactResoluDeNouveauVerifie() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        UUID signal = publish("GHSA-COR11-3", LIB, "0", "1.3.0");
        UUID impact = baseDeTest.uuid("SELECT id FROM project_impact");

        String corrige = Fixtures.osv("GHSA-COR11-3", "2026-11-12T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.1.0");
        Fixtures.stubOsv("GHSA-COR11-3", "2026-11-12T00:00:00Z", corrige);
        synchronizer.synchronize("OSV");

        assertEquals("RESOLU", stateOf(impact));
        assertEquals("AVIS_MODIFIE", lastReason(impact));
        assertEquals(1, impactsOf(project), "l'impact n'est pas supprimé");

        String elargi = Fixtures.osv("GHSA-COR11-3", "2026-11-13T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.4.0");
        Fixtures.stubOsv("GHSA-COR11-3", "2026-11-13T00:00:00Z", elargi);
        synchronizer.synchronize("OSV");

        assertEquals("OUVERT", stateOf(impact));
        assertEquals("DE_NOUVEAU_VERIFIE", lastReason(impact));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_impact WHERE signal_id = '" + signal + "'"),
                "un seul impact pour ce signal, ce projet et ce composant (COR-07)");
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM project_impact_transition WHERE impact_id = '" + impact + "'"));
    }

    /** COR-11-S4 : un impact écarté n'est jamais modifié par le système, même si son composant est retiré. */
    @Test
    void cor11S4ImpactEcarteJamaisModifieParLeSysteme() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0", "pkg:maven/org.autre/outil@1.0.0");
        UUID signal = publish("GHSA-COR11-4", LIB, "0", "1.3.0");
        UUID impact = baseDeTest.uuid("SELECT id FROM project_impact");
        baseDeTest.exec("UPDATE project_impact SET state = 'ECARTE' WHERE id = '" + impact + "'");

        reimport(project, "pkg:maven/org.autre/outil@1.0.0");
        engine.correlate(signal, Optional.empty());

        assertEquals("ECARTE", stateOf(impact));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM project_impact_transition WHERE impact_id = '" + impact + "'"));
        assertTrue(impacts.integrity().intact(), "un impact écarté sans composant respecte INV-C3");

        // Le composant revient à la même version : l'impact écarté le retrouve, sans doublon, et reste écarté.
        reimport(project, LIB + "@1.2.0");
        assertEquals(1, impactsOf(project));
        assertEquals("ECARTE", stateOf(impact));
        assertEquals(0, activeImpactsOf(project));
    }

    /**
     * COR-11-S1 puis COR-11-S5 : un composant retiré est résolu par le système (motif persisté, sans compte auteur),
     * l'impact reste consultable avec sa provenance ; réimporté à la même version, l'impact résolu le retrouve et
     * repasse ouvert. Le choix du motif est vérifié par {@code CorrelationDeterministeTest}.
     */
    @Test
    void composantRevenuRouvreLImpactSansDoublon() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0", "pkg:maven/org.autre/outil@1.0.0");
        publish("GHSA-COR11-6", LIB, "0", "1.3.0");
        UUID impact = baseDeTest.uuid("SELECT id FROM project_impact");

        reimport(project, "pkg:maven/org.autre/outil@1.0.0");
        assertEquals("RESOLU", stateOf(impact));
        assertEquals("COMPOSANT_RETIRE", lastReason(impact));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_impact_transition WHERE impact_id = '" + impact
                + "' AND actor_system AND actor_account_id IS NULL"));
        Optional<ImpactQueries.ImpactView> vue = impacts.find(impact, Api.compte("alice"), Set.of(spaceA));
        assertTrue(vue.isPresent(), "l'impact résolu reste consultable");
        assertEquals(LIB + "@1.2.0", vue.get().componentPurl());
        assertTrue(vue.get().provenance().contains("GHSA-COR11-6"), "sa provenance est conservée");
        assertTrue(impacts.integrity().intact(), "INV-C3 : aucun impact actif sans composant");
        reimport(project, LIB + "@1.2.0");

        assertEquals(1, impactsOf(project));
        assertEquals("OUVERT", stateOf(impact));
        assertEquals("DE_NOUVEAU_VERIFIE", lastReason(impact));
        assertTrue(baseDeTest.uuid("SELECT component_id FROM project_impact WHERE id = '" + impact + "'") != null);
    }

    @Test
    void cor07S1CorrelationRejoueeSansDoublon() {
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        UUID signal = publish("GHSA-REPLAY", LIB, "0", "1.3.0");
        String impact = baseDeTest.string("SELECT id::text FROM project_impact");

        String misAJour = baseDeTest.string("SELECT updated_at::text FROM project_impact");

        CorrelationEngine.CorrelationReport again = engine.correlate(signal, Optional.empty());
        engine.correlateProject(project);

        assertEquals(0, again.created());
        assertEquals(0, again.updated(), "COR-07-S2 : un impact inchangé n'est pas réécrit");
        assertEquals(1, impactsOf(project));
        assertEquals(impact, baseDeTest.string("SELECT id::text FROM project_impact"));
        assertEquals(misAJour, baseDeTest.string("SELECT updated_at::text FROM project_impact"),
                "COR-07-S2 : la date de mise à jour d'un impact inchangé ne bouge pas");
    }

    /** COR-07-S3 : une nouvelle version de l'avis met à jour l'impact et sa date de mise à jour. */
    @Test
    void cor07S3NouvelleVersionDeLAvis() {
        projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        UUID signal = publish("GHSA-VERSION", LIB, "0", "1.3.0");
        String misAJour = baseDeTest.string("SELECT updated_at::text FROM project_impact");
        UUID versionInitiale = baseDeTest.uuid("SELECT signal_version_id FROM project_impact");

        String json = Fixtures.osv("GHSA-VERSION", "2026-11-12T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.4.0");
        Fixtures.stubOsv("GHSA-VERSION", "2026-11-12T00:00:00Z", json);
        synchronizer.synchronize("OSV");

        UUID versionCourante = baseDeTest.uuid("SELECT current_version_id FROM signal WHERE id = '" + signal + "'");
        assertNotEquals(versionInitiale, versionCourante, "le jeu de test publie une nouvelle version de l'avis");
        assertEquals(versionCourante, baseDeTest.uuid("SELECT signal_version_id FROM project_impact"));
        assertNotEquals(misAJour, baseDeTest.string("SELECT updated_at::text FROM project_impact"));
    }

    @Test
    void migrationUniciteDuTriplet() {
        projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");
        publish("GHSA-UNIQ", LIB, "0", "1.3.0");
        IllegalStateException erreur = assertThrows(IllegalStateException.class, () -> baseDeTest.exec("""
                INSERT INTO project_impact(id, signal_id, signal_version_id, project_id, workspace_id, component_id, level,
                       method, inventory_origin, provenance, created_at, updated_at)
                SELECT gen_random_uuid(), signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                       inventory_origin, provenance, now(), now() FROM project_impact
                """));
        assertTrue(erreur.getCause().getMessage().contains("project_impact_triplet_uk"));
    }

    @Test
    void correlationDeclencheeParUnNouvelInventaire() {
        UUID signal = publish("GHSA-LATE", LIB, "0", "1.3.0");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM project_impact"));

        UUID project = projectWithSbom(spaceA, "tardif", fresh(), LIB + "@1.0.0");

        assertEquals(1, impactsOf(project));
        assertEquals(signal, baseDeTest.uuid("SELECT signal_id FROM project_impact"));
    }

    @Test
    void cor05S1FournisseurIaIndisponible() {
        analysis.configure(SimulatedAnalysis.Mode.UNAVAILABLE, List.of());
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");

        UUID signal = publish("GHSA-NOAI", LIB, "0", "1.3.0");

        assertEquals(1, impactsOf(project));
        assertEquals(List.of(signal), analysis.calls(), "l'analyse est sollicitée après l'enregistrement et échoue");
        CorrelationEngine.CorrelationReport report = engine.correlate(signal, Optional.empty());
        assertEquals(StatutAnalyse.UNAVAILABLE, report.analysis());
        assertEquals(1, report.impactIds().size());
    }

    @Test
    void cor05S2PlafondBudgetaireAtteintSansAppel() {
        analysis.configure(SimulatedAnalysis.Mode.BUDGET_EXHAUSTED, List.of());
        UUID project = projectWithSbom(spaceA, "app", fresh(), LIB + "@1.2.0");

        UUID signal = publish("GHSA-BUDGET", LIB, "0", "1.3.0");

        assertEquals(1, impactsOf(project));
        assertTrue(analysis.calls().isEmpty(), "aucun appel au fournisseur d'IA");
        assertEquals(StatutAnalyse.BUDGET_EXHAUSTED, engine.correlate(signal, Optional.empty()).analysis());
    }

    @Test
    void cor04S1SuggestionIaSansCorrespondanceDeterministe() {
        UUID unrelated = projectWithSbom(spaceA, "sans-rapport", fresh(), "pkg:maven/org.autre/outil@1.0.0");
        analysis.configure(SimulatedAnalysis.Mode.AVAILABLE,
                List.of(new ComplementaryAnalysis.Suggestion(unrelated, "nom proche dans la description")));

        UUID signal = publish("GHSA-SUGG", LIB, "0", "1.3.0");
        CorrelationEngine.CorrelationReport report = engine.correlate(signal, Optional.empty());

        assertEquals(StatutAnalyse.DONE, report.analysis());
        assertEquals(1, report.suggestions().size());
        assertEquals(0, impactsOf(unrelated), "une suggestion ne crée aucun impact, donc aucune alerte");
    }

    @Test
    void cor08S1EtS2ConsultationFiltreeEtRefusJournalise() {
        UUID projetA = projectWithSbom(spaceA, "a", fresh(), LIB + "@1.2.0");
        UUID projetB = projectWithSbom(spaceB, "b", fresh(), LIB + "@1.1.0");
        UUID signal = publish("GHSA-SPACES", LIB, "0", "1.3.0");
        UUID impactB = baseDeTest.uuid("SELECT id FROM project_impact WHERE project_id = '" + projetB + "'");

        Api.enTantQue("alice")
                .when().get("/api/v1/signals/" + signal + "/impacts")
                .then().statusCode(200).body("items", hasSize(1)).body("total", equalTo(1))
                .body("items[0].projectId", equalTo(projetA.toString()));

        String body = Api.enTantQue("alice")
                .when().get("/api/v1/impacts/" + impactB)
                .then().statusCode(404).extract().asString();
        assertTrue(!body.contains(projetB.toString()) && !body.contains("GHSA-SPACES"), "le refus ne révèle rien de l'impact");
        UUID alice = Api.compte("alice");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + alice
                + "' AND ressource = 'GET /api/v1/impacts/" + impactB + "' AND motif = 'NON_MEMBRE' AND horodatage IS NOT NULL"));

        Api.enTantQue("alice")
                .when().get("/api/v1/impacts/" + UUID.randomUUID()).then().statusCode(404);
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces"), "un impact inexistant n'est pas un refus");
        given().when().get("/api/v1/signals/" + signal + "/impacts").then().statusCode(401);
    }

    /**
     * Le chemin applicatif s'exécute sous le rôle non super-utilisateur argos_app : même si le périmètre
     * transmis par la couche API contenait un espace dont le compte n'est pas membre, la RLS masque ses impacts.
     */
    @Test
    void rlsAppliqueeSurLeCheminApplicatif() {
        UUID projetA = projectWithSbom(spaceA, "a", fresh(), LIB + "@1.2.0");
        UUID projetB = projectWithSbom(spaceB, "b", fresh(), LIB + "@1.1.0");
        UUID signal = publish("GHSA-RLS-APP", LIB, "0", "1.3.0");
        UUID impactB = baseDeTest.uuid("SELECT id FROM project_impact WHERE project_id = '" + projetB + "'");
        UUID alice = Api.compte("alice");
        Set<UUID> perimetreErrone = Set.of(spaceA, spaceB);

        assertTrue(impacts.find(impactB, alice, perimetreErrone).isEmpty(), "impact d'un espace non membre masqué");
        ImpactQueries.Page page = impacts.forSignal(signal, alice, perimetreErrone, 0, 50);
        assertEquals(1, page.total());
        assertEquals(projetA, page.items().get(0).projectId());
        assertEquals(1, impacts.forSignals(List.of(signal), alice, perimetreErrone).size());
        assertTrue(impacts.forProject(projetB, alice, perimetreErrone).isEmpty());
        assertTrue(impacts.find(impactB, Api.compte("jules"), Set.of(spaceB)).isPresent(), "un membre de l'espace B le voit");
        assertEquals("argos_app", rolePendantUneLecture(alice));
    }

    @Test
    void rlsGardeFouEnBase() throws Exception {
        projectWithSbom(spaceA, "a", fresh(), LIB + "@1.2.0");
        projectWithSbom(spaceB, "b", fresh(), LIB + "@1.1.0");
        publish("GHSA-RLS", LIB, "0", "1.3.0");
        UUID alice = Api.compte("alice");
        try (Connection connexion = dataSource.getConnection(); Statement requete = connexion.createStatement()) {
            connexion.setAutoCommit(false);
            requete.execute("SET LOCAL ROLE argos_app");
            assertEquals("f", string(requete, "SELECT rolsuper::text FROM pg_roles WHERE rolname = current_user").substring(0, 1));
            assertEquals(0, count(requete, "SELECT count(*) FROM project_impact"), "sans contexte, aucune ligne");
            requete.execute("SELECT set_config('argos.compte_courant', '" + alice + "', true)");
            assertEquals(1, count(requete, "SELECT count(*) FROM project_impact"), "seulement les espaces dont le compte est membre");
            assertEquals(0, count(requete, "SELECT count(*) FROM project_impact WHERE workspace_id = '" + spaceB + "'"));
            requete.execute("SELECT set_config('argos.espace_courant', '" + spaceB + "', true)");
            assertEquals(0, count(requete, "SELECT count(*) FROM project_impact"), "espace courant dont le compte n'est pas membre");
            connexion.rollback();
            connexion.setAutoCommit(true);
        }
    }

    private String rolePendantUneLecture(UUID compte) {
        return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            impacts.forProject(UUID.randomUUID(), compte, Set.of(spaceA));
            try (Connection connexion = dataSource.getConnection(); Statement requete = connexion.createStatement()) {
                return string(requete, "SELECT current_user");
            }
        });
    }

    private static String string(Statement requete, String sql) throws Exception {
        try (ResultSet resultat = requete.executeQuery(sql)) {
            resultat.next();
            return resultat.getString(1);
        }
    }

    private static long count(Statement requete, String sql) throws Exception {
        try (ResultSet resultat = requete.executeQuery(sql)) {
            resultat.next();
            return resultat.getLong(1);
        }
    }
}
