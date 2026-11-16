package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Outils de consultation (tâches 3.1 et 3.2) : {@code check_dependency} (MCP-02), puis contrat de
 * comportement de {@code search_signals}, {@code get_technology_standard}, {@code upcoming_releases}
 * et {@code get_project_context}. Le référentiel des standards et le calendrier sont réels
 * (changement {@code construire-radar-et-standards}) : entrées du référentiel et cycles endoflife.date
 * insérés en base ({@link RadarFixtures}).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class OutilsConsultationMcpTest {

    private static final String BIBLIO = "pkg:maven/org.exemple/biblio";

    @Inject
    TestDb db;

    @Inject
    SourceSynchronizer synchronisation;

    @Inject
    ProjectDirectory projets;

    @Inject
    InventoryService inventaires;

    private UUID espace;
    private UUID espaceAutre;

    @BeforeEach
    void preparer() {
        db.reset();
        espace = Api.nouvelEspace("oscar", "Espace d'Oscar");
        espaceAutre = Api.nouvelEspace("marc", "Espace voisin");
    }

    private void publier(String id, String introduit, String corrige) {
        String json = Fixtures.osv(id, "2026-11-10T00:00:00Z", "Maven", "org.exemple:biblio", BIBLIO, introduit, corrige);
        Fixtures.stubOsv(id, "2026-11-10T00:00:00Z", json);
        synchronisation.synchronize("OSV");
    }

    private static ExtractableResponse<Response> verifier(String version) {
        return Mcp.appeler("oscar", "check_dependency", Map.of("ecosystem", "Maven", "name", "org.exemple:biblio", "version", version));
    }

    /** MCP-02-S1 : faille sur la 3.2, standard 3.4.1 : faille, sources, statut et version recommandée. */
    @Test
    void mcp02S1VersionVulnerable() {
        publier("GHSA-MCP2-0032", "0", "3.3.0");
        RadarFixtures.standard(db, espace, BIBLIO, "Biblio", "ADOPT", "3.4.1", "Version validée par l'équipe socle.", "biblio");
        RadarFixtures.standard(db, espaceAutre, BIBLIO, "Biblio", "HOLD", "9.9.9", "Standard d'un autre espace.", "biblio-autre");
        cycle("biblio", "3.2", null, LocalDate.of(2026, 12, 31), null, null);
        cycle("biblio-autre", "3", null, LocalDate.of(2020, 1, 1), null, null);

        ExtractableResponse<Response> r = verifier("3.2");
        assertEquals(Boolean.FALSE, r.path("result.isError"), r.asString());
        String base = "result.structuredContent.";
        assertEquals(BIBLIO, r.path(base + "cle"));
        assertEquals(Boolean.TRUE, r.path(base + "connue"));
        assertEquals(1, (int) r.path(base + "failles.size()"));
        assertEquals("GHSA-MCP2-0032", r.path(base + "failles[0].identifiant"));
        assertEquals("OSV", r.path(base + "failles[0].source"));
        assertEquals("AFFECTEE", r.path(base + "failles[0].correspondance"));
        assertEquals("OSV", r.path(base + "failles[0].references[0].source"));
        assertEquals("GHSA-MCP2-0032", r.path(base + "failles[0].references[0].identifiant"));
        assertEquals(TexteExterne.NATURE, r.path(base + "failles[0].titre.nature"));
        assertEquals(1, (int) r.path(base + "referentiel.size()"));
        assertEquals("ADOPT", r.path(base + "referentiel[0].statut"));
        assertEquals("3.4.1", r.path(base + "versionRecommandee"));
        assertEquals("2026-12-31", r.path(base + "finDeSupport.date"));
        assertFalse(r.asString().contains("9.9.9"), "standard d'un autre espace");
        assertFalse(r.asString().contains("2020-01-01"), "correspondance de calendrier d'un autre espace");

        // La version recommandée n'est affectée par aucune faille connue.
        ExtractableResponse<Response> recommandee = verifier("3.4.1");
        assertEquals(0, (int) recommandee.path(base + "failles.size()"), recommandee.asString());
        assertEquals(Boolean.TRUE, recommandee.path(base + "connue"));
    }

    /** MCP-02-S2 : composant inconnu : aucune information, rien d'inventé. */
    @Test
    void mcp02S2DependanceInconnue() {
        publier("GHSA-MCP2-AUTRE", "0", "3.3.0");
        ExtractableResponse<Response> r = Mcp.appeler("oscar", "check_dependency",
                Map.of("ecosystem", "npm", "name", "paquet-inconnu-d-argos", "version", "1.0.0"));
        String base = "result.structuredContent.";
        assertEquals(Boolean.FALSE, r.path("result.isError"), r.asString());
        assertEquals(Boolean.FALSE, r.path(base + "connue"));
        assertEquals(0, (int) r.path(base + "failles.size()"));
        assertEquals(0, (int) r.path(base + "referentiel.size()"));
        assertNull(r.path(base + "versionRecommandee"));
        assertNull(r.path(base + "finDeSupport"));
        assertTrue(((String) r.path(base + "constat")).startsWith("Aucune information connue"));
    }

    /** Paramètres invalides : erreur explicite, sans appel métier, journalisée comme telle. */
    @Test
    void parametresInvalides() {
        ExtractableResponse<Response> r = Mcp.appeler("oscar", "check_dependency",
                Map.of("ecosystem", "Inconnu", "name", "x", "version", "1"));
        assertEquals(Boolean.TRUE, r.path("result.isError"), r.asString());
        assertTrue(((String) r.path("result.content[0].text")).startsWith("PARAMETRES_INVALIDES"));
        ExtractableResponse<Response> s = Mcp.appeler("oscar", "search_signals", Map.of());
        assertTrue(((String) s.path("result.content[0].text")).startsWith("PARAMETRES_INVALIDES"), s.asString());
        assertEquals(2, db.count("SELECT count(*) FROM mcp_appel WHERE issue = 'PARAMETRES_INVALIDES'"));
    }

    /** {@code search_signals} : signaux correspondant aux critères, contenu externe balisé. */
    @Test
    void searchSignals() {
        publier("GHSA-MCP-RECH", "0", "3.3.0");
        ExtractableResponse<Response> r = Mcp.appeler("oscar", "search_signals", Map.of("packages", List.of(BIBLIO)));
        String base = "result.structuredContent.";
        assertEquals(1, (int) r.path(base + "nombre"), r.asString());
        assertEquals("GHSA-MCP-RECH", r.path(base + "signaux[0].identifiant"));
        assertEquals(List.of(BIBLIO), r.path(base + "signaux[0].paquets"));
        assertEquals(TexteExterne.NATURE, r.path(base + "signaux[0].titre.nature"));
        assertEquals("Vulnérabilité de test GHSA-MCP-RECH", r.path(base + "signaux[0].titre.texte"));
        assertEquals(0, (int) Mcp.appeler("oscar", "search_signals", Map.of("technologies", List.of("rust")))
                .path(base + "nombre"));
    }

    /** {@code get_technology_standard} : standards des seuls espaces de l'appelant ; technologie inconnue signalée. */
    @Test
    void getTechnologyStandard() {
        RadarFixtures.standard(db, espace, BIBLIO, "Biblio", "TRIAL", "3.4.1", "Projet pilote", null);
        RadarFixtures.standard(db, espaceAutre, BIBLIO, "Biblio", "HOLD", null, "Autre espace", null);
        ExtractableResponse<Response> r = Mcp.appeler("oscar", "get_technology_standard", Map.of("name", BIBLIO));
        String base = "result.structuredContent.";
        assertEquals(Boolean.TRUE, r.path(base + "connu"), r.asString());
        assertEquals(1, (int) r.path(base + "standards.size()"));
        assertEquals("TRIAL", r.path(base + "standards[0].statut"));
        assertFalse(r.asString().contains("Autre espace"));

        ExtractableResponse<Response> inconnu = Mcp.appeler("oscar", "get_technology_standard",
                Map.of("ecosystem", "npm", "name", "inconnu"));
        assertEquals(Boolean.FALSE, inconnu.path(base + "connu"), inconnu.asString());
    }

    /** {@code upcoming_releases} : échéances d'une technologie, ou des composants des projets de l'appelant. */
    @Test
    void upcomingReleases() {
        UUID projet = projets.create(espace, "app-oscar", null, null);
        inventaires.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", BIBLIO + "@3.2"}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        LocalDate dans10Jours = LocalDate.now().plusDays(10);
        RadarFixtures.standard(db, espace, BIBLIO, "Biblio", "ADOPT", "3.2.9", "Version courante", "biblio");
        cycle("biblio", "3.2", LocalDate.of(2020, 1, 1), dans10Jours, "3.2.9", LocalDate.now().minusDays(5));
        cycle("biblio", "4.0", LocalDate.now().plusDays(400), null, "4.0.1", LocalDate.now().plusDays(450));

        ExtractableResponse<Response> r = Mcp.appeler("oscar", "upcoming_releases", Map.of());
        String base = "result.structuredContent.";
        assertEquals(1, (int) r.path(base + "nombre"), r.asString());
        assertEquals("FIN_DE_SUPPORT", r.path(base + "echeances[0].nature"));
        assertEquals(dans10Jours.toString(), r.path(base + "echeances[0].date"));
        assertNull(r.path(base + "echeances[0].note"));

        ExtractableResponse<Response> long_ = Mcp.appeler("oscar", "upcoming_releases",
                Map.of("name", BIBLIO, "horizon_days", 500));
        assertEquals(3, (int) long_.path(base + "nombre"), long_.asString());
        assertEquals("SORTIE", long_.path(base + "echeances[1].nature"));
        assertEquals("CORRECTIF", long_.path(base + "echeances[2].nature"));
        assertEquals("4.0.1", long_.path(base + "echeances[2].version"));
        assertEquals(TexteExterne.NATURE, long_.path(base + "echeances[2].note.nature"));
        ExtractableResponse<Response> horsBornes = Mcp.appeler("oscar", "upcoming_releases", Map.of("horizon_days", 5000));
        assertTrue(((String) horsBornes.path("result.content[0].text")).startsWith("PARAMETRES_INVALIDES"));
    }

    /** Cycle endoflife.date inséré dans la copie locale du calendrier. */
    private void cycle(String produit, String nom, LocalDate sortie, LocalDate fin, String derniere, LocalDate derniereLe) {
        db.exec("INSERT INTO cycle_vie_produit (produit, etat, derniere_synchro_le, dernier_succes_le) VALUES ('" + produit
                + "', 'A_JOUR', now(), now()) ON CONFLICT DO NOTHING");
        db.exec("INSERT INTO cycle_vie (produit, cycle, sortie_le, fin_support_le, en_fin_support, derniere_version,"
                + " derniere_version_le) VALUES ('" + produit + "', '" + nom + "', " + date(sortie) + ", " + date(fin)
                + ", false, " + (derniere == null ? "NULL" : "'" + derniere + "'") + ", " + date(derniereLe) + ")");
    }

    private static String date(LocalDate d) {
        return d == null ? "NULL" : "DATE '" + d + "'";
    }

    /** {@code get_project_context} : inventaire, alertes par niveau et composants d'un projet de l'appelant. */
    @Test
    void getProjectContext() {
        UUID projet = projets.create(espace, "app-contexte", null, null);
        inventaires.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", BIBLIO + "@3.2"}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        publier("GHSA-MCP-CTX", "0", "3.3.0");

        ExtractableResponse<Response> r = Mcp.appeler("oscar", "get_project_context", Map.of("project_id", projet.toString()));
        String base = "result.structuredContent.projets[0].";
        assertEquals("app-contexte", r.path(base + "projet"), r.asString());
        assertEquals("SBOM", r.path(base + "inventaire.origine"));
        assertEquals(1, (int) r.path(base + "inventaire.nombreComposants"));
        assertEquals(1, (int) r.path(base + "alertesParNiveau.EXACT"));
        assertEquals(BIBLIO + "@3.2", r.path(base + "composants[0].purl"));

        ExtractableResponse<Response> resume = Mcp.appeler("oscar", "get_project_context", Map.of());
        assertEquals(1, (int) resume.path("result.structuredContent.nombre"), resume.asString());
        assertEquals(0, (int) resume.path("result.structuredContent.projets[0].composants.size()"));
    }
}
