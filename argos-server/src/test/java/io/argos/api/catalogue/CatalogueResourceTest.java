package io.argos.api.catalogue;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

/** Catalogue publié par l'API (PRF-03-S1, AFF-01). */
@QuarkusTest
class CatalogueResourceTest {

    @Test
    @TestSecurity(user = "catalogue-lecteur")
    void prf03S1LeCatalogueInitialListeDeveloppeurEtExpertMetier() {
        given()
            .when().get("/api/v1/catalogue/profils")
            .then()
            .statusCode(200)
            .body("identifiant", contains("developpeur", "expert-metier"))
            .body("libelle", everyItem(not(emptyOrNullString())))
            .body("description", everyItem(not(emptyOrNullString())))
            .body("find { it.identifiant == 'expert-metier' }.gardeFous", hasItem(
                    "Une fiche n'est diffusée qu'après validation par l'expert métier référent (ADR-0018)."));
    }

    @Test
    @TestSecurity(user = "catalogue-lecteur")
    void aff01LeCatalogueInitialListeLesQuatreMisesEnPage() {
        given()
            .when().get("/api/v1/catalogue/mises-en-page")
            .then()
            .statusCode(200)
            .body("identifiant", contains("classique", "journal", "fantaisie", "gazette"))
            .body("find { it.parDefaut }.identifiant", equalTo("classique"));
    }

    @Test
    void leCatalogueExigeUnUtilisateurAuthentifie() {
        given().when().get("/api/v1/catalogue/profils").then().statusCode(401);
        given().when().get("/api/v1/catalogue/mises-en-page").then().statusCode(401);
    }
}
