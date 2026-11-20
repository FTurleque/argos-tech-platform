package io.argos.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.StrategieDeclaree;
import io.argos.impact.MatchRules;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.SimulatedAnalysis;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Profils de veille et chaîne sécurité intégrées (personnaliser-profils-et-affichage, tâches 3.3 et 3.6) :
 * la stratégie du profil {@code developpeur} est exécutée par le moteur d'impact sans IA (PRF-03-S2), et
 * activer un profil n'élargit jamais les données visibles (PRF-05-S1). Jetons réels, RLS active.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ProfilsDeVeilleTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";

    @Inject
    TestDb baseDeTest;

    @Inject
    CatalogueProfils catalogue;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    SimulatedAnalysis analysis;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = '" + Api.compte("ines") + "'");
    }

    private UUID projetAvecSbom(UUID espace, String nom, String purl) {
        UUID projet = projects.create(espace, nom, null, null);
        inventories.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"c", purl}), List.of("c")).getBytes(StandardCharsets.UTF_8), "ci");
        return projet;
    }

    private UUID publier(String id) {
        Fixtures.stubOsv(id, "2026-11-10T00:00:00Z",
                Fixtures.osv(id, "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.3.0"));
        synchronizer.synchronize("OSV");
        return baseDeTest.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private static void activer(String utilisateur, String profilsJson) {
        Api.enTantQue(utilisateur).contentType(ContentType.JSON).body("{\"profilsActifs\":" + profilsJson + "}")
                .when().put("/api/v1/preferences/profils").then().statusCode(200);
    }

    /** Chaque stratégie déclarée par le profil developpeur est exécutée par le moteur d'impact. */
    @Test
    void lesStrategiesDuProfilDeveloppeurSontImplementeesParLeMoteurDImpact() {
        List<String> strategies = catalogue.trouver("developpeur").orElseThrow().strategies().stream()
                .map(StrategieDeclaree::identifiant).toList();
        assertEquals(List.of(MatchRules.STRATEGY), strategies);
        assertTrue(MatchRules.IMPLEMENTED_STRATEGIES.containsAll(strategies));
    }

    /** PRF-03-S2 : avec le seul profil developpeur, l'impact est restitué avec un niveau calculé sans IA. */
    @Test
    void prf03S2RapprochementPropreAuProfilSansIa() {
        analysis.configure(SimulatedAnalysis.Mode.UNAVAILABLE, List.of());
        UUID espace = Api.nouvelEspace("ines", "Projets d'Ines");
        UUID projet = projetAvecSbom(espace, "app", LIB + "@1.2.0");
        activer("ines", "[\"developpeur\"]");

        UUID signal = publier("GHSA-PRF03");

        Api.enTantQue("ines").when().get("/api/v1/signals/" + signal + "/impacts").then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].projectId", equalTo(projet.toString()))
                .body("items[0].level", equalTo("EXACT"))
                .body("items[0].method", equalTo(MatchRules.METHOD))
                .body("items[0].provenance", containsString(MatchRules.STRATEGY));
    }

    /** PRF-05-S1 : activer un profil ne restitue aucun impact d'un espace non autorisé et ne change aucun rôle. */
    @Test
    void prf05S1ActivationDUnProfilSansDroitSurLesProjets() {
        UUID espaceA = Api.nouvelEspace("ines", "Espace A d'Ines");
        UUID espaceB = Api.nouvelEspace("jules", "Espace B de Jules");
        UUID projetA = projetAvecSbom(espaceA, "a", LIB + "@1.2.0");
        UUID projetB = projetAvecSbom(espaceB, "b", LIB + "@1.1.0");
        UUID signal = publier("GHSA-PRF05");
        UUID impactB = baseDeTest.uuid("SELECT id FROM project_impact WHERE project_id = '" + projetB + "'");
        Api.enTantQue("ines").contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"PACKAGE\",\"value\":\"" + LIB + "\"},"
                        + "{\"type\":\"PROJECT\",\"value\":\"" + projetB + "\"}],\"channel\":\"in_app\",\"frequency\":\"daily\","
                        + "\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);
        List<String> espacesAvant = Api.enTantQue("ines").when().get("/api/v1/espaces").then().statusCode(200)
                .extract().jsonPath().getList("id");

        for (String profils : List.of("[\"developpeur\"]", "[\"developpeur\",\"expert-metier\"]")) {
            activer("ines", profils);

            Api.enTantQue("ines").when().get("/api/v1/signals/" + signal + "/impacts").then().statusCode(200)
                    .body("items.projectId", not(hasItem(projetB.toString())))
                    .body("items.projectId", hasItem(projetA.toString()));
            Api.enTantQue("ines").when().get("/api/v1/impacts/" + impactB).then().statusCode(404);
            Api.enTantQue("ines").when().get("/api/v1/projects/" + projetB + "/inventory").then().statusCode(404);
            Api.enTantQue("ines").when().get("/api/v1/me/watch").then().statusCode(200)
                    .body("find { it.externalId == 'GHSA-PRF05' }.impacts.projectId", not(hasItem(projetB.toString())));
            assertEquals(espacesAvant, Api.enTantQue("ines").when().get("/api/v1/espaces").then().statusCode(200)
                    .extract().jsonPath().getList("id"), "rôles et appartenances inchangés");
        }
        Api.enTantQue("ines").when().get("/api/v1/espaces/" + espaceB).then().statusCode(404);
    }
}
