package io.argos.api.comptes;

import static io.argos.testsupport.Api.en;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import jakarta.inject.Inject;

import io.argos.testsupport.TestDb;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import io.argos.testsupport.Api;

/** ACC-05 : invitations et révocation. */
@QuarkusTest
class InvitationsTest {

    @Inject
    TestDb db;

    /** ACC-05-S1 : invité comme lecteur, l'utilisateur accepte et voit l'espace en lecture seule. */
    @Test
    void invitationAccepteeDonneUnAccesEnLectureSeule() {
        String atelier = Api.creerEspace("alice", "Atelier");
        String invitation = Api.inviter("alice", atelier, "Erin@Argos.test", "LECTEUR");

        en("alice").when().get("/api/v1/espaces/" + atelier + "/invitations").then().statusCode(200)
                .body("find { it.id == '" + invitation + "' }.etat", equalTo("EN_ATTENTE"))
                .body("find { it.id == '" + invitation + "' }.email", equalTo("erin@argos.test"));
        en("erin").when().get("/api/v1/invitations").then().statusCode(200).body("id", hasItem(invitation));

        en("erin").when().post("/api/v1/invitations/" + invitation + "/acceptation")
                .then().statusCode(200)
                .body("id", equalTo(atelier))
                .body("role", equalTo("LECTEUR"));

        // Lecture autorisée.
        en("erin").when().get("/api/v1/espaces/" + atelier).then().statusCode(200).body("nom", equalTo("Atelier"));
        en("erin").when().get("/api/v1/espaces/" + atelier + "/membres").then().statusCode(200);
        // Écriture refusée : le lecteur n'invite pas et ne gère pas les rôles.
        en("erin").contentType(ContentType.JSON).body(Map.of("email", "x@argos.test", "role", "LECTEUR"))
                .when().post("/api/v1/espaces/" + atelier + "/invitations")
                .then().statusCode(403);
        en("erin").when().get("/api/v1/espaces/" + atelier + "/invitations").then().statusCode(403);
        // Une invitation ne sert qu'une fois : 409, conformément au contrat OpenAPI.
        en("erin").when().post("/api/v1/invitations/" + invitation + "/acceptation").then().statusCode(409)
                .body("code", equalTo("INVITATION_INACTIVE"));
        en("alice").when().get("/api/v1/espaces/" + atelier + "/invitations").then().statusCode(200)
                .body("find { it.id == '" + invitation + "' }.etat", equalTo("ACCEPTEE"));
    }

    /** ACC-05-S2 : la révocation s'applique dès la requête suivante et retire le membre. */
    @Test
    void revocationImmediate() {
        String atelier = Api.creerEspace("alice", "Atelier révocable");
        Api.ajouterMembre("alice", atelier, "erin", "LECTEUR");
        String erin = Api.idCompte("erin");
        en("erin").when().get("/api/v1/espaces/" + atelier).then().statusCode(200);

        en("alice").when().delete("/api/v1/espaces/" + atelier + "/membres/" + erin).then().statusCode(204);

        en("erin").when().get("/api/v1/espaces/" + atelier).then().statusCode(404);
        en("erin").when().get("/api/v1/espaces").then().statusCode(200).body("id", not(hasItem(atelier)));
        en("alice").when().get("/api/v1/espaces/" + atelier + "/membres").then().statusCode(200)
                .body("compteId", not(hasItem(erin)));
    }

    /** Une invitation adressée à une autre personne ne peut pas être acceptée. */
    @Test
    void invitationDUnTiersIntrouvable() {
        String atelier = Api.creerEspace("alice", "Atelier réservé");
        Api.ajouterMembre("alice", atelier, "erin", "LECTEUR");
        String pourUnTiers = Api.inviter("alice", atelier, "personne-inconnue@argos.test", "ADMINISTRATEUR");

        String erin = Api.idCompte("erin");
        long refusAvant = refus(erin);
        en("erin").when().post("/api/v1/invitations/" + pourUnTiers + "/acceptation").then().statusCode(404);
        en("erin").when().post("/api/v1/invitations/" + java.util.UUID.randomUUID() + "/acceptation").then().statusCode(404);
        assertEquals(refusAvant, refus(erin), "une invitation introuvable n'est pas un refus d'accès à un espace");
    }

    private long refus(String compte) {
        return db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + compte + "'");
    }

    /** Une invitation révoquée ne peut plus être acceptée. */
    @Test
    void invitationRevoquee() {
        Api.assurerCompte("erin");
        String atelier = Api.creerEspace("alice", "Atelier fermé");
        String invitation = Api.inviter("alice", atelier, "erin@argos.test", "CONTRIBUTEUR");
        en("alice").when().delete("/api/v1/espaces/" + atelier + "/invitations/" + invitation).then().statusCode(204);

        en("erin").when().post("/api/v1/invitations/" + invitation + "/acceptation").then().statusCode(409)
                .body("code", equalTo("INVITATION_INACTIVE"));
        en("erin").when().get("/api/v1/espaces/" + atelier).then().statusCode(404);
    }
}
