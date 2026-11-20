package io.argos.projectintel;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Parcours de bout en bout (tâche 4.2) : projet relié à un dépôt GitHub de test (serveur HTTP local),
 * collecte planifiée, indicateurs sur 30 jours par l'API avec un jeton Keycloak réel, jalon
 * « 27 sur 30 (90 %) », synthèse marquée « produite par l'IA » et aucun indicateur par personne.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ParcoursActiviteTest {

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projets;

    @Inject
    CollecteActivite collecte;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    @Test
    void parcoursActiviteDUnProjet() {
        UUID espace = Api.nouvelEspace("alice", "Équipe parcours activité");
        Api.ajouterMembre("alice", espace.toString(), "jules", "LECTEUR");
        ActiviteProjetsTest.depotGithub("demo/argos");
        UUID projet = projets.create(espace, "démo", new RepositoryRef("GITHUB", "demo/argos", "main", null, null), List.of());

        // Passage du planificateur : le projet jamais collecté est échu.
        assertEquals(new CollecteActivite.Bilan(1, 0, 0), collecte.collecter(Instant.now()));

        String activite = Api.en("jules").queryParam("jours", 30).when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200)
                .body("periode.jours", equalTo(30))
                .body("compteurs.ticketsOuverts", equalTo(3))
                .body("compteurs.ticketsFermes", equalTo(2))
                .body("compteurs.fusionsFusionnees", equalTo(1))
                .body("compteurs.pipelinesReussis", equalTo(1))
                .body("compteurs.versionsPubliees", equalTo(1))
                .body("jalons", hasSize(1))
                .body("jalons[0].avancement.libelle", equalTo("27 sur 30 (90 %)"))
                .body("fraicheur.etat", equalTo("A_JOUR"))
                .extract().asString();
        String synthese = Api.en("jules").when().get("/api/v1/projects/" + projet + "/activite/synthese")
                .then().statusCode(200)
                .body("etat", equalTo("DISPONIBLE"))
                .body("mention", equalTo("produite par l'IA"))
                .body("texte", containsString("27 sur 30 (90 %)"))
                .extract().asString();
        assertFalse((activite + synthese).contains("bob") || (activite + synthese).contains("carol"),
                "aucun indicateur ni aucune identité de personne");
        System.out.printf("[parcours activite-projets] activité : %s%n[parcours activite-projets] synthèse : %s%n",
                activite, synthese);
    }
}
