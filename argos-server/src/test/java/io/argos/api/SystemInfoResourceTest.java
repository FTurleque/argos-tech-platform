package io.argos.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Vérifie que le socle démarre (base + migrations) et répond sur l'API v1. */
@QuarkusTest
class SystemInfoResourceTest {

    @Test
    void exposeLIdentiteDuServeur() {
        given()
            .when().get("/api/v1/system/info")
            .then()
            .statusCode(200)
            .body("application", equalTo("argos-server"))
            .body("apiVersion", equalTo("v1"));
    }

    @Test
    void santeOperationnelle() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
}
