package io.argos.api.preference;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import jakarta.inject.Inject;

import io.argos.workspace.preference.PreferencesService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Préférences de l'utilisateur courant (AFF-01, AFF-03, AFF-04, PRF-01).
 *
 * <p>Chaque test utilise son propre utilisateur : la base est partagée entre les tests.
 */
@QuarkusTest
class PreferencesResourceTest {

    private static void choisirMiseEnPage(String miseEnPage, int statut) {
        given().contentType(ContentType.JSON).body("{\"miseEnPage\":\"" + miseEnPage + "\"}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then().statusCode(statut);
    }

    private static void remplacerProfils(String json, int statut) {
        given().contentType(ContentType.JSON).body("{\"profilsActifs\":" + json + "}")
            .when().put("/api/v1/preferences/profils")
            .then().statusCode(statut);
    }

    @Test
    @TestSecurity(user = "aff04-premier-acces")
    void aff04S1SansChoixLaMiseEnPageClassiqueSApplique() {
        given()
            .when().get("/api/v1/preferences")
            .then()
            .statusCode(200)
            .body("miseEnPage", equalTo("classique"))
            .body("miseEnPageChoisie", nullValue())
            .body("miseEnPageIndisponible", equalTo(false))
            .body("message", nullValue());
    }

    @Test
    @TestSecurity(user = "aff01-choix")
    void aff01S1ChoixDUneMiseEnPagePubliee() {
        choisirMiseEnPage("classique", 200);
        given()
            .contentType(ContentType.JSON).body("{\"miseEnPage\":\"gazette\"}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then()
            .statusCode(200)
            .body("miseEnPage", equalTo("gazette"))
            .body("miseEnPageChoisie", equalTo("gazette"));
        // AFF-03 : la préférence est lue sur le serveur, quel que soit le client qui la demande.
        given().when().get("/api/v1/preferences").then().statusCode(200).body("miseEnPage", equalTo("gazette"));
    }

    @Test
    @TestSecurity(user = "aff01-inconnue")
    void aff01S2MiseEnPageAbsenteDuCatalogue() {
        choisirMiseEnPage("journal", 200);
        given()
            .contentType(ContentType.JSON).body("{\"miseEnPage\":\"psychedelique\"}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then()
            .statusCode(400)
            .body("code", equalTo("MISE_EN_PAGE_INCONNUE"))
            .body("message", containsString("n'existe pas"));
        given().when().get("/api/v1/preferences").then().body("miseEnPage", equalTo("journal"));
    }

    @Test
    @TestSecurity(user = "aff01-vide")
    void uneDemandeSansMiseEnPageEstRefusee() {
        given().contentType(ContentType.JSON).body("{}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then().statusCode(400).body("code", equalTo("MISE_EN_PAGE_INCONNUE"));
    }

    @Test
    @TestSecurity(user = "prf01-cumul")
    void prf01S1CumulDeDeuxProfils() {
        remplacerProfils("[\"developpeur\"]", 200);
        given()
            .contentType(ContentType.JSON).body("{\"profilsActifs\":[\"developpeur\",\"expert-metier\"]}")
            .when().put("/api/v1/preferences/profils")
            .then()
            .statusCode(200)
            .body("profilsActifs", containsInAnyOrder("developpeur", "expert-metier"))
            .body("profilsParDefaut", equalTo(false));
    }

    @Test
    @TestSecurity(user = "prf01-dernier")
    void prf01S2DesactivationDuDernierProfil() {
        remplacerProfils("[\"expert-metier\"]", 200);
        given()
            .contentType(ContentType.JSON).body("{\"profilsActifs\":[]}")
            .when().put("/api/v1/preferences/profils")
            .then()
            .statusCode(400)
            .body("code", equalTo("AU_MOINS_UN_PROFIL"))
            .body("message", containsString("Au moins un profil"));
        given().when().get("/api/v1/preferences").then().body("profilsActifs", contains("expert-metier"));
    }

    @Test
    @TestSecurity(user = "prf01-inconnu")
    void prf01S3ProfilAbsentDuCatalogue() {
        remplacerProfils("[\"developpeur\"]", 200);
        given()
            .contentType(ContentType.JSON).body("{\"profilsActifs\":[\"developpeur\",\"astrologue\"]}")
            .when().put("/api/v1/preferences/profils")
            .then()
            .statusCode(400)
            .body("code", equalTo("PROFIL_INCONNU"))
            .body("message", containsString("n'existe pas"));
        given().when().get("/api/v1/preferences").then().body("profilsActifs", contains("developpeur"));
    }

    @Test
    @TestSecurity(user = "prf01-defaut")
    void sansChoixLesProfilsParDefautSAppliquent() {
        given().when().get("/api/v1/preferences")
            .then().statusCode(200)
            .body("profilsActifs", contains("developpeur"))
            .body("profilsParDefaut", equalTo(true));
    }

    @Test
    @TestSecurity(user = "idempotence")
    void remplacerDeuxFoisLaMemeValeurEstIdempotent() {
        remplacerProfils("[\"expert-metier\",\"developpeur\"]", 200);
        remplacerProfils("[\"developpeur\",\"expert-metier\"]", 200);
        given().when().get("/api/v1/preferences")
            .then().body("profilsActifs", contains("developpeur", "expert-metier"));
    }

    @Inject
    PreferencesService preferences;

    @Test
    @TestSecurity(user = "isolement-a")
    void lesPreferencesDUnUtilisateurNeTouchentPasUnAutre() {
        choisirMiseEnPage("fantaisie", 200);
        assertEquals(Optional.of("fantaisie"), preferences.lire("isolement-a").miseEnPage());
        assertEquals(Optional.empty(), preferences.lire("isolement-b").miseEnPage());
    }

    @Test
    void lesPreferencesExigentUnUtilisateurAuthentifie() {
        given().when().get("/api/v1/preferences").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"miseEnPage\":\"gazette\"}")
            .when().put("/api/v1/preferences/mise-en-page").then().statusCode(401);
    }
}
