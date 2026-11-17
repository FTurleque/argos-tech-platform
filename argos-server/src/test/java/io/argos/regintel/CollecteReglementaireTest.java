package io.argos.regintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Collecte quotidienne des textes du Journal officiel (tâche 2.1, REG-01) : archives JORF enregistrées
 * servies par le serveur simulé, aucune requête vers la DILA. Rejeu sans doublon (REG-01-S1), source
 * indisponible puis reprise du jour manqué (REG-01-S2), dates extraites (REG-03), fichier XXE refusé.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CollecteReglementaireTest {

    static final LocalDate LE_20 = LocalDate.of(2026, 11, 20);
    static final LocalDate LE_21 = LocalDate.of(2026, 11, 21);

    @Inject
    TestDb db;

    @Inject
    CollecteReglementaire collecte;

    @BeforeEach
    void reset() {
        db.reset();
    }

    @Test
    void reg01S1RejeuSansDoublon() {
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());

        CollecteReglementaire.Rapport premier = collecte.collecter(LE_20, Instant.now());
        assertEquals(List.of(LE_20), premier.joursCollectes(), "première collecte : le jour courant seulement");
        assertEquals(2, premier.textesNouveaux());
        assertEquals(1, premier.fichiersRefuses(), "fichier XXE refusé");
        assertEquals("NOMINAL", premier.etatSource());

        CollecteReglementaire.Rapport rejeu = collecte.rejouer(LE_20, Instant.now());
        assertEquals(0, rejeu.textesNouveaux());
        assertEquals(2, rejeu.textesDejaConnus());
        CollecteReglementaire.Rapport encore = collecte.collecter(LE_20, Instant.now());
        assertEquals(0, encore.textesNouveaux(), "le jour courant est relu sans doublon");

        assertEquals(1, db.count("SELECT count(*) FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"));
        assertEquals(2, db.count("SELECT count(*) FROM texte_officiel"));
        assertEquals(0, db.count("SELECT count(*) FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000009'"));
        assertEquals("2027-01-01", db.string("SELECT date_effet::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"),
                "REG-03-S1 : date d'effet extraite");
        assertEquals("2026-11-20", db.string("SELECT date_publication::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"));
        assertNull(db.string("SELECT date_effet::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000002'"),
                "REG-03-S2 : aucune date extraite");
        assertEquals("{\"Code général de la fonction publique\"}",
                db.string("SELECT codes_libelles::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"));
        assertTrue(StubServer.received("/jorf/JORF_20261121").isEmpty(), "le 21 n'est pas lu le 20");
    }

    @Test
    void reg01S2SourceIndisponiblePuisRepriseDuJourManque() {
        StubServer.on(ReglementaireFixtures.INDEX, Stub.status(503));

        CollecteReglementaire.Rapport echec = collecte.collecter(LE_20, Instant.now());

        assertEquals(LE_20, echec.jourEnEchec());
        assertEquals("DEGRADE", echec.etatSource());
        assertTrue(echec.erreur().contains("HTTP 503"), echec.erreur());
        CollecteReglementaire.EtatSource etat = collecte.etat();
        assertEquals("DEGRADE", etat.etat());
        assertEquals(List.of(LE_20), etat.joursEnEchec());
        assertEquals(1, etat.echecsConsecutifs());
        assertTrue(etat.mentionSource().contains("Licence Ouverte"));
        assertEquals(0, db.count("SELECT count(*) FROM texte_officiel"));

        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        CollecteReglementaire.Rapport reprise = collecte.collecter(LE_21, Instant.now());

        assertEquals(List.of(LE_20, LE_21), reprise.joursCollectes(), "le jour manqué est repris avant le jour courant");
        assertEquals(3, reprise.textesNouveaux());
        assertEquals("NOMINAL", reprise.etatSource());
        assertEquals(List.of(), collecte.etat().joursEnEchec());
        assertEquals(0, collecte.etat().echecsConsecutifs());
        assertEquals(LE_21, collecte.etat().dernierJourCollecte());
    }

    @Test
    void etatEtRejeuReservesALAdministrateurPlateforme() {
        Api.assurerCompte("paul");
        Api.en("paul").when().get("/api/v1/reglementaire/collecte").then().statusCode(403);
        Api.en("alice").when().get("/api/v1/reglementaire/collecte").then().statusCode(200)
                .body("etat", org.hamcrest.Matchers.equalTo("NOMINAL"));
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        String hier = LocalDate.now().minusDays(2).toString();
        Api.en("alice").contentType("application/json").body("{\"jour\":\"" + hier + "\"}")
                .when().post("/api/v1/reglementaire/collectes").then().statusCode(200)
                .body("joursCollectes", org.hamcrest.Matchers.contains(hier))
                .body("textesNouveaux", org.hamcrest.Matchers.equalTo(0));
        Api.en("paul").contentType("application/json").body("{}")
                .when().post("/api/v1/reglementaire/collectes").then().statusCode(403);
        Api.en("alice").contentType("application/json").body("{\"jour\":\"2999-01-01\"}")
                .when().post("/api/v1/reglementaire/collectes").then().statusCode(400);
    }
}
