package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * Limite de débit des requêtes de veille web qui déclenchent une récupération externe, avec une
 * limite basse de test : ajout d'une source, réussi ou refusé, et collecte à la demande partagent
 * la limite du compte ; au-delà, 429 au format {@code Erreur}, refus journalisé.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
@TestProfile(LimiteDebitVeilleWebTest.LimiteBasse.class)
class LimiteDebitVeilleWebTest {

    /** 3 requêtes par minute (10 en exploitation). */
    public static class LimiteBasse implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.veille-web.debit.requetes-par-minute", "3");
        }
    }

    @Inject
    TestDb db;

    @BeforeEach
    void preparer() {
        db.reset();
    }

    private static ValidatableResponse ajouter(String user, String adresse) {
        return Api.en(user).contentType(ContentType.JSON)
                .body(Map.of("portee", "PERSONNELLE", "type", "FLUX", "adresse", adresse))
                .when().post("/api/v1/veille-web/sources").then();
    }

    @Test
    void ajoutsEtCollectesLimitesParCompte() {
        StubServer.on("/limite.xml", new Stub(200, """
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom"><title>L</title><id>urn:l</id><updated>2026-11-15T10:00:00Z</updated></feed>
                """, Map.of("Content-Type", "application/atom+xml"), 0));
        UUID nina = Api.compte("nina");

        // Un ajout refusé compte comme un ajout réussi.
        ajouter("nina", "http://192.168.1.1/flux").statusCode(400);
        String id = ajouter("nina", StubServer.base() + "/limite.xml").statusCode(201).extract().path("id");
        Api.en("nina").when().post("/api/v1/veille-web/sources/" + id + "/collectes").then().statusCode(200);

        ajouter("nina", "http://192.168.1.2/flux").statusCode(429)
                .header("Retry-After", "60")
                .body("code", equalTo("LIMITE_DEBIT"))
                .body("message", equalTo("Nombre maximal de requêtes par minute atteint, réessayez plus tard."));
        Api.en("nina").when().post("/api/v1/veille-web/sources/" + id + "/collectes").then().statusCode(429)
                .body("code", equalTo("LIMITE_DEBIT"));

        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + nina
                + "' AND motif = 'LIMITE_DEBIT' AND ressource = 'POST /api/v1/veille-web/sources'"));
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + nina
                + "' AND motif = 'LIMITE_DEBIT' AND ressource = 'POST /api/v1/veille-web/sources/" + id + "/collectes'"));
        assertEquals(1, db.count("SELECT count(*) FROM source_web"), "aucune source créée au-delà de la limite");

        // La limite est propre à chaque compte ; les lectures ne sont pas limitées.
        Api.assurerCompte("oscar");
        ajouter("oscar", "http://192.168.1.3/flux").statusCode(400);
        Api.en("nina").when().get("/api/v1/veille-web/sources").then().statusCode(200);
    }
}
