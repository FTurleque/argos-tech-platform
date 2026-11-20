package io.argos.mcp;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/** MCP-03 : authentification des appels par jeton OIDC Keycloak (tâche 2.2) ; flux d'appareil (Q-3). */
@QuarkusTest
class AuthentificationMcpTest {

    @Inject
    TestDb baseDeTest;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
    }

    /** MCP-03-S1 : un appel sans jeton est refusé comme non authentifié, sans atteindre l'outil. */
    @Test
    void mcp03S1AppelSansJeton() {
        ExtractableResponse<Response> reponse = Mcp.appelerSansJeton("check_dependency",
                Map.of("ecosystem", "Maven", "name", "org.exemple:lib", "version", "1.0.0"));
        assertEquals(401, reponse.statusCode());
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM mcp_appel"));
    }

    /** Un jeton invalide ou destiné à une autre application est refusé de la même façon. */
    @Test
    void jetonInvalideOuAutreAudience() {
        assertEquals(401, Mcp.appelerAvecJeton("jeton-invalide", "get_project_context", Map.of()).statusCode());
        String autre = new KeycloakTestClient().getAccessToken("alice", "alice", "autre-application", "secret-autre");
        assertEquals(401, Mcp.appelerAvecJeton(autre, "get_project_context", Map.of()).statusCode());
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM mcp_appel"));
    }

    /** Une personne authentifiée sans compte Argos (inscription sur invitation) est refusée et journalisée. */
    @Test
    void personneSansCompteRefusee() {
        ExtractableResponse<Response> reponse = Mcp.appeler("dave", "get_project_context", Map.of());
        assertEquals(200, reponse.statusCode(), reponse.asString());
        assertEquals(Boolean.TRUE, reponse.path("result.isError"));
        assertTrue(((String) reponse.path("result.content[0].text")).startsWith("NON_AUTORISE"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM compte WHERE email = 'dave@argos.test'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id IS NULL AND sujet_oidc IS NOT NULL"
                + " AND ressource = 'MCP tools/call get_project_context' AND motif = 'INSCRIPTION_SUR_INVITATION'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM mcp_appel WHERE compte_id IS NULL AND issue = 'REFUS_ACCES'"));
    }

    /**
     * Q-3 : le client Keycloak {@code argos-mcp} des IDE délivre ses jetons par le flux d'appareil
     * (RFC 8628) : la demande d'autorisation d'appareil renvoie un code d'appareil et un code
     * utilisateur à saisir sur la page de vérification.
     */
    @Test
    void fluxAppareilDuClientMcp() {
        ExtractableResponse<Response> reponse = given().contentType("application/x-www-form-urlencoded")
                .formParam("client_id", "argos-mcp").formParam("scope", "openid")
                .when().post(Api.urlFournisseur() + "/protocol/openid-connect/auth/device").then().extract();
        assertEquals(200, reponse.statusCode(), reponse.asString());
        assertNotNull(reponse.path("device_code"));
        assertNotNull(reponse.path("user_code"));
        assertTrue(((String) reponse.path("verification_uri")).contains("/device"));

        // Tant que l'utilisateur n'a pas approuvé, le jeton n'est pas délivré (RFC 8628 §3.5).
        ExtractableResponse<Response> attente = given().contentType("application/x-www-form-urlencoded")
                .formParam("client_id", "argos-mcp").formParam("device_code", (String) reponse.path("device_code"))
                .formParam("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .when().post(Api.urlFournisseur() + "/protocol/openid-connect/token").then().extract();
        assertEquals(400, attente.statusCode(), attente.asString());
        assertEquals("authorization_pending", attente.path("error"));
    }

    /**
     * Q-3, MCP-03 : un jeton obtenu par le flux d'appareil complet du client {@code argos-mcp}
     * (demande, connexion de l'utilisateur sur la page de vérification, échange du code d'appareil)
     * porte l'audience {@code argos-server} et ouvre les outils MCP.
     */
    @Test
    void jetonDuFluxAppareilAccepteSurMcp() throws Exception {
        Api.nouvelEspace("alice", "Espace MCP");
        ExtractableResponse<Response> demande = given().contentType("application/x-www-form-urlencoded")
                .formParam("client_id", "argos-mcp").formParam("scope", "openid")
                .when().post(Api.urlFournisseur() + "/protocol/openid-connect/auth/device").then().extract();
        assertEquals(200, demande.statusCode(), demande.asString());

        String parcours = FluxAppareil.approuver(demande.path("verification_uri_complete"), "alice", "alice");

        ExtractableResponse<Response> jeton = given().contentType("application/x-www-form-urlencoded")
                .formParam("client_id", "argos-mcp").formParam("device_code", (String) demande.path("device_code"))
                .formParam("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .when().post(Api.urlFournisseur() + "/protocol/openid-connect/token").then().extract();
        assertEquals(200, jeton.statusCode(), jeton.asString() + "\n" + parcours);

        ExtractableResponse<Response> reponse = Mcp.appelerAvecJeton(jeton.path("access_token"), "get_project_context", Map.of());
        assertEquals(200, reponse.statusCode(), reponse.asString());
        assertEquals(Boolean.FALSE, reponse.path("result.isError"), reponse.asString());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM mcp_appel WHERE issue = 'SUCCES'"));
    }
}
