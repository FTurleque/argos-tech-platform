package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.StatutImport;
import io.argos.impact.ImpactQueries;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.ImportPlanifie;
import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Recorrélation à chaque import (changement automatiser-chaine-veille, tâche 2.4 ; INV-09 ; décision J4 du porteur
 * du projet, 2026-11-19) : nouveau composant affecté sans nouvelle collecte, composant retiré, import refusé ou
 * inventaire conservé sans recorrélation, échec de la recorrélation repris au passage planifié suivant (marqueur,
 * [PROPOSITION] P-10). Source OSV et forge simulées par le serveur HTTP local : aucun accès à Internet.
 *
 * <p>Avant l'intégration du volet C, un impact devenu sans objet est supprimé ; les assertions portent donc sur
 * « absent des impacts ouverts du projet », vrai avant comme après la résolution automatique (COR-11).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class RecorrelationImportTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";
    private static final RepositoryRef DEPOT = new RepositoryRef("GITLAB", "groupe/app", "main", "sbom", "bom.json");
    private static final String DEPOT_SBOM = "/gitlab/api/v4/projects/groupe%2Fapp/jobs/artifacts/main/raw/bom.json?job=sbom";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    InventoryQueries queries;

    @Inject
    ImportPlanifie importPlanifie;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    ImpactQueries impacts;

    private UUID espace;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Équipe A");
    }

    private static String sbom(String... purls) {
        List<String[]> composants = new ArrayList<>();
        for (int rang = 0; rang < purls.length; rang++) {
            composants.add(new String[] {"c" + rang, purls[rang]});
        }
        return Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(), composants, List.of("c0"));
    }

    /** Signal connu qui affecte {@code org.exemple:lib} avant 2.0.0. */
    private UUID publier(String id) {
        String json = Fixtures.osv(id, "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "2.0.0");
        Fixtures.stubOsv(id, "2026-11-10T00:00:00Z", json);
        synchronizer.synchronize("OSV");
        return baseDeTest.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private List<ImpactQueries.ImpactView> ouverts(UUID projet) {
        return impacts.forProject(projet, Api.compte("alice"), Set.of(espace));
    }

    private boolean marqueur(UUID projet) {
        return "true".equals(baseDeTest.string("SELECT recorrelation_pending::text FROM inventory WHERE project_id = '" + projet + "'"));
    }

    @Test
    void inv09S1NouveauComposantAffecteSansNouvelleCollecte() {
        UUID signal = publier("GHSA-INV9-1");
        UUID projet = projects.create(espace, "app", DEPOT, List.of("package.json"));
        StubServer.programmer(DEPOT_SBOM, Stub.succes(sbom("pkg:npm/autre@1.0.0")));
        importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z"));
        assertTrue(ouverts(projet).isEmpty());
        int collectes = StubServer.received("/osv-").size();

        StubServer.programmer(DEPOT_SBOM, Stub.succes(sbom("pkg:npm/autre@1.0.0", LIB + "@1.5.0")));
        importPlanifie.importer(Instant.parse("2026-11-21T02:00:00Z"));

        List<ImpactQueries.ImpactView> apres = ouverts(projet);
        assertEquals(1, apres.size(), "un impact relie le signal, le projet et le nouveau composant");
        assertEquals(signal, apres.get(0).signalId());
        assertEquals(LIB + "@1.5.0", apres.get(0).componentPurl());
        assertEquals(collectes, StubServer.received("/osv-").size(), "aucune nouvelle collecte de l'avis");
        assertTrue(!marqueur(projet), "recorrélation réussie : marqueur effacé");
    }

    @Test
    void inv09S2ComposantRetire() {
        publier("GHSA-INV9-2");
        UUID projet = projects.create(espace, "app", DEPOT, List.of("package.json"));
        StubServer.programmer(DEPOT_SBOM, Stub.succes(sbom("pkg:npm/autre@1.0.0", LIB + "@1.5.0")));
        importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z"));
        assertEquals(1, ouverts(projet).size());

        StubServer.programmer(DEPOT_SBOM, Stub.succes(sbom("pkg:npm/autre@1.0.0")));
        importPlanifie.importer(Instant.parse("2026-11-21T02:00:00Z"));

        assertTrue(ouverts(projet).isEmpty(), "l'impact du composant retiré n'est plus compté parmi les impacts ouverts");
        // Intégration avec le volet C (COR-11-S1, J4/J5) : l'impact n'est pas supprimé, il est résolu par le système.
        assertEquals("RESOLU", baseDeTest.string("SELECT state FROM project_impact WHERE project_id = '" + projet + "'"));
        assertEquals("COMPOSANT_RETIRE", baseDeTest.string("SELECT t.reason FROM project_impact_transition t JOIN project_impact i"
                + " ON i.id = t.impact_id WHERE i.project_id = '" + projet + "' AND t.actor_system"));
    }

    /** Version mise à jour hors de la plage affectée : l'impact n'est plus vérifié. */
    @Test
    void inv09S2VersionMiseAJourHorsPlage() {
        publier("GHSA-INV9-3");
        UUID projet = projects.create(espace, "app", null, null);
        inventories.importSbom(projet, sbom(LIB + "@1.5.0").getBytes(StandardCharsets.UTF_8), "manuel#1");
        assertEquals(1, ouverts(projet).size());

        inventories.importSbom(projet, sbom(LIB + "@2.1.0").getBytes(StandardCharsets.UTF_8), "manuel#2");

        assertTrue(ouverts(projet).isEmpty());
        // COR-11-S2 : impact résolu par le système, motif « version hors plage », aucun impact pour 2.1.0.
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_impact WHERE project_id = '" + projet + "'"));
        assertEquals("VERSION_HORS_PLAGE", baseDeTest.string("SELECT t.reason FROM project_impact_transition t JOIN project_impact i"
                + " ON i.id = t.impact_id WHERE i.project_id = '" + projet + "' AND t.actor_system"));
    }

    @Test
    void inv09S3ImportRefuseOuInventaireConserveSansRecorrelation() {
        publier("GHSA-INV9-4");
        UUID projet = projects.create(espace, "app", DEPOT, List.of("package.json"));
        StubServer.programmer(DEPOT_SBOM, Stub.succes(sbom(LIB + "@1.5.0")));
        importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z"));
        String impactAvant = baseDeTest.string("SELECT id::text || '/' || updated_at::text FROM project_impact WHERE project_id = '"
                + projet + "'");
        String importAvant = baseDeTest.string("SELECT import_id::text FROM inventory WHERE project_id = '" + projet + "'");

        // SBOM refusée (CycloneDX 1.4, INV-05).
        StubServer.programmer(DEPOT_SBOM, Stub.succes("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.4\",\"components\":[]}"));
        ImportPlanifie.Bilan refus = importPlanifie.importer(Instant.parse("2026-11-21T02:00:00Z"));
        // Aucune nouvelle SBOM : inventaire SBOM conservé (artefact absent).
        StubServer.programmer(DEPOT_SBOM, Fixtures.reponseEnregistree("scm/enregistrees/gitlab/404-artefact-absent.http"));
        ImportPlanifie.Bilan conserve = importPlanifie.importer(Instant.parse("2026-11-22T02:00:00Z"));

        assertEquals(1, refus.refuses());
        assertEquals(1, conserve.conserves());
        assertEquals(impactAvant, baseDeTest.string("SELECT id::text || '/' || updated_at::text FROM project_impact WHERE project_id = '"
                + projet + "'"), "impact inchangé : aucune recorrélation");
        assertEquals(importAvant, baseDeTest.string("SELECT import_id::text FROM inventory WHERE project_id = '" + projet + "'"));
        assertTrue(!marqueur(projet));
    }

    /**
     * INV-09-S4 : la recorrélation qui suit un import réussi échoue (écriture des impacts refusée par un déclencheur
     * de test) ; l'inventaire importé reste de référence, le marqueur reste posé, et le passage planifié suivant
     * relance la recorrélation.
     */
    @Test
    void inv09S4EchecDeLaRecorrelationReprisAuPassageSuivant() {
        publier("GHSA-INV9-5");
        UUID projet = projects.create(espace, "app", null, null);
        baseDeTest.exec("""
                CREATE OR REPLACE FUNCTION test_echec_recorrelation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'échec simulé de la recorrélation'; END $$
                """);
        baseDeTest.exec("CREATE TRIGGER test_echec_recorrelation BEFORE INSERT ON project_impact"
                + " FOR EACH ROW EXECUTE FUNCTION test_echec_recorrelation()");
        try {
            InventoryService.ImportResult resultat = inventories.importSbom(projet,
                    sbom(LIB + "@1.5.0").getBytes(StandardCharsets.UTF_8), "manuel");

            assertEquals(StatutImport.IMPORTED, resultat.status());
            assertEquals(LIB + "@1.5.0", baseDeTest.string("SELECT purl FROM component WHERE project_id = '" + projet + "'"),
                    "l'inventaire importé reste l'inventaire de référence");
            assertTrue(ouverts(projet).isEmpty());
            assertTrue(marqueur(projet), "marqueur resté posé après l'échec");
            assertEquals(List.of(projet), queries.projetsARecorreler());
        } finally {
            baseDeTest.exec("DROP TRIGGER IF EXISTS test_echec_recorrelation ON project_impact");
            baseDeTest.exec("DROP FUNCTION IF EXISTS test_echec_recorrelation()");
        }

        ImportPlanifie.Bilan bilan = importPlanifie.importer(Instant.parse("2026-11-20T02:00:00Z"));

        assertEquals(1, bilan.recorrelationsRelancees());
        assertEquals(1, ouverts(projet).size(), "recorrélation relancée au passage suivant");
        assertTrue(!marqueur(projet));
        assertTrue(queries.projetsARecorreler().isEmpty());
    }
}
