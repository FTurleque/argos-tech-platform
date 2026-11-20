package io.argos.api.comptes;

import static io.argos.testsupport.Api.enTantQue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import io.argos.testsupport.Api;

/** ACC-03 : espaces et rôles. */
@QuarkusTest
class EspacesEtRolesTest {

    /** ACC-03-S1 : l'utilisateur crée « Atelier » et en devient administrateur. */
    @Test
    void creationDUnEspace() {
        String id = enTantQue("alice").contentType(ContentType.JSON).body(Map.of("nom", "Atelier"))
                .when().post("/api/v1/espaces")
                .then().statusCode(201)
                .header("Location", containsString("/api/v1/espaces/"))
                .body("nom", equalTo("Atelier"))
                .body("role", equalTo("ADMINISTRATEUR"))
                .extract().path("id");

        enTantQue("alice").when().get("/api/v1/espaces").then().statusCode(200).body("id", hasItem(id));
        enTantQue("alice").when().get("/api/v1/espaces/" + id + "/membres").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].role", equalTo("ADMINISTRATEUR"));
    }

    /** ACC-03-S2 : un contributeur ne peut pas nommer un administrateur ; les rôles restent inchangés. */
    @Test
    void gestionDesRolesParUnContributeurInterdite() {
        String atelier = Api.creerEspace("alice", "Atelier des contributeurs");
        Api.ajouterMembre("alice", atelier, "carla", "CONTRIBUTEUR");
        String carla = Api.idCompte("carla");
        String alice = Api.idCompte("alice");

        enTantQue("carla").contentType(ContentType.JSON).body(Map.of("role", "ADMINISTRATEUR"))
                .when().put("/api/v1/espaces/" + atelier + "/membres/" + carla + "/role")
                .then().statusCode(403)
                .body("code", equalTo("INTERDIT"));
        enTantQue("carla").contentType(ContentType.JSON).body(Map.of("role", "LECTEUR"))
                .when().put("/api/v1/espaces/" + atelier + "/membres/" + alice + "/role")
                .then().statusCode(403);

        enTantQue("alice").when().get("/api/v1/espaces/" + atelier + "/membres").then().statusCode(200)
                .body("find { it.compteId == '" + carla + "' }.role", equalTo("CONTRIBUTEUR"))
                .body("find { it.compteId == '" + alice + "' }.role", equalTo("ADMINISTRATEUR"));
    }

    /** Un administrateur change le rôle d'un membre. */
    @Test
    void administrateurChangeUnRole() {
        String atelier = Api.creerEspace("alice", "Atelier des promotions");
        Api.ajouterMembre("alice", atelier, "carla", "LECTEUR");
        String carla = Api.idCompte("carla");

        enTantQue("alice").contentType(ContentType.JSON).body(Map.of("role", "CONTRIBUTEUR"))
                .when().put("/api/v1/espaces/" + atelier + "/membres/" + carla + "/role")
                .then().statusCode(200).body("role", equalTo("CONTRIBUTEUR"));
    }

    /** INV-1 : le dernier administrateur ne peut pas perdre son rôle ni être retiré. */
    @Test
    void dernierAdministrateurConserve() {
        String atelier = Api.creerEspace("alice", "Atelier solitaire");
        String alice = Api.idCompte("alice");

        enTantQue("alice").contentType(ContentType.JSON).body(Map.of("role", "LECTEUR"))
                .when().put("/api/v1/espaces/" + atelier + "/membres/" + alice + "/role")
                .then().statusCode(409).body("code", equalTo("DERNIER_ADMINISTRATEUR"));
        enTantQue("alice").when().delete("/api/v1/espaces/" + atelier + "/membres/" + alice)
                .then().statusCode(409);
    }

    @Test
    void nomDEspaceObligatoire() {
        enTantQue("alice").contentType(ContentType.JSON).body(Map.of("nom", "  "))
                .when().post("/api/v1/espaces")
                .then().statusCode(400).body("code", equalTo("DONNEE_INVALIDE"));
    }
}
