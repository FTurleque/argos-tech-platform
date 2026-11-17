package io.argos.api.comptes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.argos.testsupport.Api;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * Client public {@code argos-ui} du royaume de test (changement connecter-ihm-oidc : ACC-07, ACC-09,
 * ACC-10). Parcours Authorization Code + PKCE S256 mené contre le Keycloak des Dev Services, tel que
 * le font le portail web (retour sur http://localhost:5173/) et le client desktop (retour sur
 * l'interface de bouclage 127.0.0.1, port quelconque) ; le jeton obtenu est présenté à l'API.
 */
@QuarkusTest
class ClientIhmOidcTest {

    private static final String CLIENT = "argos-ui";
    private static final String PORTAIL = "http://localhost:5173";
    private static final String ORIGINE_DESKTOP = "app://argos";
    private static final Pattern FORMULAIRE = Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*>");
    private static final Pattern ACTION = Pattern.compile("action=\"([^\"]+)\"");

    private record Pkce(String verificateur, String defi) {
    }

    private static Pkce pkce() throws Exception {
        byte[] octets = new byte[32];
        new SecureRandom().nextBytes(octets);
        String verificateur = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        byte[] empreinte = MessageDigest.getInstance("SHA-256").digest(verificateur.getBytes(StandardCharsets.US_ASCII));
        return new Pkce(verificateur, Base64.getUrlEncoder().withoutPadding().encodeToString(empreinte));
    }

    private static String bouclage() {
        return "http://127.0.0.1:" + ThreadLocalRandom.current().nextInt(49152, 65535) + "/argos/retour";
    }

    private static Response autoriser(String redirectUri, String defi, String state) {
        var requete = given().redirects().follow(false)
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid")
                .queryParam("state", state);
        if (defi != null) {
            requete = requete.queryParam("code_challenge", defi).queryParam("code_challenge_method", "S256");
        }
        return requete.when().get(Api.urlFournisseur() + "/protocol/openid-connect/auth");
    }

    private static Map<String, String> requete(String location) {
        Map<String, String> parametres = new HashMap<>();
        String brut = URI.create(location).getRawQuery();
        if (brut == null) {
            return parametres;
        }
        for (String paire : brut.split("&")) {
            int egal = paire.indexOf('=');
            String nom = egal < 0 ? paire : paire.substring(0, egal);
            String valeur = egal < 0 ? "" : URLDecoder.decode(paire.substring(egal + 1), StandardCharsets.UTF_8);
            parametres.put(nom, valeur);
        }
        return parametres;
    }

    /** Connexion de l'utilisateur sur la page du fournisseur ; renvoie le code reçu à la redirection. */
    private static String code(String utilisateur, String redirectUri, Pkce pkce) {
        String state = "etat-" + System.nanoTime();
        Response page = autoriser(redirectUri, pkce.defi(), state);
        assertEquals(200, page.statusCode(), "page de connexion attendue pour " + redirectUri);
        Matcher formulaire = FORMULAIRE.matcher(page.asString());
        assertTrue(formulaire.find(), "formulaire de connexion absent");
        Matcher action = ACTION.matcher(formulaire.group());
        assertTrue(action.find(), "action du formulaire absente");
        Response connexion = given().redirects().follow(false).urlEncodingEnabled(false)
                .cookies(page.getCookies())
                .contentType("application/x-www-form-urlencoded")
                .formParam("username", utilisateur)
                .formParam("password", utilisateur)
                .when().post(action.group(1).replace("&amp;", "&"));
        assertEquals(302, connexion.statusCode());
        String location = connexion.getHeader("Location");
        assertTrue(location.startsWith(redirectUri + "?"), location);
        Map<String, String> retour = requete(location);
        assertEquals(state, retour.get("state"), "le state revient inchangé");
        assertNotNull(retour.get("code"));
        return retour.get("code");
    }

    private static Response echanger(String origine, String code, String redirectUri, String verificateur) {
        return given().header("Origin", origine)
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", redirectUri)
                .formParam("client_id", CLIENT)
                .formParam("code_verifier", verificateur)
                .when().post(Api.urlFournisseur() + "/protocol/openid-connect/token");
    }

    /** ACC-07-S2, ACC-09-S1 : portail web, code + PKCE, jeton accepté par l'API, renouvellement. */
    @Test
    void portailWebObtientUnJetonAccepteParLApi() throws Exception {
        Pkce pkce = pkce();
        String redirectUri = PORTAIL + "/";
        Response jetons = echanger(PORTAIL, code("alice", redirectUri, pkce), redirectUri, pkce.verificateur());
        jetons.then().statusCode(200)
                .header("Access-Control-Allow-Origin", PORTAIL)
                .body("token_type", equalTo("Bearer"))
                .body("refresh_token", not(emptyOrNullString()))
                .body("id_token", not(emptyOrNullString()));

        given().auth().oauth2(jetons.path("access_token"))
                .when().get("/api/v1/moi")
                .then().statusCode(200).body("email", equalTo("alice@argos.test"));

        given().header("Origin", PORTAIL)
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "refresh_token")
                .formParam("refresh_token", (String) jetons.path("refresh_token"))
                .formParam("client_id", CLIENT)
                .when().post(Api.urlFournisseur() + "/protocol/openid-connect/token")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", PORTAIL)
                .body("access_token", not(emptyOrNullString()));
    }

    /** ACC-10-S1 : client desktop, retour sur 127.0.0.1 avec un port quelconque, échange depuis app://argos. */
    @Test
    void clientDesktopUtiliseLInterfaceDeBouclage() throws Exception {
        Pkce pkce = pkce();
        String redirectUri = bouclage();
        Response jetons = echanger(ORIGINE_DESKTOP, code("alice", redirectUri, pkce), redirectUri, pkce.verificateur());
        jetons.then().statusCode(200).header("Access-Control-Allow-Origin", ORIGINE_DESKTOP);
        given().auth().oauth2(jetons.path("access_token"))
                .when().get("/api/v1/moi")
                .then().statusCode(200);
    }

    /** ACC-09-S4 : authentifiée par l'IHM mais sans invitation, la personne reçoit le refus dédié. */
    @Test
    void personneSansInvitationRecoitLeRefusDedie() throws Exception {
        Pkce pkce = pkce();
        String redirectUri = PORTAIL + "/";
        Response jetons = echanger(PORTAIL, code("dave", redirectUri, pkce), redirectUri, pkce.verificateur());
        given().auth().oauth2(jetons.then().statusCode(200).extract().<String>path("access_token"))
                .when().get("/api/v1/moi")
                .then().statusCode(403).body("code", equalTo("INSCRIPTION_SUR_INVITATION"));
    }

    /** Redirection non déclarée (dont l'ancien app://argos/) : le fournisseur refuse la demande. */
    @Test
    void redirectionNonDeclareeRefusee() throws Exception {
        String defi = pkce().defi();
        assertEquals(400, autoriser("http://malveillant.example/", defi, "s").statusCode());
        assertEquals(400, autoriser("app://argos/index.html", defi, "s").statusCode());
        assertEquals(400, autoriser("http://127.0.0.1:50000/autre", defi, "s").statusCode());
    }

    /** PKCE S256 obligatoire : sans défi, le fournisseur renvoie une erreur et aucun code. */
    @Test
    void pkceObligatoire() {
        Response reponse = autoriser(PORTAIL + "/", null, "s");
        assertEquals(302, reponse.statusCode());
        Map<String, String> retour = requete(reponse.getHeader("Location"));
        assertEquals("invalid_request", retour.get("error"));
        assertTrue(!retour.containsKey("code"));
    }

    /** Un code présenté avec un autre vérificateur est refusé. */
    @Test
    void verificateurDifferentRefuse() throws Exception {
        String redirectUri = PORTAIL + "/";
        String code = code("alice", redirectUri, pkce());
        echanger(PORTAIL, code, redirectUri, pkce().verificateur())
                .then().statusCode(400).body("error", equalTo("invalid_grant"));
    }
}
