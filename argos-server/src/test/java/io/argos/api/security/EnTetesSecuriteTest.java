package io.argos.api.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.junit.QuarkusTest;

/**
 * ACC-12-S3 et ACC-12-S4 : en-têtes de sécurité de l'IHM servie. L'émetteur public du profil de test est
 * {@code http://idp.test:8443/realms/argos} ; la politique en nomme l'origine, pas l'adresse du royaume.
 */
@QuarkusTest
class EnTetesSecuriteTest {

    private static final String ORIGINE_EMETTEUR_TEST = "http://idp.test:8443";

    /** Page, ressource de build et réponse d'API refusée : tous portent les mêmes en-têtes. */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/assets/test.js", "/api/v1/system/info", "/api/v1/moi"})
    void enTetesPoses(String chemin) {
        given().when().get(chemin)
                .then()
                .header("Content-Security-Policy", equalTo(EnTetesSecurite.politiqueContenu(ORIGINE_EMETTEUR_TEST)))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("Referrer-Policy", equalTo("no-referrer"));
    }

    /** ACC-12-S3 : connexions limitées à l'origine du portail et à celle de l'émetteur, scripts de l'IHM seulement. */
    @Test
    void politiqueNommeLEmetteurConfigure() {
        String politique = given().when().get("/").then().extract().header("Content-Security-Policy");
        assertTrue(politique.contains("connect-src 'self' " + ORIGINE_EMETTEUR_TEST + ";"), politique);
        assertTrue(politique.contains("script-src 'self';"), politique);
        assertTrue(!politique.contains("/realms/"), "l'origine de l'émetteur seule, sans chemin : " + politique);
        assertTrue(!politique.contains("localhost:8180"), politique);
    }

    /** ACC-12-S4 : le portail ne s'affiche dans aucun cadre. */
    @Test
    void affichageEnCadreInterdit() {
        given().when().get("/")
                .then().header("Content-Security-Policy", containsString("frame-ancestors 'none'"));
    }

}
