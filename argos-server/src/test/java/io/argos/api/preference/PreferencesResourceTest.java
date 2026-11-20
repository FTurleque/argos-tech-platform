package io.argos.api.preference;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.argos.workspace.Compte;
import io.argos.workspace.CompteCree;
import io.argos.workspace.ComptesService;
import io.argos.workspace.preference.PreferencesService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Préférences de l'utilisateur courant (AFF-01, AFF-03, AFF-04, PRF-01), avec des jetons réels :
 * la clé des préférences est l'identifiant du compte Argos relié au sujet OIDC.
 *
 * <p>Les préférences d'ines et de jules sont effacées avant chaque test (base partagée).
 */
@QuarkusTest
class PreferencesResourceTest {

    @Inject
    TestDb baseDeTest;

    @Inject
    DataSource dataSource;

    @Inject
    PreferencesService preferences;

    @Inject
    ComptesService comptes;

    @Inject
    Event<CompteCree> compteCree;

    private UUID ines;

    @BeforeEach
    void reinitialiser() {
        ines = Api.compte("ines");
        UUID jules = Api.compte("jules");
        baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id IN ('" + ines + "', '" + jules + "')");
    }

    private static RequestSpecification ines() {
        return Api.en("ines");
    }

    private static void choisirMiseEnPage(String miseEnPage, int statut) {
        ines().contentType(ContentType.JSON).body("{\"miseEnPage\":\"" + miseEnPage + "\"}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then().statusCode(statut);
    }

    private static void remplacerProfils(String json, int statut) {
        ines().contentType(ContentType.JSON).body("{\"profilsActifs\":" + json + "}")
            .when().put("/api/v1/preferences/profils")
            .then().statusCode(statut);
    }

    @Test
    void aff04S1SansChoixLaMiseEnPageClassiqueSApplique() {
        ines()
            .when().get("/api/v1/preferences")
            .then()
            .statusCode(200)
            .body("miseEnPage", equalTo("classique"))
            .body("miseEnPageChoisie", nullValue())
            .body("miseEnPageIndisponible", equalTo(false))
            .body("message", nullValue());
    }

    @Test
    void aff01S1ChoixDUneMiseEnPagePubliee() {
        choisirMiseEnPage("classique", 200);
        ines()
            .contentType(ContentType.JSON).body("{\"miseEnPage\":\"gazette\"}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then()
            .statusCode(200)
            .body("miseEnPage", equalTo("gazette"))
            .body("miseEnPageChoisie", equalTo("gazette"));
        // AFF-03 : la préférence est lue sur le serveur, quel que soit le client qui la demande.
        ines().when().get("/api/v1/preferences").then().statusCode(200).body("miseEnPage", equalTo("gazette"));
        assertEquals("gazette", baseDeTest.string("SELECT mise_en_page FROM workspace_preference_utilisateur WHERE compte_id = '"
                + ines + "'"), "clé des préférences : identifiant du compte Argos");
    }

    @Test
    void aff01S2MiseEnPageAbsenteDuCatalogue() {
        choisirMiseEnPage("journal", 200);
        ines()
            .contentType(ContentType.JSON).body("{\"miseEnPage\":\"psychedelique\"}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then()
            .statusCode(400)
            .body("code", equalTo("MISE_EN_PAGE_INCONNUE"))
            .body("message", containsString("n'existe pas"));
        ines().when().get("/api/v1/preferences").then().body("miseEnPage", equalTo("journal"));
    }

    @Test
    void uneDemandeSansMiseEnPageEstRefusee() {
        ines().contentType(ContentType.JSON).body("{}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then().statusCode(400).body("code", equalTo("MISE_EN_PAGE_INCONNUE"));
    }

    @Test
    void prf01S1CumulDeDeuxProfils() {
        remplacerProfils("[\"developpeur\"]", 200);
        ines()
            .contentType(ContentType.JSON).body("{\"profilsActifs\":[\"developpeur\",\"expert-metier\"]}")
            .when().put("/api/v1/preferences/profils")
            .then()
            .statusCode(200)
            .body("profilsActifs", containsInAnyOrder("developpeur", "expert-metier"))
            .body("profilsParDefaut", equalTo(false));
    }

    @Test
    void prf01S2DesactivationDuDernierProfil() {
        remplacerProfils("[\"expert-metier\"]", 200);
        ines()
            .contentType(ContentType.JSON).body("{\"profilsActifs\":[]}")
            .when().put("/api/v1/preferences/profils")
            .then()
            .statusCode(400)
            .body("code", equalTo("AU_MOINS_UN_PROFIL"))
            .body("message", containsString("Au moins un profil"));
        ines().when().get("/api/v1/preferences").then().body("profilsActifs", contains("expert-metier"));
    }

    @Test
    void prf01S3ProfilAbsentDuCatalogue() {
        remplacerProfils("[\"developpeur\"]", 200);
        ines()
            .contentType(ContentType.JSON).body("{\"profilsActifs\":[\"developpeur\",\"astrologue\"]}")
            .when().put("/api/v1/preferences/profils")
            .then()
            .statusCode(400)
            .body("code", equalTo("PROFIL_INCONNU"))
            .body("message", containsString("n'existe pas"));
        ines().when().get("/api/v1/preferences").then().body("profilsActifs", contains("developpeur"));
    }

    @Test
    void sansChoixLesProfilsParDefautSAppliquent() {
        ines().when().get("/api/v1/preferences")
            .then().statusCode(200)
            .body("profilsActifs", contains("developpeur"))
            .body("profilsParDefaut", equalTo(true));
    }

    @Test
    void remplacerDeuxFoisLaMemeValeurEstIdempotent() {
        remplacerProfils("[\"expert-metier\",\"developpeur\"]", 200);
        remplacerProfils("[\"developpeur\",\"expert-metier\"]", 200);
        ines().when().get("/api/v1/preferences")
            .then().body("profilsActifs", contains("developpeur", "expert-metier"));
    }

    @Test
    void lesPreferencesDUnUtilisateurNeTouchentPasUnAutre() {
        choisirMiseEnPage("fantaisie", 200);
        assertEquals(Optional.of("fantaisie"), preferences.lire(ines).miseEnPage());
        assertEquals(Optional.empty(), preferences.lire(Api.compte("jules")).miseEnPage());
        Api.en("jules").when().get("/api/v1/preferences").then().statusCode(200).body("miseEnPage", equalTo("classique"));
    }

    /** La RLS n'expose à argos_app que les préférences du compte courant (garde-fou en base). */
    @Test
    void laBaseNExposeQueLesPreferencesDuCompteCourant() throws Exception {
        choisirMiseEnPage("fantaisie", 200);
        UUID jules = Api.compte("jules");
        try (Connection connexion = dataSource.getConnection(); Statement instruction = connexion.createStatement()) {
            connexion.setAutoCommit(false);
            instruction.execute("SET LOCAL ROLE argos_app");
            instruction.execute("SELECT set_config('argos.compte_courant', '" + jules + "', true)");
            assertEquals(0, instruction.executeUpdate(
                    "UPDATE workspace_preference_utilisateur SET mise_en_page = 'gazette' WHERE compte_id = '"
                    + ines + "'"),"les préférences d'un autre compte ne sont pas modifiables");
            try (ResultSet resultat = instruction.executeQuery("SELECT count(*) FROM workspace_preference_utilisateur")) {
                resultat.next();
                assertEquals(0, resultat.getLong(1), "les préférences d'un autre compte ne sont pas visibles");
            }
            connexion.rollback();
            connexion.setAutoCommit(true);
        }
        assertEquals("fantaisie", baseDeTest.string("SELECT mise_en_page FROM workspace_preference_utilisateur WHERE compte_id = '"
                + ines + "'"));
    }

    /** PRF-01, Q-3 : la création d'un compte active le profil developpeur dans ses préférences. */
    @Test
    void laCreationDUnCompteActiveLeProfilDeveloppeur() {
        Compte compte = comptes.parSujet(baseDeTest.string("SELECT sujet_oidc FROM compte WHERE id = '" + ines + "'")).orElseThrow();
        compteCree.fire(new CompteCree(compte, "developpeur"));
        assertEquals("{developpeur}", baseDeTest.string(
                "SELECT profils_actifs::text FROM workspace_preference_utilisateur WHERE compte_id = '" + ines + "'"));
        ines().when().get("/api/v1/preferences").then().statusCode(200)
            .body("profilsActifs", contains("developpeur")).body("profilsParDefaut", equalTo(false));

        // Un compte qui a déjà choisi ses profils les conserve.
        remplacerProfils("[\"expert-metier\"]", 200);
        compteCree.fire(new CompteCree(compte, "developpeur"));
        ines().when().get("/api/v1/preferences").then().body("profilsActifs", contains("expert-metier"));
        preferences.activerSiAucun(ines, Set.of("developpeur"));
        ines().when().get("/api/v1/preferences").then().body("profilsActifs", contains("expert-metier"));
    }

    @Test
    void lesPreferencesExigentUnUtilisateurAuthentifie() {
        given().when().get("/api/v1/preferences").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"miseEnPage\":\"gazette\"}")
            .when().put("/api/v1/preferences/mise-en-page").then().statusCode(401);
    }
}
