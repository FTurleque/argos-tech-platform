package io.argos.chain;

import static io.argos.testsupport.ArtisteFixtures.JEU;
import static io.argos.testsupport.ArtisteFixtures.activer;
import static io.argos.testsupport.ArtisteFixtures.declarer;
import static io.argos.testsupport.ArtisteFixtures.enregistrement;
import static io.argos.testsupport.ArtisteFixtures.reponse;
import static io.argos.testsupport.ArtisteFixtures.suivre;
import static io.argos.testsupport.ArtisteFixtures.verifier;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.ArtisteFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.argos.workspace.suivi.SuivisProfil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Profil de veille {@code artiste} de bout en bout côté serveur (ajouter-profil-artiste, tâches 2.1,
 * 2.2, 2.3, 3.1 et 3.2) : jetons réels du royaume Keycloak de test, RLS active sous {@code argos_app},
 * sources data.culture.gouv.fr et RSS simulées par {@link StubServer}.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ProfilArtisteTest {

    private static final String ATOM = "application/atom+xml";

    @Inject
    TestDb baseDeTest;

    @Inject
    ContexteBase base;

    @Inject
    SuivisProfil suivis;

    private LocalDate aujourdhui;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        ArtisteFixtures.reinitialiser(baseDeTest, "hugo", "jules");
        aujourdhui = LocalDate.now(ZoneId.of("Europe/Paris"));
    }

    // ------------------------------------------------------------------ ART-01

    /** ART-01-S1 : un développeur qui active le profil artiste voit les cinq rubriques artiste en plus des siennes. */
    @Test
    void art01S1ActivationAjouteLesCinqRubriquesArtiste() {
        activer("hugo", "developpeur");
        Api.enTantQue("hugo").when().get("/api/v1/preferences/rubriques").then().statusCode(200)
                .body("profil", not(hasItem("artiste")))
                .body("identifiant", contains("securite", "versions", "fins-de-support"));

        activer("hugo", "developpeur", "artiste");

        Api.enTantQue("hugo").when().get("/api/v1/preferences/rubriques").then().statusCode(200)
                .body("identifiant", contains("securite", "versions", "fins-de-support", "appels-residences",
                        "aides-creation", "droits-remunerations", "statut-social-fiscal", "evenements"))
                .body("findAll { it.profil == 'artiste' }", hasSize(5));
        Api.enTantQue("hugo").when().get("/api/v1/catalogue/profils").then().statusCode(200)
                .body("find { it.identifiant == 'artiste' }.gardeFous", hasItem(containsString("éligibilité")));
    }

    /** PRF-04 : l'activation par un utilisateur ne change ni les profils ni les rubriques d'un autre. */
    @Test
    void lActivationNAAucunEffetSurLesAutresUtilisateurs() {
        activer("jules", "developpeur");
        activer("hugo", "developpeur", "artiste");
        Api.enTantQue("jules").when().get("/api/v1/preferences").then().statusCode(200)
                .body("profilsActifs", contains("developpeur"));
        Api.enTantQue("jules").when().get("/api/v1/preferences/rubriques").then().statusCode(200)
                .body("profil", not(hasItem("artiste")));
        Api.enTantQue("jules").when().get("/api/v1/profils/artiste/opportunites").then().statusCode(409)
                .body("code", equalTo("PROFIL_INACTIF"));
    }

    // ------------------------------------------------------------------ ART-02

    /** ART-02-S1 : « arts visuels » et « Occitanie » sont enregistrés. */
    @Test
    void art02S1ChoixEnregistres() {
        activer("hugo", "developpeur", "artiste");
        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/opportunites").then().statusCode(200)
                .body("suivisRequis", equalTo(true)).body("items", hasSize(0));

        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));

        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/suivis").then().statusCode(200)
                .body("disciplines", contains("arts-visuels"))
                .body("territoires", contains("occitanie"))
                .body("rappelsJours", contains(30, 7, 1))
                .body("rappelsParDefaut", equalTo(true))
                .body("complets", equalTo(true));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM suivi_profil WHERE profil = 'artiste' AND compte_id = '"
                + Api.compte("hugo") + "'"));
        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/listes").then().statusCode(200)
                .body("disciplines", hasSize(6)).body("territoires.identifiant", hasItem("occitanie"));
    }

    /** ART-02-S2 : une discipline absente de la liste est refusée avec la liste des disciplines disponibles. */
    @Test
    void art02S2DisciplineHorsListeRefusee() {
        activer("hugo", "developpeur", "artiste");
        Api.enTantQue("hugo").contentType(ContentType.JSON)
                .body(Map.of("disciplines", List.of("sculpture-sur-glace"), "territoires", List.of("occitanie")))
                .when().put("/api/v1/profils/artiste/suivis").then().statusCode(400)
                .body("code", equalTo("DISCIPLINE_INCONNUE"))
                .body("message", containsString("Arts visuels (arts-visuels)"))
                .body("message", containsString("Design et métiers d'art (design-metiers-art)"));
        Api.enTantQue("hugo").contentType(ContentType.JSON)
                .body(Map.of("disciplines", List.of("musique"), "territoires", List.of()))
                .when().put("/api/v1/profils/artiste/suivis").then().statusCode(400)
                .body("code", equalTo("TERRITOIRE_INCONNU"));
        Api.enTantQue("hugo").contentType(ContentType.JSON)
                .body(Map.of("disciplines", List.of("musique"), "territoires", List.of("occitanie"),
                        "rappelsJours", List.of(400)))
                .when().put("/api/v1/profils/artiste/suivis").then().statusCode(400);
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM suivi_profil"));
    }

    /** Les suivis sont réservés à un utilisateur dont le profil artiste est actif. */
    @Test
    void lesSuivisExigentLeProfilActif() {
        activer("hugo", "developpeur");
        Api.enTantQue("hugo").contentType(ContentType.JSON)
                .body(Map.of("disciplines", List.of("musique"), "territoires", List.of("occitanie")))
                .when().put("/api/v1/profils/artiste/suivis").then().statusCode(409)
                .body("code", equalTo("PROFIL_INACTIF"));
    }

    /** RLS sous argos_app : un compte ne lit ni n'écrit les suivis d'un autre compte. */
    @Test
    void lesSuivisSontCloisonnesParCompteSousArgosApp() {
        activer("hugo", "developpeur", "artiste");
        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));
        UUID hugo = Api.compte("hugo");
        UUID jules = Api.compte("jules");

        long vusParJules = base.executer(new Contexte(jules, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("SELECT current_user, count(*) FROM suivi_profil GROUP BY 1");
                    ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? resultat.getLong(2) : 0L;
            }
        });
        assertEquals(0L, vusParJules);
        long vusParHugo = base.executer(new Contexte(hugo, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("SELECT current_user, count(*) FROM suivi_profil GROUP BY 1");
                    ResultSet resultat = requete.executeQuery()) {
                assertTrue(resultat.next());
                assertEquals("argos_app", resultat.getString(1));
                return resultat.getLong(2);
            }
        });
        assertEquals(2L, vusParHugo);
        assertThrows(IllegalStateException.class, () -> base.executer(new Contexte(jules, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO suivi_profil (compte_id, profil, type_objet, valeurs) VALUES (?, 'artiste', 'x', '{a}')
                    """)) {
                requete.setObject(1, hugo);
                return requete.executeUpdate();
            }
        }), "écrire les suivis d'un autre compte est refusé par la politique RLS");
        long modifies = base.executer(new Contexte(jules, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM suivi_profil WHERE compte_id = ?")) {
                requete.setObject(1, hugo);
                return (long) requete.executeUpdate();
            }
        });
        assertEquals(0L, modifies);
    }

    /** Deux remplacements simultanés des suivis d'un même compte s'appliquent l'un après l'autre, sans erreur. */
    @Test
    void deuxRemplacementsSimultanesDesSuivisNEchouentPas() throws Exception {
        activer("hugo", "developpeur", "artiste");
        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));
        UUID hugo = Api.compte("hugo");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<SuivisProfil.Suivis>> resultats = new java.util.ArrayList<>();
            for (int rang = 0; rang < 8; rang++) {
                String discipline = rang % 2 == 0 ? "musique" : "arts-visuels";
                resultats.add(pool.submit(() -> {
                    depart.await();
                    return suivis.remplacer(hugo, "artiste",
                            Map.of("discipline", List.of(discipline), "territoire", List.of("france")));
                }));
            }
            depart.countDown();
            for (java.util.concurrent.Future<SuivisProfil.Suivis> resultat : resultats) {
                assertEquals(List.of("france"), resultat.get(30, java.util.concurrent.TimeUnit.SECONDS).valeurs("territoire"));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, suivis.lire(hugo, "artiste").valeurs("discipline").size());
    }

    // ------------------------------------------------------------------ ART-03

    /** ART-03-S1 et S2 : seules les opportunités ouvertes de la discipline et du territoire suivis sont restituées. */
    @Test
    void art03RapprochementParDisciplineTerritoireEtDateLimite() {
        StubServer.programmer(JEU, Stub.succes(reponse(
                enregistrement("AAP-OCC", "Résidence arts visuels Occitanie", "Arts visuels", "Occitanie",
                        aujourdhui.plusDays(20)),
                enregistrement("AAP-PASSE", "Appel clos", "Arts visuels", "Occitanie", aujourdhui.minusDays(1)),
                enregistrement("AAP-BZH", "Résidence Bretagne", "Arts visuels", "Bretagne", aujourdhui.plusDays(10)),
                enregistrement("AAP-MUS", "Aide musique", "Musique", "Occitanie", aujourdhui.plusDays(10)),
                enregistrement("AAP-NAT", "Aide nationale", "Arts plastiques", "National", aujourdhui.plusDays(40)),
                enregistrement("AAP-SANS", "Résidence sans date", "Arts visuels", "Occitanie", null))));
        String source = declarer("DATA_CULTURE", StubServer.base() + JEU, "appels-residences", List.of(), List.of());
        verifier(source);
        ArtisteFixtures.collecter();
        assertEquals(6, baseDeTest.count("SELECT count(*) FROM opportunite"));

        activer("hugo", "developpeur", "artiste");
        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));

        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/opportunites").then().statusCode(200)
                .body("suivisRequis", equalTo(false))
                .body("items.titre", contains("Résidence arts visuels Occitanie", "Aide nationale", "Résidence sans date"))
                .body("items[0].dateLimite", equalTo(aujourdhui.plusDays(20).toString()))
                .body("items[0].dateAConfirmer", equalTo(false))
                .body("items[0].rubrique", equalTo("appels-residences"))
                .body("items[0].lienOfficiel", equalTo("https://www.culture.gouv.fr/AAP-OCC"))
                .body("items[0].explication", containsString("dans 20 jours"))
                .body("items[1].territoires", contains("france"))
                .body("items[2].dateAConfirmer", equalTo(true))
                .body("items.titre", not(hasItem("Appel clos")));
    }

    // ------------------------------------------------------------------ ART-05

    /** ART-05-S1 : une source sans conditions d'usage enregistrées n'est pas collectée et l'indique. */
    @Test
    void art05S1SourceNonVerifieeNonCollectee() {
        StubServer.programmer("/organisme/rss", new Stub(200, VeilleWebTest.atom("Appels de l'organisme",
                VeilleWebTest.entree("Résidence de création", "https://organisme.test/residence", "Critères")),
                Map.of("Content-Type", ATOM)));
        String source = declarer("FLUX", StubServer.base() + "/organisme/rss", "appels-residences",
                List.of("arts-visuels"), List.of("occitanie"));

        Api.enTantQue("alice").when().post("/api/v1/profils/artiste/collectes").then().statusCode(200)
                .body("find { it.sourceId == '" + source + "' }.etat", equalTo("CONDITIONS_A_VERIFIER"));

        assertTrue(StubServer.received("/organisme/rss").isEmpty(), "aucune requête vers une source non vérifiée");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM opportunite"));
        Api.enTantQue("alice").when().get("/api/v1/profils/artiste/sources").then().statusCode(200)
                .body("find { it.id == '" + source + "' }.etat", equalTo("CONDITIONS_A_VERIFIER"))
                .body("find { it.id == '" + source + "' }.licence", nullValue());

        verifier(source);
        ArtisteFixtures.collecter();

        assertEquals(1, StubServer.received("/organisme/rss").size());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM opportunite WHERE date_limite IS NULL"
                + " AND 'arts-visuels' = ANY (disciplines) AND 'occitanie' = ANY (territoires)"));
        Api.enTantQue("alice").when().get("/api/v1/profils/artiste/sources").then().statusCode(200)
                .body("find { it.id == '" + source + "' }.etat", equalTo("ACTIVE"))
                .body("find { it.id == '" + source + "' }.licence", equalTo("Licence Ouverte Etalab 2.0"));
        ArtisteFixtures.collecter();
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM opportunite"), "seconde collecte dédupliquée");
    }

    /** Le registre des sources est réservé à l'administrateur plateforme ; refus journalisé. */
    @Test
    void lesSourcesSontReserveesALAdministrateurPlateforme() {
        Api.compte("hugo");
        long refusAvant = baseDeTest.count("SELECT count(*) FROM refus_acces");
        Api.enTantQue("hugo").contentType(ContentType.JSON)
                .body(Map.of("type", "FLUX", "adresse", "https://organisme.test/rss", "rubrique", "evenements"))
                .when().post("/api/v1/profils/artiste/sources").then().statusCode(403);
        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/sources").then().statusCode(403);
        Api.enTantQue("hugo").when().post("/api/v1/profils/artiste/collectes").then().statusCode(403);
        assertEquals(refusAvant + 3, baseDeTest.count("SELECT count(*) FROM refus_acces"));
    }

    /** SSRF : hôte non admis pour data.culture.gouv.fr refusé ; adresse locale refusée à la collecte. */
    @Test
    void lesAdressesDesSourcesSontControlees() {
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body(Map.of("type", "DATA_CULTURE", "adresse", "https://exemple.test/api", "rubrique", "aides-creation"))
                .when().post("/api/v1/profils/artiste/sources").then().statusCode(400)
                .body("code", equalTo("HOTE_NON_ADMIS"));
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body(Map.of("type", "FLUX", "adresse", "file:///etc/passwd", "rubrique", "aides-creation"))
                .when().post("/api/v1/profils/artiste/sources").then().statusCode(400);
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body(Map.of("type", "FLUX", "adresse", "https://organisme.test/rss", "rubrique", "inconnue"))
                .when().post("/api/v1/profils/artiste/sources").then().statusCode(400)
                .body("code", equalTo("RUBRIQUE_INCONNUE"));
        String locale = declarer("FLUX", "http://127.0.0.1:9/rss", "evenements", List.of("musique"), List.of("france"));
        verifier(locale);
        Api.enTantQue("alice").when().post("/api/v1/profils/artiste/collectes").then().statusCode(200)
                .body("find { it.sourceId == '" + locale + "' }.etat", equalTo("EN_ERREUR"))
                .body("find { it.sourceId == '" + locale + "' }.erreur", containsString("ADRESSE_INTERDITE"));
    }

    // ------------------------------------------------------------------ ART-06

    /** ART-06-S1 : le résumé présente les critères tels qu'énoncés, renvoie au texte officiel et ne juge pas l'éligibilité. */
    @Test
    void art06S1ResumeSansAvisDEligibilite() {
        StubServer.programmer(JEU, Stub.succes(reponse("""
                {"identifiant": "AAP-X", "titre": "<img src=x onerror=alert(1)>Résidence", "organisme": "Région",
                 "criteres": "<p>Être artiste professionnel ; <script>vol()</script>projet en Occitanie.</p>",
                 "lien": "javascript:alert(1)", "discipline": "Arts visuels", "region": "Occitanie",
                 "date_limite": "%s"}""".formatted(aujourdhui.plusDays(5)),
                enregistrement("AAP-Y", "Résidence officielle", "Arts visuels", "Occitanie", aujourdhui.plusDays(6)))));
        verifier(declarer("DATA_CULTURE", StubServer.base() + JEU, "appels-residences", List.of(), List.of()));
        ArtisteFixtures.collecter();
        activer("hugo", "developpeur", "artiste");
        suivre("hugo", List.of("arts-visuels"), List.of("occitanie"));
        String residence = baseDeTest.string("SELECT id FROM opportunite WHERE titre = 'Résidence'");
        String residenceOfficielle = baseDeTest.string("SELECT id FROM opportunite WHERE titre = 'Résidence officielle'");

        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/opportunites/" + residence + "/resume").then().statusCode(200)
                .body("criteres", equalTo("Être artiste professionnel ; projet en Occitanie."))
                .body("texteOfficiel", nullValue())
                .body("mention", containsString("ne se prononce pas sur votre éligibilité"))
                .body("keySet()", not(hasItem("eligible")));
        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/opportunites/" + residenceOfficielle + "/resume").then().statusCode(200)
                .body("criteres", equalTo("Artistes professionnels résidant en France."))
                .body("texteOfficiel", equalTo("https://www.culture.gouv.fr/AAP-Y"))
                .body("keySet()", hasItems("titre", "organisme", "criteres", "dateLimite", "texteOfficiel", "mention"));
        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/opportunites").then().statusCode(200)
                .body("items.resume.mention", hasItem(containsString("éligibilité")));
        Api.enTantQue("hugo").when().get("/api/v1/profils/artiste/opportunites/" + UUID.randomUUID() + "/resume")
                .then().statusCode(404);
    }
}
