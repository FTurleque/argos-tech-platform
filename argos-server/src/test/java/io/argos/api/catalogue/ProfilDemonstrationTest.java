package io.argos.api.catalogue;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import io.argos.workspace.preference.PreferencesRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

/**
 * Publication d'un profil de démonstration (PRF-04-S1, partie catalogue et préférences).
 *
 * <p>Le profil apparaît au catalogue et les profils actifs des autres utilisateurs restent
 * inchangés. La comparaison de la veille restituée attend la restitution de la veille (tâche 3.4).
 */
@QuarkusTest
@TestProfile(ProfilDemonstrationTest.AvecDemonstration.class)
class ProfilDemonstrationTest {

    /** Catalogue enrichi du manifeste de test {@code catalog/profils/demonstration.json}. */
    public static class AvecDemonstration implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.catalogue.profils.manifestes", "developpeur,expert-metier,demonstration");
        }
    }

    @Inject
    PreferencesRepository repository;

    @Test
    @TestSecurity(user = "prf04-developpeur")
    void prf04S1LeProfilDeDemonstrationApparaitSansModifierLesProfilsActifs() {
        repository.enregistrerProfils("prf04-developpeur", Set.of("developpeur"));

        given().when().get("/api/v1/catalogue/profils")
            .then().statusCode(200)
            .body("identifiant", contains("developpeur", "expert-metier", "demonstration"))
            .body("find { it.identifiant == 'developpeur' }.version", equalTo("1.0.0"));

        given().when().get("/api/v1/preferences")
            .then().statusCode(200)
            .body("profilsActifs", contains("developpeur"));
    }
}
