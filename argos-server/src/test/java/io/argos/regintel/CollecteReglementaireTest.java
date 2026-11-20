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
 * indisponible puis reprise du jour manqué (REG-01-S2), dates extraites (REG-03), fichier XXE refusé,
 * archive publiée après le passage de la collecte, archive en erreur permanente, jour abandonné.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CollecteReglementaireTest {

    static final LocalDate LE_18 = LocalDate.of(2026, 11, 18);
    static final LocalDate LE_19 = LocalDate.of(2026, 11, 19);
    static final LocalDate LE_20 = LocalDate.of(2026, 11, 20);
    static final LocalDate LE_21 = LocalDate.of(2026, 11, 21);
    static final String ARCHIVE_20 = "/jorf/JORF_20261120-213004.tar.gz";

    @Inject
    TestDb baseDeTest;

    @Inject
    CollecteReglementaire collecte;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    @Test
    void reg01S1RejeuSansDoublon() {
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());

        CollecteReglementaire.Rapport premier = collecte.collecter(LE_20, Instant.now());
        assertEquals(List.of(LE_18, LE_19, LE_20), premier.joursCollectes(), "première collecte : la fenêtre de relecture");
        assertEquals(2, premier.textesNouveaux());
        assertEquals(1, premier.fichiersRefuses(), "fichier XXE refusé");
        assertEquals("NOMINAL", premier.etatSource());

        CollecteReglementaire.Rapport rejeu = collecte.rejouer(LE_20, Instant.now());
        assertEquals(0, rejeu.textesNouveaux());
        assertEquals(2, rejeu.textesDejaConnus());
        CollecteReglementaire.Rapport encore = collecte.collecter(LE_20, Instant.now());
        assertEquals(0, encore.textesNouveaux(), "le jour courant est relu sans doublon");
        assertEquals(2, StubServer.received(ARCHIVE_20).size(), "relecture : l'archive déjà lue n'est pas retéléchargée");

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM texte_officiel"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000009'"));
        assertEquals("2027-01-01", baseDeTest.string(
                        "SELECT date_effet::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"),
                "REG-03-S1 : date d'effet extraite");
        assertEquals("2026-11-20", baseDeTest.string(
                "SELECT date_publication::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"));
        assertNull(baseDeTest.string("SELECT date_effet::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000002'"),
                "REG-03-S2 : aucune date extraite");
        assertEquals("{\"Code général de la fonction publique\"}",
                baseDeTest.string("SELECT codes_libelles::text FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"));
        assertTrue(StubServer.received("/jorf/JORF_20261121").isEmpty(), "le 21 n'est pas lu le 20");
    }

    @Test
    void reg01S2SourceIndisponiblePuisRepriseDuJourManque() {
        StubServer.on(ReglementaireFixtures.INDEX, Stub.status(503));

        CollecteReglementaire.Rapport echec = collecte.collecter(LE_20, Instant.now());

        assertEquals(LE_18, echec.jourEnEchec(), "premier jour de la fenêtre non collecté");
        assertEquals("DEGRADE", echec.etatSource());
        assertTrue(echec.erreur().contains("HTTP 503"), echec.erreur());
        CollecteReglementaire.EtatSource etat = collecte.etat();
        assertEquals("DEGRADE", etat.etat());
        assertEquals(List.of(LE_18), etat.joursEnEchec());
        assertEquals(1, etat.echecsConsecutifs());
        assertTrue(etat.mentionSource().contains("Licence Ouverte"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM texte_officiel"));

        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        CollecteReglementaire.Rapport reprise = collecte.collecter(LE_21, Instant.now());

        assertEquals(List.of(LE_18, LE_19, LE_20, LE_21), reprise.joursCollectes(),
                "le jour manqué est repris avant la fenêtre de relecture");
        assertEquals(3, reprise.textesNouveaux());
        assertEquals("NOMINAL", reprise.etatSource());
        assertEquals(List.of(), collecte.etat().joursEnEchec());
        assertEquals(0, collecte.etat().echecsConsecutifs());
        assertEquals(LE_21, collecte.etat().dernierJourCollecte());
    }

    @Test
    void archivePublieeApresLePassageDeLaCollecteLueAuPassageSuivant() {
        // Le 20 à 06:30, l'archive du 20 (publiée à 21:30) n'est pas encore dans l'index.
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        StubServer.on(ReglementaireFixtures.INDEX, Stub.ok(index("JORF_20261119-213012.tar.gz")));
        CollecteReglementaire.Rapport le20 = collecte.collecter(LE_20, Instant.now());
        assertEquals(0, le20.textesNouveaux());
        assertEquals(LE_20, collecte.etat().dernierJourCollecte());

        // Le 21 à 06:30, l'archive du 20 est publiée : elle est lue, le jour 20 restant dans la fenêtre.
        StubServer.on(ReglementaireFixtures.INDEX,
                Stub.ok(index("JORF_20261119-213012.tar.gz", "JORF_20261120-213004.tar.gz")));
        CollecteReglementaire.Rapport le21 = collecte.collecter(LE_21, Instant.now());
        assertEquals(2, le21.textesNouveaux(), "archive tardive du 20 lue le 21");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM texte_officiel WHERE identifiant = 'JORFTEXT000099000001'"));
        assertEquals("{JORF_20261120-213004.tar.gz}",
                baseDeTest.string("SELECT archives_lues::text FROM reg_collecte_jour WHERE jour = '2026-11-20'"));
        assertEquals(1, StubServer.received("/jorf/JORF_20261119").size(), "archive du 19 lue une seule fois");
    }

    @Test
    void archiveEnErreurPermanenteEcarteeSansBloquerLesJoursSuivants() {
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        StubServer.on(ARCHIVE_20, Stub.status(404));

        CollecteReglementaire.Rapport rapport = collecte.collecter(LE_21, Instant.now());

        assertEquals(List.of(LE_19, LE_20, LE_21), rapport.joursCollectes(), "le jour suivant est collecté");
        assertNull(rapport.jourEnEchec());
        assertEquals(1, rapport.textesNouveaux(), "texte du 21");
        assertEquals(1, rapport.archivesEcartees().size());
        assertTrue(rapport.archivesEcartees().get(0).startsWith("JORF_20261120-213004.tar.gz : HTTP 404"),
                rapport.archivesEcartees().get(0));
        assertTrue(baseDeTest.string("SELECT derniere_erreur FROM reg_collecte_jour WHERE jour = '2026-11-20'").contains("HTTP 404"));

        // L'archive revient : relue tant que le jour reste dans la fenêtre de relecture.
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        assertEquals(2, collecte.collecter(LE_21, Instant.now()).textesNouveaux());
    }

    @Test
    void jourEnEchecRepeteAbandonnePuisRejoue() {
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        StubServer.on(ARCHIVE_20, Stub.status(503));

        for (int tentative = 1; tentative < 5; tentative++) {
            CollecteReglementaire.Rapport rapport = collecte.collecter(LE_21, Instant.now());
            assertEquals(LE_20, rapport.jourEnEchec(), "tentative " + tentative);
            assertEquals(List.of(LE_19), rapport.joursCollectes(), "les jours suivants attendent le jour manqué");
        }
        CollecteReglementaire.Rapport cinquieme = collecte.collecter(LE_21, Instant.now());
        assertEquals(List.of(LE_20), cinquieme.joursAbandonnes(), "abandon après 5 échecs consécutifs");
        assertEquals(List.of(LE_19, LE_21), cinquieme.joursCollectes(), "la collecte passe au jour suivant");
        assertEquals(List.of(LE_20), collecte.etat().joursAbandonnes());
        assertEquals(List.of(), collecte.etat().joursEnEchec());

        CollecteReglementaire.Rapport suivante = collecte.collecter(LE_21, Instant.now());
        assertEquals(List.of(LE_19, LE_21), suivante.joursCollectes(), "jour abandonné ignoré");
        assertEquals("NOMINAL", suivante.etatSource());

        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        CollecteReglementaire.Rapport rejeu = collecte.rejouer(LE_20, Instant.now());
        assertEquals(2, rejeu.textesNouveaux(), "jour abandonné rejoué par l'administrateur plateforme");
        assertEquals(List.of(), collecte.etat().joursAbandonnes());
    }

    @Test
    void jourEnEchecHorsDeLaFenetreDeRepriseAbandonne() {
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
        baseDeTest.exec("INSERT INTO reg_collecte_jour (jour, etat, derniere_erreur, modifie_le)"
                + " VALUES ('2026-09-01', 'EN_ECHEC', 'HTTP 503', now())");

        CollecteReglementaire.Rapport rapport = collecte.collecter(LE_21, Instant.now());

        assertEquals(LE_19, rapport.joursCollectes().get(0), "le jour sorti de la fenêtre de reprise n'est plus repris");
        assertEquals(List.of(LocalDate.of(2026, 9, 1)), collecte.etat().joursAbandonnes());
        assertEquals(List.of(), collecte.etat().joursEnEchec());
    }

    private static String index(String... archives) {
        StringBuilder html = new StringBuilder("<html><body><pre>\n");
        for (String archive : archives) {
            html.append("<a href=\"").append(archive).append("\">").append(archive).append("</a>\n");
        }
        return html.append("</pre></body></html>").toString();
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
