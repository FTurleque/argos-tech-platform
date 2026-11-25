package io.argos.api.comptes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * ACC-01 : authentification obligatoire, sauf santé et identité du serveur. Les ressources statiques de l'IHM, publiques
 * pour que la page se charge avant l'ouverture de la session, sont couvertes par {@link CheminsPublicsTest} (ACC-12).
 */
@QuarkusTest
class AuthentificationTest {

    /** ACC-01-S1 : sans session, toute route de données est refusée comme non authentifiée, sans donnée. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/moi", "/api/v1/espaces", "/api/v1/invitations",
            "/api/v1/espaces/00000000-0000-0000-0000-000000000001", "/api/v1/veille", "/q/openapi"})
    void accesSansSessionRefuse(String chemin) {
        given().when().get(chemin)
                .then().statusCode(401)
                .body(emptyOrNullString());
    }

    @Test
    void jetonInvalideRefuse() {
        given().auth().oauth2("eyJhbGciOiJSUzI1NiJ9.e30.signature-invalide")
                .when().get("/api/v1/espaces")
                .then().statusCode(401);
    }

    /** Jeton valide émis pour une autre application : audience refusée. */
    @Test
    void jetonDUneAutreAudienceRefuse() {
        String jeton = new KeycloakTestClient().getAccessToken("alice", "alice", "autre-application", "secret-autre");
        given().auth().oauth2(jeton)
                .when().get("/api/v1/moi")
                .then().statusCode(401);
    }

    /**
     * ACC-01-S2 : l'identité du serveur reste publique et ne contient aucune donnée d'utilisateur ; elle désigne
     * l'émetteur public et le client public de l'IHM (ACC-11).
     */
    @Test
    void identiteDuServeurPublique() {
        given().when().get("/api/v1/system/info")
                .then().statusCode(200)
                .body("application", equalTo("argos-server"))
                .body("version", not(emptyOrNullString()))
                .body("keySet()", containsInAnyOrder("application", "version", "apiVersion", "oidcIssuer", "oidcClientId"))
                .body("oidcIssuer", equalTo("http://idp.test:8443/realms/argos"))
                .body("oidcClientId", equalTo("argos-ui"));
    }

    @Test
    void santePublique() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("$", not(anEmptyMap()));
    }
}
