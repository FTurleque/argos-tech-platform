package io.argos.api.comptes;

import static io.restassured.RestAssured.given;

import java.util.Map;

import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

/**
 * Appels à l'API avec des jetons réels émis par le Keycloak des Dev Services (royaume de test
 * {@code argos}, mot de passe de chaque utilisateur = son identifiant).
 */
final class Api {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    private Api() {
    }

    static String jeton(String utilisateur) {
        return KEYCLOAK.getAccessToken(utilisateur);
    }

    static String urlFournisseur() {
        return KEYCLOAK.getAuthServerUrl();
    }

    static RequestSpecification en(String utilisateur) {
        return given().auth().oauth2(jeton(utilisateur));
    }

    static ValidatableResponse moi(String utilisateur) {
        return en(utilisateur).when().get("/api/v1/moi").then();
    }

    static String creerEspace(String admin, String nom) {
        return en(admin).contentType(ContentType.JSON).body(Map.of("nom", nom))
                .when().post("/api/v1/espaces")
                .then().statusCode(201).extract().path("id");
    }

    static String inviter(String admin, String espaceId, String email, String role) {
        return en(admin).contentType(ContentType.JSON).body(Map.of("email", email, "role", role))
                .when().post("/api/v1/espaces/" + espaceId + "/invitations")
                .then().statusCode(201).extract().path("id");
    }

    static void accepter(String utilisateur, String invitationId) {
        en(utilisateur).when().post("/api/v1/invitations/" + invitationId + "/acceptation")
                .then().statusCode(200);
    }

    static String idCompte(String utilisateur) {
        return moi(utilisateur).statusCode(200).extract().path("id");
    }

    /** Garantit que l'utilisateur a un compte (inscription sur invitation), quel que soit l'ordre des tests. */
    static void assurerCompte(String utilisateur) {
        String accueil = creerEspace("alice", "Accueil de " + utilisateur);
        inviter("alice", accueil, utilisateur + "@argos.test", "LECTEUR");
        moi(utilisateur).statusCode(200);
    }

    /** Invite puis fait accepter : l'utilisateur devient membre avec le rôle demandé. */
    static void ajouterMembre(String admin, String espaceId, String utilisateur, String role) {
        accepter(utilisateur, inviter(admin, espaceId, utilisateur + "@argos.test", role));
    }
}
