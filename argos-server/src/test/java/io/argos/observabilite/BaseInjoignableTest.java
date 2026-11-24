package io.argos.observabilite;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.argos.aigateway.SantePasserelleIa;
import io.argos.ingestion.SanteSources;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * EXP-15-S4 : une base de données injoignable rend le serveur indisponible ({@code /q/health/ready} répond 503 et le
 * contrôle des connexions est {@code DOWN}), alors que les contrôles des dépendances facultatives restent {@code UP}
 * (INV-3). La base injoignable est une source de données nommée qui vise un port fermé : le contrôle des sources de
 * données de Quarkus la vérifie comme la base principale.
 */
@QuarkusTest
@TestProfile(BaseInjoignableTest.AvecBaseInjoignable.class)
class BaseInjoignableTest {

    /** Source de données supplémentaire sur un port où aucune base n'écoute. */
    public static class AvecBaseInjoignable implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.datasource.injoignable.db-kind", "postgresql",
                    "quarkus.datasource.injoignable.jdbc.url", "jdbc:postgresql://127.0.0.1:1/injoignable",
                    "quarkus.datasource.injoignable.username", "injoignable",
                    "quarkus.datasource.injoignable.password", "injoignable",
                    "quarkus.datasource.injoignable.devservices.enabled", "false",
                    "quarkus.datasource.injoignable.jdbc.acquisition-timeout", "PT2S");
        }
    }

    @Test
    void exp15S4BaseInjoignableServeurIndisponible() {
        given().when().get("/q/health/ready").then().statusCode(503)
                .body("status", equalTo("DOWN"))
                .body("checks.status", hasItem("DOWN"))
                .body("checks.find { it.name == '" + SanteSources.NOM + "' }.status", equalTo("UP"))
                .body("checks.find { it.name == '" + SantePasserelleIa.NOM + "' }.status", equalTo("UP"));
    }
}
