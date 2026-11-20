package io.argos.api.comptes;

import static io.argos.testsupport.Api.enTantQue;
import static io.argos.testsupport.Api.moi;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.argos.workspace.CompteCree;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import io.argos.testsupport.Api;

/** ACC-02 : création du compte à la première connexion, inscription sur invitation (Q-2). */
@QuarkusTest
class PremiereConnexionTest {

    /** Observe les comptes créés pour vérifier le profil par défaut (Q-3). */
    @Singleton
    static class ObservateurComptes {
        final List<CompteCree> crees = new ArrayList<>();

        void surCreation(@Observes CompteCree evenement) {
            crees.add(evenement);
        }
    }

    @Inject
    DataSource dataSource;

    @Inject
    ObservateurComptes observateur;

    /** ACC-02-S1 : une personne invitée se connecte ; son compte est créé sans aucun espace. */
    @Test
    void premiereConnexionCreeLeCompteSansEspace() throws Exception {
        String atelier = Api.creerEspace("alice", "Espace d'Alice pour Bob");
        Api.inviter("alice", atelier, "bob@argos.test", "LECTEUR");
        assertEquals(0, comptes("bob"));

        moi("bob").statusCode(200)
                .body("nomAffiche", equalTo("Bob Durand"))
                .body("email", equalTo("bob@argos.test"))
                .body("adminPlateforme", equalTo(false));

        assertEquals(1, comptes("bob"));
        enTantQue("bob").when().get("/api/v1/espaces").then().statusCode(200).body("$", empty());
        enTantQue("bob").when().get("/api/v1/espaces/" + atelier).then().statusCode(404);
        // Profil de veille par défaut transmis à la capacité profils-veille (Q-3).
        String sujetBob = sujet("bob");
        assertTrue(observateur.crees.stream().anyMatch(evenement -> evenement.compte().sujetOidc().equals(sujetBob)
                && "developpeur".equals(evenement.profilParDefaut())));
        // Une seconde connexion ne crée pas de doublon.
        moi("bob").statusCode(200);
        assertEquals(1, comptes("bob"));
        enTantQue("bob").when().get("/api/v1/invitations").then().statusCode(200).body("$", hasSize(1));
    }

    /** ACC-02-S2 : le fournisseur refuse l'authentification ; aucun compte n'est créé. */
    @Test
    void connexionRefuseeParLeFournisseur() throws Exception {
        given().contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("client_id", "argos-server")
                .formParam("client_secret", "secret-de-test")
                .formParam("username", "dave")
                .formParam("password", "mauvais-mot-de-passe")
                .when().post(Api.urlFournisseur() + "/protocol/openid-connect/token")
                .then().statusCode(anyOf(is(400), is(401)))
                .body("error", equalTo("invalid_grant"));

        given().when().get("/api/v1/moi").then().statusCode(401);
        assertEquals(0, comptesParEmail("dave@argos.test"));
    }

    /** Q-2 : authentifiée mais sans invitation, la personne reçoit un refus explicite et aucun compte. */
    @Test
    void inscriptionSansInvitationRefusee() throws Exception {
        moi("dave").statusCode(403)
                .body("code", equalTo("INSCRIPTION_SUR_INVITATION"));
        assertEquals(0, comptesParEmail("dave@argos.test"));
    }

    /** L'administrateur plateforme initial n'a besoin d'aucune invitation et n'obtient aucun espace (INV-3). */
    @Test
    void administrateurPlateformeInitial() {
        moi("alice").statusCode(200).body("adminPlateforme", equalTo(true));
    }

    private String sujet(String utilisateur) throws Exception {
        String jeton = Api.jeton(utilisateur);
        String charge = new String(java.util.Base64.getUrlDecoder().decode(jeton.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(charge).get("sub").asText();
    }

    private long comptes(String utilisateur) throws Exception {
        return compter("SELECT count(*) FROM compte WHERE sujet_oidc = ?", sujet(utilisateur));
    }

    private long comptesParEmail(String email) throws Exception {
        return compter("SELECT count(*) FROM compte WHERE lower(email) = ?", email);
    }

    private long compter(String sql, String valeur) throws Exception {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setString(1, valeur);
            try (ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getLong(1);
            }
        }
    }
}
