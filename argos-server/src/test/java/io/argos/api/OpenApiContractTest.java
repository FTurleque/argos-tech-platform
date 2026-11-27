package io.argos.api;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

import io.argos.testsupport.Api;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Contrat OpenAPI des préférences et du catalogue (tâche 2.1 du changement
 * {@code personnaliser-profils-et-affichage}).
 *
 * <p>Le document est généré par SmallRye OpenAPI à partir des ressources ; ce test fige
 * les chemins, les opérations et les champs dont dépendent l'IHM web et le client desktop.
 */
@QuarkusTest
class OpenApiContractTest {

    @Test
    void lesOperationsDesPreferencesEtDuCatalogueSontPubliees() {
        Api.enTantQue("alice").queryParam("format", "json")
            .when().get("/q/openapi")
            .then()
            .statusCode(200)
            .body("paths.'/api/v1/preferences'.get", notNullValue())
            .body("paths.'/api/v1/preferences/mise-en-page'.put.responses.'400'", notNullValue())
            .body("paths.'/api/v1/preferences/profils'.put.responses.'400'", notNullValue())
            .body("paths.'/api/v1/catalogue/mises-en-page'.get", notNullValue())
            .body("paths.'/api/v1/catalogue/profils'.get", notNullValue())
            .body("paths.'/api/v1/me/veille'.get", notNullValue());
    }

    /**
     * Veille restituée par profils (PRF-02, PRF-06) : rubriques, garde-fous et mention de validation ; origine
     * abonnement PERSONAL (décision Q-3, tâche 3.9).
     */
    @Test
    void laVeilleParProfilsExposeSesChamps() {
        Api.enTantQue("alice").queryParam("format", "json")
            .when().get("/q/openapi")
            .then()
            .statusCode(200)
            .body("components.schemas.VeilleProfils.properties.keySet()", hasItems("profilsActifs", "items", "page", "size", "total"))
            .body("components.schemas.ElementVeilleProfils.properties.keySet()", hasItems(
                    "type", "id", "titre", "date", "profils", "rubriques", "gardeFous", "mentionValidation", "projets",
                    "abonnements", "mentionIa", "mentionSource"))
            .body("components.schemas.RubriqueElementVeille.properties.keySet()", hasItems(
                    "profil", "identifiant", "libelle"));
    }

    @Test
    void lesSchemasExposentLesChampsAttendus() {
        Api.enTantQue("alice").queryParam("format", "json")
            .when().get("/q/openapi")
            .then()
            .statusCode(200)
            .body("components.schemas.PreferencesEffectives.properties.keySet()", hasItems(
                    "miseEnPage", "miseEnPageChoisie", "miseEnPageIndisponible", "message",
                    "profilsActifs", "profilsParDefaut", "profilsIndisponibles"))
            .body("components.schemas.ChoixMiseEnPage.properties.keySet()", hasItems("miseEnPage"))
            .body("components.schemas.ChoixProfils.properties.keySet()", hasItems("profilsActifs"))
            .body("components.schemas.MiseEnPageCatalogue.properties.keySet()", hasItems(
                    "identifiant", "libelle", "description", "parDefaut"))
            .body("components.schemas.ProfilCatalogue.properties.keySet()", hasItems(
                    "identifiant", "libelle", "description", "version", "rubriques", "gardeFous"))
            .body("components.schemas.Erreur.properties.keySet()", hasItems("code", "message"));
    }
}
