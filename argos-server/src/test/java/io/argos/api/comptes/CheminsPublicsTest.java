package io.argos.api.comptes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.junit.QuarkusTest;

/**
 * ACC-12 : chemins publics de la politique d'accès HTTP. Les ressources statiques de l'IHM se chargent sans session
 * (la page doit s'afficher pour ouvrir la session OIDC) ; toute autre route, API et routes techniques comprises,
 * reste refusée comme non authentifiée. Les ressources statiques de test sont dans
 * {@code src/test/resources/META-INF/resources} : l'IHM n'est copiée qu'à la construction de l'image.
 */
@QuarkusTest
class CheminsPublicsTest {

    private static final String CLE_CHEMINS_PUBLICS = "quarkus.http.auth.permission.public.paths";

    /** Routes de l'API et routes techniques publiques : l'identité du serveur et la santé, rien d'autre. */
    private static final Set<String> ROUTES_NON_STATIQUES_PUBLIQUES =
            Set.of("/api/v1/system/info", "/q/health", "/q/health/*");

    /** ACC-12-S1 : la page, une ressource de build et l'avis de licence des polices, sans session. */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/assets/test.js", "/licences-polices.txt"})
    void ressourcesDeLIhmSansSession(String chemin) {
        given().when().get(chemin)
                .then().statusCode(200)
                .body(containsStringIgnoringCase("test"));
    }

    /** ACC-12-S1 : la page d'accueil ne porte aucune donnée de veille, d'espace ni d'utilisateur. */
    @Test
    void pageDAccueilSansDonneeDeVeille() {
        String corps = given().when().get("/").then().statusCode(200).extract().asString();
        for (String donnee : List.of("espace", "projet", "alerte", "@")) {
            assertTrue(!corps.toLowerCase().contains(donnee), "la page publique ne doit pas contenir « " + donnee + " »");
        }
    }

    /** ACC-12-S2 : API et routes techniques, hors identité et santé, refusées sans session et sans corps. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/moi", "/api/v1/espaces", "/api/v1/recherche", "/api/v1/me/veille",
            "/api/v1/ops/sources", "/api/v1/system", "/api/v1/system/info/autre", "/q/openapi", "/q/metrics",
            "/assets/../api/v1/moi", "/api/v1/../v1/espaces"})
    void routesProtegeesRefuseesSansSession(String chemin) {
        given().when().get(chemin)
                .then().statusCode(401)
                .body(emptyOrNullString());
    }

    /**
     * Invariant I-2 : les chemins publics se limitent aux ressources statiques de l'IHM, à l'identité du serveur et
     * à la santé. Ajouter une route d'API ou une route technique publique fait échouer ce test.
     */
    @Test
    void aucuneRouteDApiOuTechniquePubliqueAuDelaDeIdentiteEtSante() {
        String configures = ConfigProvider.getConfig().getValue(CLE_CHEMINS_PUBLICS, String.class);
        List<String> chemins = Arrays.stream(configures.split(",")).map(String::trim).toList();
        assertTrue(!chemins.isEmpty(), "des chemins publics sont configurés");
        for (String chemin : chemins) {
            boolean routeApplicative = chemin.startsWith("/api/") || chemin.startsWith("/q/");
            if (routeApplicative) {
                assertTrue(ROUTES_NON_STATIQUES_PUBLIQUES.contains(chemin),
                        "route publique non autorisée : " + chemin);
            }
        }
        assertTrue(chemins.containsAll(ROUTES_NON_STATIQUES_PUBLIQUES), "identité et santé restent publiques");
        assertEquals(chemins.size(), Set.copyOf(chemins).size(), "aucun chemin public en double");
    }
}
