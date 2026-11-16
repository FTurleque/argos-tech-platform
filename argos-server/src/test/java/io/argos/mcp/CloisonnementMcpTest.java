package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.impact.ImpactQueries;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/** MCP-04 : aucune fuite de périmètre (tâches 2.2 et 3.2), sous la RLS du rôle {@code argos_app}. */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CloisonnementMcpTest {

    private static final String LIB = "pkg:maven/org.exemple/cloison";

    @Inject
    TestDb db;

    @Inject
    ProjectDirectory projets;

    @Inject
    InventoryService inventaires;

    @Inject
    InventoryQueries inventaireQueries;

    @Inject
    SourceSynchronizer synchronisation;

    @Inject
    ImpactQueries impacts;

    private UUID espaceA;
    private UUID espaceB;
    private UUID projetP;
    private UUID signal;

    @BeforeEach
    void preparer() {
        db.reset();
        espaceA = Api.nouvelEspace("lina", "Espace de Lina");
        espaceB = Api.nouvelEspace("marc", "Espace de Marc");
        projetP = projets.create(espaceB, "Projet Pluton confidentiel", null, null);
        inventaires.importSbom(projetP, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", LIB + "@1.2.0"}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        String json = Fixtures.osv("GHSA-MCP4", "2026-11-10T00:00:00Z", "Maven", "org.exemple:cloison", LIB, "0", "1.3.0");
        Fixtures.stubOsv("GHSA-MCP4", "2026-11-10T00:00:00Z", json);
        synchronisation.synchronize("OSV");
        signal = db.uuid("SELECT id FROM signal WHERE external_id = 'GHSA-MCP4'");
        assertEquals(1, db.count("SELECT count(*) FROM project_impact WHERE project_id = '" + projetP + "'"));
    }

    /** MCP-04-S1 : alertes d'un projet d'un autre espace refusées, sans aucune information, refus journalisé. */
    @Test
    void mcp04S1ProjetDUnAutreEspace() {
        ExtractableResponse<Response> r = Mcp.appeler("lina", "get_security_alerts", Map.of("project_id", projetP.toString()));
        assertEquals(Boolean.TRUE, r.path("result.isError"), r.asString());
        assertTrue(((String) r.path("result.content[0].text")).startsWith("INTROUVABLE"));
        assertAucuneInformationSurP(r.asString());
        UUID lina = Api.compte("lina");
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + lina
                + "' AND ressource = 'MCP tools/call get_security_alerts' AND motif = 'NON_MEMBRE'"));
        assertEquals(1, db.count("SELECT count(*) FROM mcp_appel WHERE compte_id = '" + lina
                + "' AND outil = 'get_security_alerts' AND issue = 'REFUS_ACCES'"));

        // Le membre de l'espace B voit l'alerte.
        ExtractableResponse<Response> m = Mcp.appeler("marc", "get_security_alerts", Map.of("project_id", projetP.toString()));
        assertEquals(1, (int) m.path("result.structuredContent.nombre"), m.asString());
        assertEquals("Projet Pluton confidentiel", m.path("result.structuredContent.alertes[0].projet"));
    }

    /** Sans projet désigné, seules les alertes des espaces de l'appelant sont renvoyées. */
    @Test
    void alertesLimiteesAuxEspacesDeLAppelant() {
        ExtractableResponse<Response> r = Mcp.appeler("lina", "get_security_alerts", Map.of());
        assertEquals(0, (int) r.path("result.structuredContent.nombre"), r.asString());
        assertAucuneInformationSurP(r.asString());
        assertEquals(1, (int) Mcp.appeler("marc", "get_security_alerts", Map.of()).path("result.structuredContent.nombre"));
    }

    /** Contexte de projet et explication d'un signal : rien sur P pour un non-membre. */
    @Test
    void contexteEtExplicationSansFuite() {
        ExtractableResponse<Response> ctx = Mcp.appeler("lina", "get_project_context", Map.of("project_id", projetP.toString()));
        assertTrue(((String) ctx.path("result.content[0].text")).startsWith("INTROUVABLE"), ctx.asString());
        assertAucuneInformationSurP(ctx.asString());

        ExtractableResponse<Response> tous = Mcp.appeler("lina", "get_project_context", Map.of());
        assertEquals(0, (int) tous.path("result.structuredContent.nombre"), tous.asString());

        ExtractableResponse<Response> explication = Mcp.appeler("lina", "explain_change", Map.of("signal_id", signal.toString()));
        assertEquals(0, (int) explication.path("result.structuredContent.impactsDansVotrePerimetre"), explication.asString());
        assertAucuneInformationSurP(explication.asString());
        assertEquals(1, (int) Mcp.appeler("marc", "explain_change", Map.of("signal_id", signal.toString()))
                .path("result.structuredContent.impactsDansVotrePerimetre"));
    }

    /** Un projet inexistant reçoit la même réponse, sans entrée au journal des refus. */
    @Test
    void projetInexistant() {
        ExtractableResponse<Response> r = Mcp.appeler("lina", "get_security_alerts", Map.of("project_id", UUID.randomUUID().toString()));
        assertTrue(((String) r.path("result.content[0].text")).startsWith("INTROUVABLE"), r.asString());
        assertEquals(0, db.count("SELECT count(*) FROM refus_acces"));
    }

    /**
     * Défense en profondeur (ADR-0016) : sous le rôle {@code argos_app}, même avec un filtre d'espaces
     * forgé, les requêtes utilisées par le serveur MCP ne renvoient rien d'un espace dont le compte
     * n'est pas membre.
     */
    @Test
    void rlsSousLeRoleApplicatif() {
        UUID lina = Api.compte("lina");
        assertTrue(impacts.forWorkspaces(lina, Set.of(espaceB), 50).isEmpty());
        assertTrue(impacts.forProject(projetP, lina, Set.of(espaceB)).isEmpty());
        assertFalse(inventaireQueries.visibleProjects(lina).stream().anyMatch(p -> p.id().equals(projetP)));
        assertTrue(inventaireQueries.inventory(projetP, lina).isEmpty());

        UUID marc = Api.compte("marc");
        assertEquals(1, impacts.forWorkspaces(marc, Set.of(espaceB), 50).size());
        assertTrue(inventaireQueries.visibleProjects(marc).stream().anyMatch(p -> p.id().equals(projetP)));
        assertFalse(espaceA.equals(espaceB));
    }

    private void assertAucuneInformationSurP(String reponse) {
        assertFalse(reponse.contains("Pluton"), reponse);
        assertFalse(reponse.contains(projetP.toString()), reponse);
        assertFalse(reponse.contains(LIB), reponse);
        assertFalse(reponse.contains(espaceB.toString()), reponse);
    }
}
