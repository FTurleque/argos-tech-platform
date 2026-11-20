package io.argos.alerting;

import static io.argos.testsupport.ArtisteFixtures.JEU;
import static io.argos.testsupport.ArtisteFixtures.activer;
import static io.argos.testsupport.ArtisteFixtures.declarer;
import static io.argos.testsupport.ArtisteFixtures.enregistrement;
import static io.argos.testsupport.ArtisteFixtures.reponse;
import static io.argos.testsupport.ArtisteFixtures.suivre;
import static io.argos.testsupport.ArtisteFixtures.verifier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.ArtisteFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Rappels de date limite du profil {@code artiste} avec une horloge simulée (ajouter-profil-artiste,
 * tâche 2.4 ; ART-04, Q-4) : la tâche quotidienne est appelée avec le jour voulu, sans attendre.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class RappelsOpportunitesTest {

    /** Jour simulé ; la collecte a lieu « aujourd'hui », les rappels sont évalués à ce jour. */
    private static final LocalDate JOUR = LocalDate.of(2027, 3, 1);

    @Inject
    TestDb baseDeTest;

    @Inject
    RappelsOpportunites rappels;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        ArtisteFixtures.reinitialiser(baseDeTest, "hugo", "jules");
        StubServer.on(JEU, Stub.ok(reponse(
                enregistrement("AAP-J7", "Résidence arts visuels", "Arts visuels", "Occitanie", JOUR.plusDays(7)),
                enregistrement("AAP-J30", "Aide à la création", "Arts visuels", "France entière", JOUR.plusDays(30)),
                enregistrement("AAP-SANS", "Appel sans date", "Arts visuels", "Occitanie", null),
                enregistrement("AAP-AUTRE", "Résidence Bretagne", "Arts visuels", "Bretagne", JOUR.plusDays(7)))));
        verifier(declarer("DATA_CULTURE", StubServer.base() + JEU, "appels-residences", List.of(), List.of()));
        ArtisteFixtures.collecter();
    }

    /** ART-04-S1 : à l'échéance de rappel, l'utilisateur reçoit la date limite et le lien vers l'appel officiel. */
    @Test
    void art04S1RappelALEcheanceAvecDateLimiteEtLienOfficiel() {
        activer("hugo", "developpeur", "artiste");
        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));

        List<java.util.UUID> crees = rappels.executer(JOUR, Instant.now());

        assertEquals(2, crees.size(), "J-7 pour la résidence, J-30 pour l'aide nationale");
        String compte = Api.compte("hugo").toString();
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte WHERE nature = 'RAPPEL' AND compte_id = '" + compte
                + "' AND titre = 'Date limite dans 7 jours (2027-03-08) : Résidence arts visuels'"));
        String contenu = baseDeTest.string("SELECT contenu::text FROM alerte WHERE compte_id = '" + compte
                + "' AND titre LIKE '%Résidence arts visuels'");
        assertTrue(contenu.contains("https://www.culture.gouv.fr/AAP-J7"), contenu);
        assertTrue(contenu.contains("ne se prononce pas sur votre éligibilité"), contenu);
        List<Mail> mails = mailbox.getMailsSentTo("hugo@argos.test");
        assertTrue(mails.stream().anyMatch(mail -> mail.getText().contains("2027-03-08")
                && mail.getText().contains("https://www.culture.gouv.fr/AAP-J7")),
                () -> mails.stream().map(Mail::getText).toList().toString());
        Api.en("hugo").queryParam("canal", "EMAIL").when().get("/api/v1/alertes").then().statusCode(200);

        assertTrue(rappels.executer(JOUR, Instant.now()).isEmpty(), "aucun second message pour le même rappel");
        assertTrue(rappels.executer(JOUR.plusDays(1), Instant.now()).isEmpty(), "J-6 : pas un jour de rappel");
        assertEquals(1, rappels.executer(JOUR.plusDays(6), Instant.now()).size(), "J-1");
        assertEquals(1, rappels.executer(JOUR.plusDays(23), Instant.now()).size(), "J-7 de l'aide nationale");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM alerte WHERE titre LIKE '%sans date%' OR titre LIKE '%Bretagne%'"),
                "ni date à confirmer, ni territoire non suivi");
    }

    /** Q-4 : les jours de rappel sont paramétrables par l'utilisateur. */
    @Test
    void lesJoursDeRappelSontParametrables() {
        activer("jules", "artiste");
        Api.en("jules").contentType(ContentType.JSON)
                .body(Map.of("disciplines", List.of("arts-visuels"), "territoires", List.of("occitanie"),
                        "rappelsJours", List.of(3, 3, 10)))
                .when().put("/api/v1/profils/artiste/suivis").then().statusCode(200);
        Api.en("jules").when().get("/api/v1/profils/artiste/suivis").then().statusCode(200)
                .body("rappelsJours", org.hamcrest.Matchers.contains(10, 3))
                .body("rappelsParDefaut", org.hamcrest.Matchers.equalTo(false));

        assertTrue(rappels.executer(JOUR, Instant.now()).isEmpty(), "J-7 et J-30 ne sont plus des jours de rappel");
        assertEquals(1, rappels.executer(JOUR.plusDays(4), Instant.now()).size(), "J-3");
    }

    /** Un utilisateur qui désactive le profil artiste ne reçoit plus de rappel (PRF-04). */
    @Test
    void sansProfilActifAucunRappel() {
        activer("hugo", "developpeur", "artiste");
        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));
        activer("hugo", "developpeur");

        assertTrue(rappels.executer(JOUR, Instant.now()).isEmpty());
    }
}
