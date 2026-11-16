package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.alerting.RappelsRadar;
import io.argos.catalog.standard.ReferentielEspace;
import io.argos.impact.RadarProjets;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.RadarFixtures.Cycle;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * Référentiel des standards et radar des sorties (changement {@code construire-radar-et-standards},
 * tâches 1.1 à 2.3) : API réelle avec jetons Keycloak, RLS sous {@code argos_app}, calendrier
 * endoflife.date simulé par le serveur HTTP local (réponses enregistrées), horloge simulée pour les
 * rappels, boîte aux lettres simulée de Quarkus Mailer.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class RadarStandardsTest {

    private static final String SPRING = "pkg:maven/org.springframework.boot/spring-boot";
    private static final String BIBLIO = "pkg:npm/biblio-ui";
    private static final String POSTGRES = "pkg:generic/postgresql";

    @Inject
    TestDb db;

    @Inject
    ContexteBase base;

    @Inject
    ReferentielEspace referentiel;

    @Inject
    RadarProjets radar;

    @Inject
    RappelsRadar rappels;

    @Inject
    ProjectDirectory projets;

    @Inject
    InventoryService inventaires;

    @Inject
    MockMailbox mailbox;

    private UUID espace;
    private UUID compteAdmin;
    private UUID compteLecteur;
    private UUID compteExterieur;

    @BeforeEach
    void preparer() {
        db.reset();
        espace = Api.nouvelEspace("nina", "Radar de Nina");
        Api.ajouterMembre("nina", espace.toString(), "paul", "LECTEUR");
        compteAdmin = Api.compte("nina");
        compteLecteur = Api.compte("paul");
        compteExterieur = Api.compte("marc");
    }

    // ------------------------------------------------------------------ RAD-01

    /** RAD-01-S1 : Spring Boot en ADOPT, version 3.5, responsable, justification, revue : entrée créée et historisée. */
    @Test
    void rad01S1StandardComplet() {
        enregistrer("nina", saisie(SPRING, "Spring Boot", "ADOPT", "3.5", "Socle Java", "Version validée", "2027-06-30", "spring-boot"))
                .statusCode(200)
                .body("cle", equalTo(SPRING))
                .body("statut", equalTo("ADOPT"))
                .body("versionRecommandee", equalTo("3.5"))
                .body("responsable", equalTo("Socle Java"))
                .body("dateRevue", equalTo("2027-06-30"))
                .body("produitCycleVie", equalTo("spring-boot"));
        Api.en("paul").get(chemin("/standards")).then().statusCode(200)
                .body("$", hasSize(1)).body("[0].libelle", equalTo("Spring Boot"));
        Api.en("paul").queryParam("cle", SPRING).get(chemin("/standards/historique")).then().statusCode(200)
                .body("$", hasSize(1)).body("[0].origine", equalTo("SAISIE"));
    }

    /** RAD-01-S2 : HOLD sans justification : refus qui nomme le champ, référentiel inchangé. */
    @Test
    void rad01S2JustificationManquante() {
        enregistrer("nina", saisie(BIBLIO, "Biblio", "ASSESS", null, "Front", "À étudier", "2027-01-31", null)).statusCode(200);

        enregistrer("nina", saisie(BIBLIO, "Biblio", "HOLD", null, "Front", null, "2027-01-31", null))
                .statusCode(400).body("code", equalTo("CHAMP_MANQUANT")).body("attribut", equalTo("justification"));
        enregistrer("nina", saisie(SPRING, "Spring Boot", "ADOPT", null, "Socle", "Validé", "2027-01-31", null))
                .statusCode(400).body("attribut", equalTo("versionRecommandee"));
        enregistrer("nina", saisie("pkg:npm/biblio-ui@1.0.0", "Biblio", "HOLD", null, "Front", "Non", "2027-01-31", null))
                .statusCode(400).body("attribut", equalTo("cle"));
        enregistrer("nina", saisie(BIBLIO, "Biblio", "HOLD", null, "Front", "Non", "31/01/2027", null))
                .statusCode(400).body("attribut", equalTo("dateRevue"));
        enregistrer("nina", saisie(BIBLIO, "Biblio", "HOLD", null, "Front", "Non", "2027-01-31", "../../etc"))
                .statusCode(400).body("attribut", equalTo("produitCycleVie"));

        Api.en("nina").get(chemin("/standards")).then().statusCode(200)
                .body("$", hasSize(1)).body("[0].statut", equalTo("ASSESS"));
        assertEquals(1, db.count("SELECT count(*) FROM standard_historique"));
    }

    // ------------------------------------------------------------------ RAD-02

    /** RAD-02-S1 : un lecteur ne modifie pas le référentiel (403 journalisé) ; un non-membre reçoit 404. */
    @Test
    void rad02S1Lecteur() {
        enregistrer("nina", saisie(SPRING, "Spring Boot", "ADOPT", "3.5", "Socle", "Validé", "2027-06-30", null)).statusCode(200);
        long refus = db.count("SELECT count(*) FROM refus_acces");

        enregistrer("paul", saisie(SPRING, "Spring Boot", "HOLD", null, "Socle", "Abandon", "2027-06-30", null))
                .statusCode(403).body("code", equalTo("INTERDIT"));
        Api.en("paul").contentType(ContentType.JSON).body(Map.of("rappelsJours", List.of(3)))
                .put(chemin("/parametres")).then().statusCode(403);
        Api.en("marc").get(chemin("/standards")).then().statusCode(404);
        assertEquals(refus + 3, db.count("SELECT count(*) FROM refus_acces"));
        Api.en("nina").get(chemin("/standards")).then().body("[0].statut", equalTo("ADOPT"));
    }

    /** Garde-fou en base : sous argos_app, un lecteur n'écrit rien et un non-membre ne lit rien. */
    @Test
    void rlsDuReferentiel() {
        enregistrer("nina", saisie(SPRING, "Spring Boot", "ADOPT", "3.5", "Socle", "Validé", "2027-06-30", null)).statusCode(200);


        assertThrows(IllegalStateException.class, () -> base.executer(new Contexte(compteLecteur, null, espace), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE standard_technologie SET statut = 'HOLD', modifie_par = argos_compte_courant() WHERE espace_id = ?")) {
                ps.setObject(1, espace);
                if (ps.executeUpdate() == 0) {
                    throw new java.sql.SQLException("aucune ligne modifiable");
                }
            }
            return null;
        }));
        assertEquals(0L, (long) base.executer(new Contexte(compteExterieur, null, null), c -> compter(c,
                "SELECT count(*) FROM standard_technologie")));
        assertEquals(1L, (long) base.executer(new Contexte(compteLecteur, null, null), c -> compter(c,
                "SELECT count(*) FROM standard_technologie")));
        assertEquals("ADOPT", db.string("SELECT statut FROM standard_technologie"));
    }

    /** RAD-02-S2 : la suggestion de l'IA reste en attente, statut inchangé ; seul un administrateur l'applique. */
    @Test
    void rad02S2SuggestionDeLIa() {
        enregistrer("nina", saisie(POSTGRES, "PostgreSQL", "ADOPT", "14", "DBA", "Version courante", "2027-06-30", "postgresql"))
                .statusCode(200);

        UUID suggestion = referentiel.suggerer(espace, POSTGRES, "HOLD", null, "La version 14 a atteint sa fin de support.");

        Api.en("paul").get(chemin("/suggestions")).then().statusCode(200)
                .body("$", hasSize(1)).body("[0].etat", equalTo("EN_ATTENTE")).body("[0].origine", equalTo("IA"))
                .body("[0].statut", equalTo("HOLD"));
        Api.en("paul").get(chemin("/standards")).then().body("[0].statut", equalTo("ADOPT"));

        Api.en("paul").contentType(ContentType.JSON).body(Map.of("decision", "ACCEPTER"))
                .post(chemin("/suggestions/" + suggestion + "/decision")).then().statusCode(403);
        Api.en("nina").contentType(ContentType.JSON).body(Map.of("decision", "ACCEPTER"))
                .post(chemin("/suggestions/" + suggestion + "/decision")).then().statusCode(200)
                .body("[0].etat", equalTo("ACCEPTEE"));
        Api.en("paul").get(chemin("/standards")).then().body("[0].statut", equalTo("HOLD"))
                .body("[0].responsable", equalTo("DBA"));
        Api.en("paul").queryParam("cle", POSTGRES).get(chemin("/standards/historique")).then()
                .body("$", hasSize(2)).body("[0].origine", equalTo("SUGGESTION"));
        Api.en("nina").contentType(ContentType.JSON).body(Map.of("decision", "REJETER"))
                .post(chemin("/suggestions/" + suggestion + "/decision")).then().statusCode(409);
    }

    // ------------------------------------------------------------------ RAD-06

    /** RAD-06-S1 : une bibliothèque de deux projets passe en HOLD : leurs utilisateurs reçoivent l'annonce et sa justification. */
    @Test
    void rad06S1PassageEnHold() {
        UUID p1 = projet(espace, "Portail agent", BIBLIO + "@2.1.0");
        UUID p2 = projet(espace, "Back-office", BIBLIO + "@2.4.0");
        UUID espaceVoisin = Api.nouvelEspace("marc", "Voisin du radar");
        projet(espaceVoisin, "Projet voisin", BIBLIO + "@2.1.0");
        enregistrer("nina", saisie(BIBLIO, "Biblio UI", "ADOPT", "2.4", "Front", "Bibliothèque commune", "2027-06-30", null))
                .statusCode(200);
        assertEquals(0, db.count("SELECT count(*) FROM alerte WHERE nature = 'ANNONCE'"), "une création n'est pas annoncée");

        enregistrer("nina", saisie(BIBLIO, "Biblio UI", "HOLD", null, "Front", "Plus maintenue, migrer vers Kit UI",
                "2027-06-30", null)).statusCode(200);

        assertEquals(2, db.count("SELECT count(*) FROM alerte WHERE nature = 'ANNONCE'"));
        assertEquals(1, db.count("SELECT count(*) FROM alerte WHERE nature = 'ANNONCE' AND compte_id = '" + compteLecteur + "'"));
        assertEquals(0, db.count("SELECT count(*) FROM alerte WHERE nature = 'ANNONCE' AND compte_id = '"
                + compteExterieur + "'"));
        String contenu = db.string("SELECT contenu::text FROM alerte WHERE compte_id = '" + compteLecteur + "'");
        assertTrue(contenu.contains(p1.toString()) && contenu.contains(p2.toString()), contenu);
        assertFalse(contenu.contains("Projet voisin"), contenu);
        List<Mail> mails = mailbox.getMailsSentTo("paul@argos.test");
        assertEquals(1, mails.size());
        assertTrue(mails.get(0).getSubject().startsWith("[Argos] Standard modifié"), mails.get(0).getSubject());
        assertTrue(mails.get(0).getText().contains("Plus maintenue, migrer vers Kit UI"), mails.get(0).getText());
        assertTrue(mails.get(0).getText().contains("Back-office (version 2.4.0)"), mails.get(0).getText());
        Api.en("paul").queryParam("canal", "EMAIL").get("/api/v1/alertes").then().statusCode(200)
                .body("[0].nature", equalTo("ANNONCE"));
    }

    // ------------------------------------------------------------------ RAD-03 et connecteur

    /** RAD-03-S1 : une sortie dans 10 jours et une fin de support dans 166 jours : seule la sortie à 30 jours. */
    @Test
    void rad03S1Horizon30Jours() {
        LocalDate aujourdhui = LocalDate.now();
        RadarFixtures.publier("biblio-ui", RadarFixtures.reponse("biblio-ui",
                new Cycle("3", aujourdhui.plusDays(10), null, false, null, null),
                new Cycle("2", LocalDate.of(2024, 1, 15), aujourdhui.plusDays(166), false, "2.4.0", LocalDate.of(2025, 3, 1))));
        enregistrer("nina", saisie(BIBLIO, "Biblio UI", "ADOPT", "2.4", "Front", "Courante", "2027-06-30", "biblio-ui"))
                .statusCode(200);
        radar.actualiser(aujourdhui, Instant.now());

        Api.en("paul").queryParam("horizon", "30j").get(chemin("/echeances")).then().statusCode(200)
                .body("echeances", hasSize(1))
                .body("echeances[0].nature", equalTo("SORTIE"))
                .body("echeances[0].version", equalTo("3"))
                .body("echeances[0].libelle", equalTo("Biblio UI"))
                .body("echeances[0].source", equalTo("endoflife.date"))
                .body("sources[0].etat", equalTo("A_JOUR"));
        Api.en("paul").queryParam("horizon", "6m").get(chemin("/echeances")).then().statusCode(200)
                .body("echeances", hasSize(2)).body("echeances[1].nature", equalTo("FIN_DE_SUPPORT"));
        Api.en("paul").queryParam("horizon", "10ans").get(chemin("/echeances")).then().statusCode(400);
        Api.en("marc").get(chemin("/echeances")).then().statusCode(404);
    }

    /** Contrat du connecteur : chemin v1, lecture tolérante, échec conservant les cycles, produit invalide jamais demandé. */
    @Test
    void contratConnecteurEndoflife() {
        RadarFixtures.publier("postgresql", RadarFixtures.reponse("postgresql",
                new Cycle("14", LocalDate.of(2021, 9, 30), LocalDate.of(2026, 11, 12), true, "14.25", LocalDate.of(2026, 11, 9)),
                new Cycle("17", LocalDate.of(2024, 9, 26), LocalDate.of(2029, 11, 8), false, "17.12", LocalDate.of(2026, 11, 9))));
        enregistrer("nina", saisie(POSTGRES, "PostgreSQL", "ADOPT", "17", "DBA", "Courante", "2027-06-30", "postgresql"))
                .statusCode(200);

        RadarProjets.Bilan bilan = radar.actualiser(LocalDate.of(2026, 11, 16), Instant.now());
        assertEquals(Map.of("postgresql", "A_JOUR"), bilan.produits());
        assertEquals(1, StubServer.received("/endoflife/api/v1/products/postgresql").size());
        assertEquals("2026-11-12", db.string("SELECT fin_support_le FROM cycle_vie WHERE produit = 'postgresql' AND cycle = '14'"));
        assertEquals("17.12", db.string("SELECT derniere_version FROM cycle_vie WHERE cycle = '17'"));

        // Réponse illisible puis indisponibilité : produit EN_ERREUR, cycles déjà connus conservés.
        RadarFixtures.publier("postgresql", "<html>pas du JSON</html>");
        assertEquals(Map.of("postgresql", "EN_ERREUR"), radar.actualiser(LocalDate.of(2026, 11, 17), Instant.now()).produits());
        StubServer.on("/endoflife/api/v1/products/postgresql", StubServer.Stub.status(503));
        assertEquals(Map.of("postgresql", "EN_ERREUR"), radar.actualiser(LocalDate.of(2026, 11, 18), Instant.now()).produits());
        assertEquals("réponse HTTP 503", db.string("SELECT derniere_erreur FROM cycle_vie_produit"));
        assertEquals(2, db.count("SELECT count(*) FROM cycle_vie WHERE produit = 'postgresql'"));

        // Cycle mal formé ignoré, les autres gardés.
        RadarFixtures.publier("postgresql", """
                {"result":{"releases":[{"name":"18","releaseDate":"pas-une-date","eolFrom":"2030-11-14","isEol":false},
                {"name":{"objet":1}},{"releaseDate":"2020-01-01"}]}}""");
        radar.actualiser(LocalDate.of(2026, 11, 19), Instant.now());
        assertEquals(1, db.count("SELECT count(*) FROM cycle_vie WHERE produit = 'postgresql'"));
        assertEquals(null, db.string("SELECT sortie_le FROM cycle_vie WHERE cycle = '18'"));

        // Identifiant de produit refusé en base : aucune requête vers un chemin arbitraire.
        assertThrows(IllegalStateException.class, () -> RadarFixtures.standard(db, espace, BIBLIO, "X", "HOLD", null, "x",
                "../admin"));
        assertTrue(StubServer.received("/endoflife/api/v1/products/..").isEmpty());
    }

    // ------------------------------------------------------------------ RAD-04

    /** RAD-04-S1 : fin de support dans 30 jours d'une technologie utilisée : rappel aux utilisateurs du projet, une seule fois. */
    @Test
    void rad04S1RappelAJ30() {
        LocalDate jour = LocalDate.of(2027, 3, 1);
        RadarFixtures.publier("spring-boot", RadarFixtures.reponse("spring-boot",
                new Cycle("3.3", LocalDate.of(2024, 5, 23), jour.plusDays(30), false, "3.3.13", LocalDate.of(2025, 6, 19)),
                new Cycle("3.5", LocalDate.of(2025, 5, 22), LocalDate.of(2028, 6, 30), false, "3.5.4", LocalDate.of(2026, 7, 1))));
        UUID concerne = projet(espace, "Portail agent", SPRING + "@3.3.5");
        projet(espace, "Back-office", SPRING + "@3.5.1");
        enregistrer("nina", saisie(SPRING, "Spring Boot", "ADOPT", "3.5", "Socle", "Version validée", "2027-06-30", "spring-boot"))
                .statusCode(200);

        RappelsRadar.Bilan bilan = rappels.executer(jour, Instant.now());
        assertEquals(2, bilan.rappels().size(), "un rappel par utilisateur de l'espace");
        String contenu = db.string("SELECT contenu::text FROM alerte WHERE nature = 'RAPPEL' AND compte_id = '" + compteLecteur + "'");
        assertTrue(contenu.contains(concerne.toString()), contenu);
        assertFalse(contenu.contains("Back-office"), contenu);
        assertEquals("Fin de support de Spring Boot 3.3 dans 30 jours (2027-03-31)",
                db.string("SELECT titre FROM alerte WHERE compte_id = '" + compteLecteur + "'"));
        List<Mail> mails = mailbox.getMailsSentTo("paul@argos.test");
        assertEquals(1, mails.size());
        assertTrue(mails.get(0).getSubject().startsWith("[Argos] Rappel d'échéance"));

        // Rejeu du même jour : aucun doublon ; le lendemain (J-29) : aucun rappel ; J-7 puis J0 : un rappel chacun.
        assertTrue(rappels.executer(jour, Instant.now()).rappels().isEmpty());
        assertTrue(rappels.executer(jour.plusDays(1), Instant.now()).rappels().isEmpty());
        assertEquals(2, rappels.executer(jour.plusDays(23), Instant.now()).rappels().size());
        assertEquals(2, rappels.executer(jour.plusDays(30), Instant.now()).rappels().size());
        assertEquals(6, db.count("SELECT count(*) FROM alerte WHERE nature = 'RAPPEL'"));
    }

    /** Q-1 : jours de rappel paramétrables par espace, J-30, J-7 et J0 par défaut. */
    @Test
    void rappelsParametrablesParEspace() {
        Api.en("paul").get(chemin("/parametres")).then().statusCode(200).body("rappelsJours", equalTo(List.of(30, 7, 0)));
        Api.en("nina").contentType(ContentType.JSON).body(Map.of("rappelsJours", List.of(400)))
                .put(chemin("/parametres")).then().statusCode(400).body("attribut", equalTo("rappelsJours"));
        Api.en("nina").contentType(ContentType.JSON).body(Map.of("rappelsJours", List.of(2, 15, 15)))
                .put(chemin("/parametres")).then().statusCode(200).body("rappelsJours", equalTo(List.of(15, 2)));

        LocalDate jour = LocalDate.of(2027, 3, 1);
        RadarFixtures.publier("spring-boot", RadarFixtures.reponse("spring-boot",
                new Cycle("3.3", LocalDate.of(2024, 5, 23), jour.plusDays(30), false, null, null)));
        projet(espace, "Portail agent", SPRING + "@3.3.5");
        enregistrer("nina", saisie(SPRING, "Spring Boot", "ADOPT", "3.5", "Socle", "Version validée", "2027-06-30", "spring-boot"))
                .statusCode(200);
        assertTrue(rappels.executer(jour, Instant.now()).rappels().isEmpty(), "J-30 n'est plus un jour de rappel");
        assertEquals(2, rappels.executer(jour.plusDays(15), Instant.now()).rappels().size());
    }

    // ------------------------------------------------------------------ RAD-05

    /** RAD-05-S1 : Portail agent utilise PostgreSQL 14 ; fin de support atteinte : dette de version avec la version standard. */
    @Test
    void rad05S1VersionPlusMaintenue() {
        RadarFixtures.publier("postgresql", RadarFixtures.reponse("postgresql",
                new Cycle("14", LocalDate.of(2021, 9, 30), LocalDate.of(2026, 11, 12), true, "14.25", LocalDate.of(2026, 11, 9)),
                new Cycle("17", LocalDate.of(2024, 9, 26), LocalDate.of(2029, 11, 8), false, "17.12", LocalDate.of(2026, 11, 9))));
        UUID portail = projet(espace, "Portail agent", POSTGRES + "@14.10");
        projet(espace, "Back-office", POSTGRES + "@17.2");
        enregistrer("nina", saisie(POSTGRES, "PostgreSQL", "ADOPT", "17", "DBA", "Version courante", "2027-06-30", "postgresql"))
                .statusCode(200);

        assertEquals(0, radar.actualiser(LocalDate.of(2026, 11, 11), Instant.now()).dettesCreees(), "avant la fin de support");
        assertEquals(1, radar.actualiser(LocalDate.of(2026, 11, 12), Instant.now()).dettesCreees());
        assertEquals(0, radar.actualiser(LocalDate.of(2026, 11, 13), Instant.now()).dettesCreees(), "aucun doublon");

        Api.en("paul").get(chemin("/dettes")).then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].projetId", equalTo(portail.toString()))
                .body("[0].projet", equalTo("Portail agent"))
                .body("[0].version", equalTo("14.10"))
                .body("[0].cycle", equalTo("14"))
                .body("[0].finSupport", equalTo("2026-11-12"))
                .body("[0].versionStandard", equalTo("17"));
        Api.en("marc").get(chemin("/dettes")).then().statusCode(404);

        assertEquals(0L, (long) base.executer(new Contexte(compteExterieur, null, null),
                c -> compter(c, "SELECT count(*) FROM dette_version")));
    }

    // ------------------------------------------------------------------ outils

    private String chemin(String suffixe) {
        return "/api/v1/radar/espaces/" + espace + suffixe;
    }

    private ValidatableResponse enregistrer(String utilisateur, Map<String, Object> corps) {
        return Api.en(utilisateur).contentType(ContentType.JSON).body(corps).put(chemin("/standards")).then();
    }

    private static Map<String, Object> saisie(String cle, String libelle, String statut, String version, String responsable,
            String justification, String dateRevue, String produit) {
        Map<String, Object> m = new HashMap<>();
        m.put("cle", cle);
        m.put("libelle", libelle);
        m.put("statut", statut);
        m.put("versionRecommandee", version);
        m.put("responsable", responsable);
        m.put("justification", justification);
        m.put("dateRevue", dateRevue);
        m.put("produitCycleVie", produit);
        return m;
    }

    private UUID projet(UUID espaceId, String nom, String purl) {
        UUID id = projets.create(espaceId, nom, null, null);
        inventaires.importSbom(id, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", purl}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        return id;
    }

    private static long compter(java.sql.Connection c, String sql) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
