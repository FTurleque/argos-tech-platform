package io.argos.artefact;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Artefact empaqueté (AUD-TST-12) : le jar construit par {@code mvn package} ({@code target/quarkus-app}) est lancé
 * par {@code java -jar} sous le profil {@code test} ({@code quarkus.test.integration-test-profile}), contre le
 * PostgreSQL et le Keycloak des Dev Services. Le test vérifie ce que seul l'artefact révèle : migrations Flyway lues
 * dans le jar et appliquées, ressources servies, politique d'accès et en-têtes de sécurité de la configuration
 * empaquetée.
 *
 * <p>Exécuté par maven-failsafe-plugin en phase {@code integration-test} ({@code mvn verify}).
 */
@QuarkusIntegrationTest
class ArtefactEmpaqueteIT {

    private static final int NON_AUTHENTIFIE = 401;
    private static final int SUCCES = 200;

    /** La base est jointe et migrée : la vérification de disponibilité inclut la source de données. */
    @Test
    void serveurDisponibleAvecSaBase() {
        given().when().get("/q/health/ready")
                .then().statusCode(SUCCES)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem("Database connections health check"));
    }

    /** Route publique de l'API : identité du serveur et client OIDC de l'IHM. */
    @Test
    void identiteDuServeurPublique() {
        given().when().get("/api/v1/system/info")
                .then().statusCode(SUCCES)
                .body("application", equalTo("argos-server"))
                .body("apiVersion", equalTo("v1"))
                .body("oidcClientId", equalTo("argos-ui"));
    }

    /** Toute autre route de l'API est refusée sans jeton, avec les en-têtes de sécurité (ACC-12). */
    @Test
    void routeProtegeeRefuseeSansJeton() {
        given().when().get("/api/v1/moi")
                .then().statusCode(NON_AUTHENTIFIE)
                .header("Content-Security-Policy", not(emptyOrNullString()))
                .header("X-Content-Type-Options", equalTo("nosniff"));
    }

    /** Le serveur MCP de l'artefact exige lui aussi une authentification. */
    @Test
    void serveurMcpRefuseSansJeton() {
        given().contentType("application/json").body("{}").when().post("/mcp")
                .then().statusCode(NON_AUTHENTIFIE);
    }
}
