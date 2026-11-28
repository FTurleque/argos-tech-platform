package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.alerting.MoteurAlertes;
import io.argos.impact.CorrelationEngine;
import io.argos.impact.ImpactsRecorded;
import io.argos.impact.MatchRules;
import io.argos.impact.RadarProjets;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Asynchrone;
import io.argos.testsupport.CompteurInstructions;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.RadarFixtures.Cycle;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Nombre d'instructions SQL indépendant du volume (critère de sortie du sprint 10) : corrélation d'un projet
 * (COR-13-S1), évaluation des alertes d'un signal (ALR-10-S1), dettes de version et échéances de rappel du radar
 * (RAD-10-S1, RAD-10-S2). Chaque test mesure deux volumes différents avec {@link CompteurInstructions} et compare.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class RequetesConstantesTest {

    private static final String PAQUET = "pkg:maven/org.volume/lib-";
    private static final String LIB = "pkg:maven/org.exemple/lib";
    private static final String POSTGRES = "pkg:generic/postgresql";
    private static final int PETIT = 2;
    private static final int GRAND = 20;
    // dave reste sans compte : AuthentificationMcpTest vérifie le refus d'une personne non inscrite.
    private static final List<String> MEMBRES_DU_GRAND_ESPACE = List.of("erin", "frank", "gina", "hugo", "ines",
            "jules", "karim", "lina", "marc", "nina");

    @Inject
    TestDb baseDeTest;

    @Inject
    Asynchrone asynchrone;

    @Inject
    ProjectDirectory projets;

    @Inject
    InventoryService inventaires;

    @Inject
    SourceSynchronizer synchroniseur;

    @Inject
    CorrelationEngine correlation;

    @Inject
    MoteurAlertes moteur;

    @Inject
    RadarProjets radar;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
    }

    private static int compterInstructions(IntSupplier traitementMesure) {
        try (CompteurInstructions compteur = CompteurInstructions.demarrer()) {
            traitementMesure.getAsInt();
            return compteur.nombre();
        }
    }

    private UUID projet(UUID espace, String nom, List<String> purls) {
        UUID projet = projets.create(espace, nom, null, null);
        List<String[]> composants = new ArrayList<>();
        for (int rang = 0; rang < purls.size(); rang++) {
            composants.add(new String[] {"c" + rang, purls.get(rang)});
        }
        inventaires.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                composants, List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        asynchrone.attendre();
        return projet;
    }

    /** Publie un avis par paquet {@code lib-<n>}, pour n de 1 à {@code nombre}, en une synchronisation. */
    private void publierAvis(int nombre) {
        Map<String, String> avis = new LinkedHashMap<>();
        Map<String, String> modifies = new LinkedHashMap<>();
        for (int rang = 1; rang <= nombre; rang++) {
            String id = "GHSA-VOL-" + rang;
            String modifie = "2026-11-10T00:00:" + String.format("%02d", rang % 60) + "Z";
            avis.put(id, Fixtures.osv(id, modifie, "Maven", "org.volume:lib-" + rang, PAQUET + rang, "0", "2.0.0"));
            modifies.put(id, modifie);
        }
        Fixtures.stubOsv(avis, modifies);
        synchroniseur.synchronize("OSV");
        asynchrone.attendre();
    }

    private static List<String> composants(int nombre) {
        List<String> purls = new ArrayList<>();
        for (int rang = 1; rang <= nombre; rang++) {
            purls.add(PAQUET + rang + "@1.0.0");
        }
        return purls;
    }

    /** COR-13-S1 : un projet de 20 signaux et 20 composants se recorrèle avec autant d'instructions qu'un projet de 2. */
    @Test
    void cor13S1CorrelationEnNombreFixeDInstructions() {
        UUID espace = Api.nouvelEspace("alice", "Volumes de corrélation");
        UUID petit = projet(espace, "petit", composants(PETIT));
        UUID grand = projet(espace, "grand", composants(GRAND));
        publierAvis(GRAND);
        assertEquals(PETIT + GRAND, baseDeTest.count("SELECT count(*) FROM project_impact"));

        int instructionsPetit = compterInstructions(() -> correlation.correlateProject(petit).size());
        int instructionsGrand = compterInstructions(() -> correlation.correlateProject(grand).size());

        assertTrue(instructionsPetit > 0, "le compteur voit les instructions du fil courant");
        assertEquals(instructionsPetit, instructionsGrand, "COR-13-S1 : même nombre d'instructions pour 2 et 20 signaux");
    }

    private static void regle(String utilisateur, String canal, String frequence) {
        Api.enTantQue(utilisateur).contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"PACKAGE\",\"value\":\"" + LIB + "\"}],\"channel\":\""
                        + canal + "\",\"frequency\":\"" + frequence + "\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);
    }

    private void membreAvecRegles(UUID espace, String administrateur, String membre) {
        if (!membre.equals(administrateur)) {
            Api.ajouterMembre(administrateur, espace.toString(), membre, "LECTEUR");
        }
        regle(membre, "email", "immediate");
        regle(membre, "desktop", "daily");
    }

    private ImpactsRecorded impactsDe(UUID signal, UUID projet) {
        List<ImpactsRecorded.Item> items = new ArrayList<>();
        UUID impact = UUID.fromString(baseDeTest.string("SELECT id::text FROM project_impact WHERE project_id = ?", projet));
        UUID espace = UUID.fromString(baseDeTest.string("SELECT workspace_id::text FROM project_impact WHERE id = ?", impact));
        items.add(new ImpactsRecorded.Item(impact, projet, espace, MatchRules.Level.EXACT, LIB));
        return new ImpactsRecorded(signal, "Faille de volume", "CRITICAL", "OSV", "GHSA-VOL-ALERTES", items);
    }

    /** ALR-10-S1 : les règles de 11 membres s'évaluent avec autant de requêtes que celles de 2 membres. */
    @Test
    void alr09S1EvaluationEnNombreFixeDeRequetes() {
        UUID espacePetit = Api.nouvelEspace("alice", "Petit espace d'alertes");
        membreAvecRegles(espacePetit, "alice", "alice");
        membreAvecRegles(espacePetit, "alice", "bob");
        UUID espaceGrand = Api.nouvelEspace("carla", "Grand espace d'alertes");
        membreAvecRegles(espaceGrand, "carla", "carla");
        MEMBRES_DU_GRAND_ESPACE.forEach(membre -> membreAvecRegles(espaceGrand, "carla", membre));
        UUID projetPetit = projet(espacePetit, "petit", List.of(LIB + "@1.2.0"));
        UUID projetGrand = projet(espaceGrand, "grand", List.of(LIB + "@1.2.0"));
        String json = Fixtures.osv("GHSA-VOL-ALERTES", "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.3.0")
                .replace("\"severity\":\"HIGH\"", "\"severity\":\"CRITICAL\"");
        Fixtures.stubOsv("GHSA-VOL-ALERTES", "2026-11-10T00:00:00Z", json);
        synchroniseur.synchronize("OSV");
        asynchrone.attendre();
        UUID signal = baseDeTest.uuid("SELECT id FROM signal WHERE external_id = 'GHSA-VOL-ALERTES'");
        assertEquals(PETIT + 1 + MEMBRES_DU_GRAND_ESPACE.size(), baseDeTest.count(
                "SELECT count(*) FROM alerte WHERE nature = 'IMMEDIATE' AND signal_id = ?", signal),
                "une alerte immédiate par membre");

        int requetesPetit = compterInstructions(() -> moteur.evaluer(impactsDe(signal, projetPetit), Instant.now()).alertes().size());
        int requetesGrand = compterInstructions(() -> moteur.evaluer(impactsDe(signal, projetGrand), Instant.now()).alertes().size());

        assertTrue(requetesPetit > 0, "le compteur voit les requêtes du fil courant");
        assertEquals(requetesPetit, requetesGrand, "ALR-10-S1 : même nombre de requêtes pour 2 et 11 membres");
    }

    private static List<String> versionsPostgres(int nombre) {
        List<String> purls = new ArrayList<>();
        for (int rang = 1; rang <= nombre; rang++) {
            purls.add(POSTGRES + "@14." + rang);
        }
        return purls;
    }

    /**
     * RAD-10-S1 et S2 : les dettes de version d'un espace de 30 composants s'enregistrent avec autant de requêtes que
     * celles d'un espace de 3 composants ; les échéances de trois jours de rappel se lisent avec autant de requêtes
     * que celles d'un seul jour.
     */
    @Test
    void rad08RadarEnNombreFixeDeRequetes() {
        LocalDate finDeSupport = LocalDate.of(2026, 11, 12);
        RadarFixtures.publier("postgresql", RadarFixtures.reponse("postgresql",
                new Cycle("14", LocalDate.of(2021, 9, 30), finDeSupport, true, "14.25", LocalDate.of(2026, 11, 9))));
        UUID espacePetit = Api.nouvelEspace("alice", "Petit radar");
        projet(espacePetit, "petit", versionsPostgres(3));
        RadarFixtures.standard(baseDeTest, espacePetit, POSTGRES, "PostgreSQL", "ADOPT", "17", "Version courante", "postgresql");
        radar.actualiser(finDeSupport.minusDays(1), Instant.now());

        int requetesPetit = compterInstructions(() -> radar.enregistrerDettes(finDeSupport, Instant.now()));
        assertEquals(3, baseDeTest.count("SELECT count(*) FROM dette_version"), "RAD-10-S1 : les 3 composants du petit espace");

        baseDeTest.exec("DELETE FROM standard_technologie WHERE espace_id = ?", espacePetit);
        UUID espaceGrand = Api.nouvelEspace("carla", "Grand radar");
        projet(espaceGrand, "grand", versionsPostgres(30));
        RadarFixtures.standard(baseDeTest, espaceGrand, POSTGRES, "PostgreSQL", "ADOPT", "17", "Version courante", "postgresql");
        int requetesGrand = compterInstructions(() -> radar.enregistrerDettes(finDeSupport, Instant.now()));

        assertEquals(33, baseDeTest.count("SELECT count(*) FROM dette_version"));
        assertEquals(requetesPetit, requetesGrand, "RAD-10-S1 : même nombre de requêtes pour 3 et 30 composants");

        LocalDate aujourdhui = finDeSupport.minusDays(30);
        int requetesUnJour = compterInstructions(() -> radar.echeancesTouchant(espaceGrand, List.of(aujourdhui.plusDays(30))).size());
        int requetesTroisJours = compterInstructions(() -> radar.echeancesTouchant(espaceGrand,
                List.of(aujourdhui.plusDays(30), aujourdhui.plusDays(7), aujourdhui.plusDays(1))).size());
        assertEquals(requetesUnJour, requetesTroisJours, "RAD-10-S2 : une seule lecture pour plusieurs jours de rappel");
        assertEquals(1, radar.echeancesTouchant(espaceGrand, List.of(finDeSupport)).get(finDeSupport).size());
    }
}
