package io.argos.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.JeuRecherche;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

/**
 * Paramètres de pagination non entiers des listes de l'API REST (décision G4 du porteur du projet, 2026-11-19) :
 * pour chaque liste paginée, une valeur non entière de {@code page}, {@code size}, {@code taille} ou {@code limite}
 * est refusée par 400 {@code DONNEE_INVALIDE} avec l'attribut en cause ; les bornes propres à chaque liste sont
 * conservées ; le contrat OpenAPI déclare des entiers et la réponse 400. Les listes de l'inventaire sont couvertes
 * par {@code chain/ConsultationInventaireTest#parametresDePaginationNonEntiers}.
 */
@QuarkusTest
class ParametresPaginationTest {

    @Inject
    TestDb db;

    @Inject
    JeuRecherche jeu;

    private UUID espace;

    @BeforeEach
    void preparer() {
        db.reset();
        espace = Api.nouvelEspace("alice", "Équipe pagination");
    }

    private static RequestSpecification alice() {
        return Api.en("alice");
    }

    private static void refuse(ValidatableResponse reponse, String attribut) {
        reponse.statusCode(400).body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo(attribut));
    }

    @Test
    void impactsDUnSignal() {
        UUID signal = jeu.signal("GHSA-PAGE-0001", "Faille de pagination", "Correctif.", "HIGH", Instant.now(), null);
        String chemin = "/api/v1/signals/" + signal + "/impacts";

        refuse(alice().queryParam("page", "abc").when().get(chemin).then(), "page");
        refuse(alice().queryParam("size", "1.5").when().get(chemin).then(), "size");
        alice().queryParam("page", -2).queryParam("size", 100_000).when().get(chemin)
                .then().statusCode(200).body("page", equalTo(0)).body("size", equalTo(200));
        alice().queryParam("size", "").when().get(chemin).then().statusCode(200).body("size", equalTo(50));
        alice().queryParam("page", "abc").when().get("/api/v1/signals/" + UUID.randomUUID() + "/impacts")
                .then().statusCode(404);
    }

    @Test
    void recherche() {
        refuse(alice().queryParam("page", "abc").when().get("/api/v1/recherche").then(), "page");
        refuse(alice().queryParam("taille", "x").when().get("/api/v1/recherche").then(), "taille");
        // Bornes existantes conservées : taille de 1 à 100, page positive ou nulle.
        alice().queryParam("taille", 101).when().get("/api/v1/recherche")
                .then().statusCode(400).body("code", equalTo("CRITERE_INVALIDE"));
        alice().queryParam("page", -1).when().get("/api/v1/recherche")
                .then().statusCode(400).body("code", equalTo("CRITERE_INVALIDE"));
        alice().queryParam("page", 1).queryParam("taille", 5).when().get("/api/v1/recherche")
                .then().statusCode(200).body("page", equalTo(1)).body("taille", equalTo(5));
        alice().when().get("/api/v1/recherche").then().statusCode(200).body("page", equalTo(0)).body("taille", equalTo(20));
    }

    @Test
    void resultatsDUneVueEnregistree() {
        String vue = alice().contentType(ContentType.JSON)
                .body(Map.of("nom", "Vue paginée", "criteres", Map.of("texte", "faille", "horizonJours", 30)))
                .post("/api/v1/vues").then().statusCode(201).extract().path("id");
        String chemin = "/api/v1/vues/" + vue + "/resultats";

        refuse(alice().queryParam("page", "1e2").when().get(chemin).then(), "page");
        refuse(alice().queryParam("taille", "dix").when().get(chemin).then(), "taille");
        alice().queryParam("taille", 0).when().get(chemin).then().statusCode(400).body("code", equalTo("CRITERE_INVALIDE"));
        alice().queryParam("taille", 7).when().get(chemin).then().statusCode(200).body("taille", equalTo(7));
    }

    @Test
    void alertesDeLAppelant() {
        refuse(alice().queryParam("limite", "abc").when().get("/api/v1/alertes").then(), "limite");
        alice().queryParam("limite", 0).when().get("/api/v1/alertes").then().statusCode(200);
        alice().queryParam("limite", "99999999999").when().get("/api/v1/alertes").then().statusCode(200);
    }

    @Test
    void actualitesWeb() {
        refuse(alice().queryParam("limite", "1.5").when().get("/api/v1/veille-web/actualites").then(), "limite");
        alice().queryParam("limite", 500).queryParam("espaceId", espace.toString())
                .when().get("/api/v1/veille-web/actualites").then().statusCode(200);
    }

    @Test
    void fichesReglementaires() {
        refuse(alice().queryParam("limite", "abc").when().get("/api/v1/reglementaire/fiches").then(), "limite");
        // Borne existante conservée : une limite hors de [1, 200] est refusée.
        alice().queryParam("limite", 0).when().get("/api/v1/reglementaire/fiches")
                .then().statusCode(400).body("code", equalTo("DONNEE_INVALIDE"));
        alice().queryParam("limite", 10).when().get("/api/v1/reglementaire/fiches").then().statusCode(200);
    }

    /** Contrat OpenAPI : chaque paramètre de pagination est un entier, chaque liste déclare la réponse 400. */
    @Test
    void contratOpenApi() {
        ValidatableResponse contrat = alice().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200);
        Map<String, List<String>> listes = Map.of(
                "/api/v1/signals/{id}/impacts", List.of("page", "size"),
                "/api/v1/recherche", List.of("page", "taille"),
                "/api/v1/vues/{id}/resultats", List.of("page", "taille"),
                "/api/v1/alertes", List.of("limite"),
                "/api/v1/veille-web/actualites", List.of("limite"),
                "/api/v1/reglementaire/fiches", List.of("limite"),
                "/api/v1/projects", List.of("page", "size"),
                "/api/v1/projects/{projectId}/inventory/components", List.of("page", "size"));
        listes.forEach((chemin, parametres) -> {
            String operation = "paths.'" + chemin + "'.get";
            contrat.body(operation + ".responses.'400'", notNullValue());
            for (String parametre : parametres) {
                contrat.body(operation + ".parameters.name", hasItem(parametre));
                contrat.body(operation + ".parameters.find { it.name == '" + parametre + "' }.schema.type", equalTo("integer"));
            }
        });
    }
}
