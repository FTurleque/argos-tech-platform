package io.argos.chain;

import static io.argos.testsupport.ArtisteFixtures.JEU;
import static io.argos.testsupport.ArtisteFixtures.activer;
import static io.argos.testsupport.ArtisteFixtures.declarer;
import static io.argos.testsupport.ArtisteFixtures.enregistrement;
import static io.argos.testsupport.ArtisteFixtures.reponse;
import static io.argos.testsupport.ArtisteFixtures.suivre;
import static io.argos.testsupport.ArtisteFixtures.verifier;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.argos.alerting.RappelsOpportunites;
import io.argos.testsupport.Api;
import io.argos.testsupport.ArtisteFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Parcours de bout en bout du profil {@code artiste} (ajouter-profil-artiste, tâche 5.2) : un utilisateur
 * développeur active le profil artiste, choisit « arts visuels » et « Occitanie », se voit restituer un
 * appel correspondant puis reçoit son rappel J-7 ; sa veille développeur reste identique. Seule l'API
 * data.culture.gouv.fr est simulée (serveur HTTP local).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ParcoursArtisteTest {

    @Inject
    TestDb baseDeTest;

    @Inject
    RappelsOpportunites rappels;

    @Test
    void developpeurActiveArtisteRecoitUnAppelPuisSonRappelSansChangerSaVeille() {
        baseDeTest.reset();
        ArtisteFixtures.reinitialiser(baseDeTest, "hugo");
        LocalDate aujourdhui = LocalDate.now(ZoneId.of("Europe/Paris"));

        // 1. Utilisateur développeur : état de sa veille avant l'activation du profil artiste.
        activer("hugo", "developpeur");
        String rubriquesAvant = Api.en("hugo").get("/api/v1/preferences/rubriques").then().statusCode(200)
                .extract().asString();
        String veilleAvant = Api.en("hugo").get("/api/v1/me/watch").then().statusCode(200).extract().asString();
        String abonnementsAvant = Api.en("hugo").get("/api/v1/subscriptions").then().statusCode(200)
                .extract().asString();
        long signauxAvant = baseDeTest.count("SELECT count(*) FROM signal");

        // 2. L'administratrice plateforme déclare et vérifie la source data.culture.gouv.fr (simulée).
        StubServer.on(JEU, Stub.ok(reponse(
                enregistrement("AAP-OCC-7", "Résidence arts visuels en Occitanie", "Arts visuels", "Occitanie",
                        aujourdhui.plusDays(7)))));
        verifier(declarer("DATA_CULTURE", StubServer.base() + JEU, "appels-residences", List.of(), List.of()));

        // 3. Activation du profil artiste et choix des suivis.
        activer("hugo", "developpeur", "artiste");
        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));

        // 4. Collecte : l'appel correspondant est restitué avec sa date limite.
        ArtisteFixtures.collecter();
        Api.en("hugo").get("/api/v1/profils/artiste/opportunites").then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].titre", equalTo("Résidence arts visuels en Occitanie"))
                .body("items[0].dateLimite", equalTo(aujourdhui.plusDays(7).toString()))
                .body("items[0].lienOfficiel", equalTo("https://www.culture.gouv.fr/AAP-OCC-7"));

        // 5. Tâche quotidienne des rappels : J-7 aujourd'hui.
        List<UUID> crees = rappels.executer(aujourdhui, Instant.now());
        assertEquals(1, crees.size());
        Api.en("hugo").get("/api/v1/alertes").then().statusCode(200)
                .body("findAll { it.nature == 'RAPPEL' }.titre", contains("Date limite dans 7 jours ("
                        + aujourdhui.plusDays(7) + ") : Résidence arts visuels en Occitanie"));

        // 6. La veille développeur est inchangée : rubriques du profil développeur, veille, abonnements, signaux.
        Api.en("hugo").get("/api/v1/preferences/rubriques").then().statusCode(200)
                .body("findAll { it.profil == 'developpeur' }.identifiant",
                        contains("securite", "versions", "fins-de-support"))
                .body("findAll { it.profil == 'artiste' }", hasSize(5));
        String rubriquesDeveloppeur = Api.en("hugo").get("/api/v1/preferences/rubriques").then().extract()
                .jsonPath().getList("findAll { it.profil == 'developpeur' }").toString();
        assertEquals(io.restassured.path.json.JsonPath.from(rubriquesAvant).getList("$").toString(),
                rubriquesDeveloppeur);
        assertEquals(veilleAvant, Api.en("hugo").get("/api/v1/me/watch").then().statusCode(200).extract().asString());
        assertEquals(abonnementsAvant, Api.en("hugo").get("/api/v1/subscriptions").then().statusCode(200)
                .extract().asString());
        assertEquals(signauxAvant, baseDeTest.count("SELECT count(*) FROM signal"), "une opportunité n'est pas un signal");
    }
}
