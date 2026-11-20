package io.argos.api.comptes;

import static io.argos.testsupport.Api.en;
import static io.argos.testsupport.Api.moi;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import io.argos.testsupport.Api;

/**
 * Parcours de bout en bout au niveau de l'API (tâche 5.2) : première connexion, création d'un
 * espace, invitation d'un lecteur, refus d'accès à un autre espace journalisé. Jetons réels
 * émis par Keycloak, base PostgreSQL réelle avec RLS.
 */
@QuarkusTest
class ParcoursComptesEtAccesTest {

    @Inject
    DataSource dataSource;

    @Test
    void parcoursComplet() throws Exception {
        // 0. L'administrateur plateforme initial invite Hugo (inscription sur invitation).
        String espaceAlice = Api.creerEspace("alice", "Veille d'Alice");
        Api.inviter("alice", espaceAlice, "hugo@argos.test", "LECTEUR");

        // 1. Première connexion de Hugo : compte créé, aucun espace.
        moi("hugo").statusCode(200).body("nomAffiche", equalTo("Hugo Blanc"));
        en("hugo").when().get("/api/v1/espaces").then().statusCode(200).body("$", empty());

        // 2. Hugo crée son espace et en devient administrateur.
        String atelier = en("hugo").contentType(ContentType.JSON).body(Map.of("nom", "Atelier de Hugo"))
                .when().post("/api/v1/espaces")
                .then().statusCode(201).body("role", equalTo("ADMINISTRATEUR"))
                .extract().path("id");

        // 3. Hugo invite Gina comme lectrice ; Gina se connecte pour la première fois et accepte.
        String invitation = Api.inviter("hugo", atelier, "gina@argos.test", "LECTEUR");
        moi("gina").statusCode(200);
        en("gina").when().post("/api/v1/invitations/" + invitation + "/acceptation")
                .then().statusCode(200).body("role", equalTo("LECTEUR"));
        en("gina").when().get("/api/v1/espaces/" + atelier).then().statusCode(200)
                .body("nom", equalTo("Atelier de Hugo"));
        en("hugo").when().get("/api/v1/espaces/" + atelier + "/membres").then().statusCode(200)
                .body("$", hasSize(2));

        // 4. Gina tente de lire l'espace d'Alice : introuvable, refus journalisé.
        String gina = Api.idCompte("gina");
        en("gina").when().get("/api/v1/espaces/" + espaceAlice).then().statusCode(404);
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement(
                        "SELECT count(*) FROM refus_acces WHERE compte_id = ?::uuid AND ressource = ? AND motif = 'NON_MEMBRE'")) {
            requete.setString(1, gina);
            requete.setString(2, "GET /api/v1/espaces/" + espaceAlice);
            try (ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                assertEquals(1, resultat.getLong(1));
            }
        }
    }
}
