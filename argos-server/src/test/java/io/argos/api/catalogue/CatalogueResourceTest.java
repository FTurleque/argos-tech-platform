package io.argos.api.catalogue;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Catalogue publié par l'API : réservé aux utilisateurs authentifiés. Le contenu publié (PRF-03-S1, AFF-01) est
 * vérifié par {@code CatalogueProfilsTest} et {@code CatalogueMisesEnPageTest}.
 */
@QuarkusTest
class CatalogueResourceTest {

    @Test
    void leCatalogueExigeUnUtilisateurAuthentifie() {
        given().when().get("/api/v1/catalogue/profils").then().statusCode(401);
        given().when().get("/api/v1/catalogue/mises-en-page").then().statusCode(401);
    }
}
