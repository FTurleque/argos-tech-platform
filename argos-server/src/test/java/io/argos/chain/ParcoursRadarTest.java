package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.argos.alerting.RappelsRadar;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.RadarFixtures.Cycle;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Parcours de bout en bout (tâche 4.2) : un administrateur construit le référentiel de son espace, un
 * projet utilise une version qui atteint aujourd'hui sa fin de support, la tâche quotidienne crée
 * l'impact « dette de version » et le rappel J0, et un membre retrouve l'échéance dans le radar, la
 * dette et le rappel dans ses alertes. Seul le calendrier endoflife.date est simulé (serveur HTTP local).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ParcoursRadarTest {

    private static final String POSTGRES = "pkg:generic/postgresql";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projets;

    @Inject
    InventoryService inventaires;

    @Inject
    RappelsRadar rappels;

    @Test
    void referentielInventaireDetteRappelEtRadar() {
        baseDeTest.reset();
        LocalDate aujourdhui = LocalDate.now();
        UUID espace = Api.nouvelEspace("lina", "Parcours radar");
        Api.ajouterMembre("lina", espace.toString(), "karim", "LECTEUR");
        String base = "/api/v1/radar/espaces/" + espace;

        // 1. Référentiel : PostgreSQL en ADOPT 17, relié au produit endoflife.date « postgresql ».
        Api.enTantQue("lina").contentType(ContentType.JSON).body(Map.of("cle", POSTGRES, "libelle", "PostgreSQL",
                "statut", "ADOPT", "versionRecommandee", "17", "responsable", "DBA",
                "justification", "Version supportée la plus récente", "dateRevue", "2027-06-30",
                "produitCycleVie", "postgresql")).put(base + "/standards").then().statusCode(200);

        // 2. Inventaire : Portail agent utilise PostgreSQL 14, dont la fin de support tombe aujourd'hui.
        UUID portail = projets.create(espace, "Portail agent", null, null);
        inventaires.importSbom(portail, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", POSTGRES + "@14.10"}), List.of("c0")).getBytes(StandardCharsets.UTF_8),
                "ci");
        RadarFixtures.publier("postgresql", RadarFixtures.reponse("postgresql",
                new Cycle("14", LocalDate.of(2021, 9, 30), aujourdhui, true, "14.25", LocalDate.of(2026, 11, 9)),
                new Cycle("17", LocalDate.of(2024, 9, 26), LocalDate.of(2029, 11, 8), false, "17.12", LocalDate.of(2026, 11, 9))));

        // 3. Tâche quotidienne : synchronisation, dette de version, rappel J0.
        RappelsRadar.Bilan bilan = rappels.executer(aujourdhui, Instant.now());
        assertEquals(Map.of("postgresql", "A_JOUR"), bilan.radar().produits());
        assertEquals(1, bilan.radar().dettesCreees());
        assertEquals(2, bilan.rappels().size());

        // 4. Restitution au membre lecteur : radar, dette, rappel.
        Api.enTantQue("karim").queryParam("horizon", "30j").get(base + "/echeances").then().statusCode(200)
                .body("echeances", hasSize(1))
                .body("echeances[0].nature", equalTo("FIN_DE_SUPPORT"))
                .body("echeances[0].version", equalTo("14"))
                .body("echeances[0].date", equalTo(aujourdhui.toString()));
        Api.enTantQue("karim").get(base + "/dettes").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].projet", equalTo("Portail agent"))
                .body("[0].versionStandard", equalTo("17"));
        Api.enTantQue("karim").get("/api/v1/alertes").then().statusCode(200)
                .body("findAll { it.nature == 'RAPPEL' }", hasSize(1))
                .body("find { it.nature == 'RAPPEL' }.titre",
                        equalTo("Fin de support de PostgreSQL 14 aujourd'hui (" + aujourdhui + ")"))
                .body("find { it.nature == 'RAPPEL' }.etat", equalTo("ENVOYEE"));
    }
}
