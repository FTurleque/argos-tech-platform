package io.argos.api.preference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;

import java.util.Map;

import jakarta.inject.Inject;

import io.argos.testsupport.Api;
import io.argos.workspace.preference.PreferencesRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Mise en page retirée du catalogue après avoir été choisie (AFF-04-S2, INV-2). */
@QuarkusTest
@TestProfile(MiseEnPageRetireeTest.FantaisieRetiree.class)
class MiseEnPageRetireeTest {

    /** Catalogue dont la mise en page {@code fantaisie} est retirée. */
    public static class FantaisieRetiree implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.catalogue.mises-en-page.retirees", "fantaisie");
        }
    }

    @Inject
    PreferencesRepository repository;

    @Test
    void aff04S2MiseEnPageRetireeClassiqueEtMessage() {
        // Préférence enregistrée avant le retrait.
        repository.enregistrerMiseEnPage(Api.compte("ines"), "fantaisie");

        Api.en("ines")
            .when().get("/api/v1/preferences")
            .then()
            .statusCode(200)
            .body("miseEnPage", equalTo("classique"))
            .body("miseEnPageChoisie", equalTo("fantaisie"))
            .body("miseEnPageIndisponible", equalTo(true))
            .body("message", containsString("n'est plus disponible"));
    }

    @Test
    void uneMiseEnPageRetireeNePeutPlusEtreChoisie() {
        Api.en("ines").when().get("/api/v1/catalogue/mises-en-page")
            .then().statusCode(200).body("identifiant", not(hasItem("fantaisie")));
        Api.en("ines").contentType(ContentType.JSON).body("{\"miseEnPage\":\"fantaisie\"}")
            .when().put("/api/v1/preferences/mise-en-page")
            .then().statusCode(400).body("code", equalTo("MISE_EN_PAGE_INCONNUE"));
    }
}
