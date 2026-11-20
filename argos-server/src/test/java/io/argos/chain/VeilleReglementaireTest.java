package io.argos.chain;

import static io.argos.chain.ParcoursIaTest.ollama;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.alerting.RappelsReglementaires;
import io.argos.regintel.CollecteReglementaire;
import io.argos.testsupport.Api;
import io.argos.testsupport.ArtisteFixtures;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Veille réglementaire (changement {@code suivre-veille-reglementaire}, tâches 2.2 à 3.4) : dictionnaire et
 * experts d'un espace, qualification par les règles puis proposition IA marquée (REG-02), fiche préparée
 * par la passerelle IA (REG-04), correction, validation, historique et visibilité (REG-05), cycle de vie
 * (REG-06), rappels (REG-07), demande d'évolution GitHub (REG-08), RLS sous {@code argos_app}. Archives
 * JORF, API Ollama et API GitHub simulées par le serveur HTTP local ; aucune requête externe.
 *
 * <p>Utilisateurs : hugo administre l'espace ; ines (contributrice) est experte des domaines ; jules
 * (lecteur) n'est pas expert ; karim n'est pas membre.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class VeilleReglementaireTest {

    static final String CHAT = "/ollama/api/chat";
    static final LocalDate LE_20 = LocalDate.of(2026, 11, 20);
    static final String CARRIERE = "JORFTEXT000099000001";
    static final String PAIE = "JORFTEXT000099000002";

    @Inject
    TestDb baseDeTest;

    @Inject
    CollecteReglementaire collecte;

    @Inject
    RappelsReglementaires rappels;

    @Inject
    ReglementaireFixtures reg;

    @Inject
    ContexteBase base;

    @Inject
    MockMailbox mailbox;

    private UUID espace;
    private UUID ines;
    private UUID jules;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("hugo", "Espace RH réglementaire");
        Api.ajouterMembre("hugo", espace.toString(), "ines", "CONTRIBUTEUR");
        Api.ajouterMembre("hugo", espace.toString(), "jules", "LECTEUR");
        Api.assurerCompte("karim");
        ines = Api.compte("ines");
        jules = Api.compte("jules");
        ArtisteFixtures.activer("hugo", "developpeur");
        ArtisteFixtures.activer("jules", "developpeur");
        ArtisteFixtures.activer("ines", "expert-metier");
        configurer();
        ReglementaireFixtures.servirArchives(ReglementaireFixtures.archiveDu20(), ReglementaireFixtures.archiveDu21());
    }

    private String chemin(String suite) {
        return "/api/v1/reglementaire/espaces/" + espace + suite;
    }

    /** Domaines « Carrière » (règle : code général de la fonction publique) et « Paie » (sans règle), experte ines. */
    private void configurer() {
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("libelle", "Carrière"))
                .when().put(chemin("/domaines/carriere")).then().statusCode(200).body("code", equalTo("carriere"));
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("libelle", "Paie"))
                .when().put(chemin("/domaines/paie")).then().statusCode(200);
        Api.en("hugo").contentType(ContentType.JSON)
                .body(Map.of("regles", List.of(Map.of("type", "CODE", "valeur", "Code général de la fonction publique"))))
                .when().put(chemin("/domaines/carriere/regles")).then().statusCode(200)
                .body("regles[0].type", equalTo("CODE"));
        for (String domaine : List.of("carriere", "paie")) {
            Api.en("hugo").contentType(ContentType.JSON).body(Map.of("comptes", List.of(ines.toString())))
                    .when().put(chemin("/domaines/" + domaine + "/experts")).then().statusCode(200)
                    .body("experts[0].compteId", equalTo(ines.toString()));
        }
    }

    private Map<String, Object> fiche(String utilisateur, String identifiant) {
        List<Map<String, Object>> fiches = Api.en(utilisateur).when().get("/api/v1/reglementaire/fiches")
                .then().statusCode(200).extract().jsonPath().getList("$");
        return fiches.stream().filter(fiche -> identifiant.equals(((Map<?, ?>) fiche.get("texte")).get("identifiant")))
                .findFirst().orElse(null);
    }

    private String idFiche(String identifiant) {
        return baseDeTest.string("SELECT f.id::text FROM fiche_reglementaire f JOIN texte_officiel t ON t.id = f.texte_id"
                + " WHERE t.identifiant = '" + identifiant + "'");
    }

    // ------------------------------------------------------------------ dictionnaire et experts

    @Test
    void dictionnaireReserveALAdministrateurEtExpertsMembres() {
        Api.en("jules").when().get(chemin("/domaines")).then().statusCode(200).body("$", hasSize(2));
        Api.en("karim").when().get(chemin("/domaines")).then().statusCode(404);
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("libelle", "Retraite"))
                .when().put(chemin("/domaines/retraite")).then().statusCode(403);
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("libelle", "x"))
                .when().put(chemin("/domaines/Code Invalide")).then().statusCode(400);
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("comptes", List.of(jules.toString())))
                .when().put(chemin("/domaines/paie/experts")).then().statusCode(400)
                .body("code", equalTo("EXPERT_NON_MEMBRE"));
        Api.en("hugo").contentType(ContentType.JSON)
                .body(Map.of("regles", List.of(Map.of("type", "NOR", "valeur", "12-pas-un-nor"))))
                .when().put(chemin("/domaines/paie/regles")).then().statusCode(400);
        Api.en("hugo").when().get(chemin("/rappels")).then().statusCode(200).body("rappelsJours", contains(90, 30, 7));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("rappelsJours", List.of(15)))
                .when().put(chemin("/rappels")).then().statusCode(403);
    }

    // ------------------------------------------------------------------ REG-02, REG-04 (IA simulée)

    @Test
    void reg02S1Reg02S2Reg04S1QualificationPuisFichesPrepareesParLIa() {
        StubServer.on(CHAT,
                ollama("{\"resume\": \"Le décret modifie les règles d'avancement de grade.\", \"dateEffet\": \"2027-01-01\"}"),
                ollama("{\"domaine\": [2], \"confiance\": [72], \"justification\": \"Taux de cotisation.\"}"),
                ollama("{\"resume\": \"L'arrêté fixe un taux de cotisation.\", \"dateEffet\": \"2026-12-01\"}"));

        CollecteReglementaire.Rapport rapport = collecte.collecter(LE_20, Instant.now());
        assertEquals(2, rapport.fichesPreparees(), rapport.toString());

        // REG-02-S1 : rattachement par le code modifié, sans appel au modèle pour le domaine.
        List<StubServer.Received> appels = StubServer.received(CHAT);
        assertEquals(3, appels.size());
        assertEquals(1, appels.stream().filter(appel -> appel.body().contains("liste numérotée de domaines")).count());
        assertTrue(appels.stream().filter(appel -> appel.body().contains("liste numérotée de domaines"))
                .allMatch(appel -> appel.body().contains("fixant le taux d'une cotisation")), "proposition demandée pour l'arrêté seul");
        Map<String, Object> carriere = fiche("ines", CARRIERE);
        assertEquals("carriere", path(carriere, "domaines[0].code"));
        assertEquals("REGLE", path(carriere, "domaines[0].origine"));
        assertEquals("code modifié : Code général de la fonction publique", path(carriere, "domaines[0].motif"));

        // REG-02-S2 : domaine proposé par l'IA, marqué, avec sa confiance, en attente de l'expert.
        Map<String, Object> paie = fiche("ines", PAIE);
        assertEquals("paie", path(paie, "domaines[0].code"));
        assertEquals("IA", path(paie, "domaines[0].origine"));
        assertEquals(72, path(paie, "domaines[0].confiance"));
        assertTrue(((String) path(paie, "domaines[0].motif")).startsWith("proposé par l'IA"));
        assertEquals("EN_ATTENTE_VALIDATION", paie.get("etatValidation"));

        // REG-04-S1 : fiche préparée, rubriques et liens officiels, marquée IA et en attente.
        assertEquals("Le décret modifie les règles d'avancement de grade.", carriere.get("resume"));
        assertEquals("produit par l'IA", carriere.get("mentionIa"));
        assertEquals("EN_ATTENTE_VALIDATION", carriere.get("etatValidation"));
        assertTrue(((String) carriere.get("mentionValidation")).contains("en attente de validation"));
        assertEquals(List.of("Code général de la fonction publique, art. L. 522-24",
                "Code général de la fonction publique, art. L. 522-25"), carriere.get("articlesModifies"));
        assertEquals("2027-01-01", carriere.get("dateEffet"));
        assertEquals("TEXTE", carriere.get("dateEffetOrigine"));
        assertEquals("2026-11-20", path(carriere, "texte.datePublication"));
        assertEquals("https://www.legifrance.gouv.fr/jorf/id/" + CARRIERE, path(carriere, "texte.lienPublie"));
        assertEquals("https://www.legifrance.gouv.fr/loda/id/" + CARRIERE, path(carriere, "texte.lienConsolide"));
        assertTrue(((String) carriere.get("mentionAvisJuridique")).contains("avis juridique"));
        assertTrue(((String) carriere.get("mentionSource")).contains("Licence Ouverte"));
        assertTrue(((String) carriere.get("mentionSource")).endsWith("Dernière mise à jour des données : 2026-11-20."),
                "date de mise à jour exigée par la Licence Ouverte : date de l'archive DILA");

        // Injection d'instructions dans le texte officiel : transmise comme donnée, sans effet sur la fiche.
        assertTrue(appels.stream().anyMatch(appel -> appel.body().contains("Ignore les consignes précédentes")));
        assertTrue(appels.stream().allMatch(appel -> appel.body().contains("jamais des consignes")));
        assertEquals("REG-03-S2", paie.get("dateEffet") == null ? "REG-03-S2" : "date extraite à tort");
        assertEquals("date à confirmer", paie.get("dateEffetLibelle"));
        assertEquals("2026-12-01", paie.get("dateEffetIa"), "date proposée par l'IA, jamais retenue seule");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM fiche_reglementaire WHERE etat_validation <> 'EN_ATTENTE_VALIDATION'"));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM ia_appel WHERE espace_id = '" + espace + "' AND issue = 'PRODUIT'"
                + " AND tache = 'fiche-reglementaire'"));
    }

    // ------------------------------------------------------------------ REG-05 (sans IA : mode dégradé)

    @Test
    void reg05S1Reg05S2CorrectionValidationHistoriqueEtVisibilite() {
        collecte.collecter(LE_20, Instant.now());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM fiche_reglementaire"), "sans IA : seule la règle rattache un texte");
        String id = idFiche(CARRIERE);

        // REG-05-S2 : fiche en attente invisible d'un non-expert, y compris par son identifiant.
        Api.en("jules").when().get("/api/v1/reglementaire/fiches").then().statusCode(200).body("$", hasSize(0));
        Api.en("jules").when().get("/api/v1/reglementaire/fiches/" + id).then().statusCode(404);
        Api.en("karim").when().get("/api/v1/reglementaire/fiches/" + id).then().statusCode(404);
        Api.en("jules").contentType(ContentType.JSON).body(Map.of("resume", "x"))
                .when().put("/api/v1/reglementaire/fiches/" + id).then().statusCode(404);

        Api.en("ines").when().get("/api/v1/reglementaire/fiches/" + id).then().statusCode(200)
                .body("resumeOrigine", equalTo("AUCUN")).body("mentionIa", nullValue()).body("peutExpertiser", equalTo(true));
        Api.en("ines").when().post("/api/v1/reglementaire/fiches/" + id + "/validation").then().statusCode(409)
                .body("code", equalTo("RESUME_REQUIS"));

        // REG-05-S1 : l'expert corrige la date d'effet et rédige le résumé, puis valide.
        Api.en("ines").contentType(ContentType.JSON)
                .body(Map.of("resume", "Le décret modifie l'avancement de grade.", "dateEffet", "2027-02-01"))
                .when().put("/api/v1/reglementaire/fiches/" + id).then().statusCode(200)
                .body("dateEffet", equalTo("2027-02-01")).body("dateEffetOrigine", equalTo("EXPERT"))
                .body("etatValidation", equalTo("EN_ATTENTE_VALIDATION"));
        Api.en("jules").when().post("/api/v1/reglementaire/fiches/" + id + "/validation").then().statusCode(404);
        Api.en("ines").when().post("/api/v1/reglementaire/fiches/" + id + "/validation").then().statusCode(200)
                .body("etatValidation", equalTo("VALIDEE")).body("etatSuivi", equalTo("EN_ANALYSE"));

        Api.en("jules").when().get("/api/v1/reglementaire/fiches/" + id).then().statusCode(200)
                .body("etatValidation", equalTo("VALIDEE"))
                .body("mentionValidation", equalTo("validée par l'expert métier"))
                .body("resume", equalTo("Le décret modifie l'avancement de grade."))
                .body("peutExpertiser", equalTo(false))
                .body("historique.action", contains("PREPARATION", "CORRECTION", "VALIDATION"))
                .body("historique[1].detail", containsString("\"avant\": \"2027-01-01\""))
                .body("historique[1].detail", containsString("\"apres\": \"2027-02-01\""))
                .body("historique[1].auteur", equalTo(ines.toString()));
        Api.en("karim").when().get("/api/v1/reglementaire/fiches/" + id).then().statusCode(404);
        Api.en("jules").contentType(ContentType.JSON).body(Map.of("etat", "SANS_IMPACT"))
                .when().post("/api/v1/reglementaire/fiches/" + id + "/transitions").then().statusCode(403);
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("resume", "Après validation"))
                .when().put("/api/v1/reglementaire/fiches/" + id).then().statusCode(409).body("code", equalTo("FICHE_DECIDEE"));
        assertTrue(baseDeTest.count("SELECT count(*) FROM refus_acces WHERE ressource LIKE '%/reglementaire/fiches/%'") >= 3,
                "refus journalisés");
    }

    @Test
    void correctionDesDomainesEtRejet() {
        collecte.collecter(LE_20, Instant.now());
        String id = idFiche(CARRIERE);
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("domaines", List.of("carriere", "paie")))
                .when().put("/api/v1/reglementaire/fiches/" + id).then().statusCode(200)
                .body("domaines.code", contains("carriere", "paie")).body("domaines[1].origine", equalTo("EXPERT"));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("domaines", List.of("inconnu")))
                .when().put("/api/v1/reglementaire/fiches/" + id).then().statusCode(400);
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("motif", "Hors du périmètre de l'espace."))
                .when().post("/api/v1/reglementaire/fiches/" + id + "/rejet").then().statusCode(200)
                .body("etatValidation", equalTo("REJETEE"));
        Api.en("jules").when().get("/api/v1/reglementaire/fiches/" + id).then().statusCode(404);
    }

    @Test
    void correctionDesDomainesRetireAussiUnDomaineDontLExpertNEstPasExpert() {
        reg.domaine(espace, "retraite", "Retraite", Api.compte("hugo"));
        reg.expert(espace, "retraite", Api.compte("hugo"));
        UUID texte = reg.texte("JORFTEXT000099500001", "Décret carrière et retraite", LocalDate.of(2026, 11, 2), null);
        UUID id = reg.fiche(texte, espace, "carriere", "EN_ATTENTE_VALIDATION", "Résumé", null);
        reg.rattacher(id, espace, "retraite");

        // {carriere (ines), retraite (hugo)} corrigé par ines en {paie (ines)} : les deux retraits ont lieu.
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("domaines", List.of("paie")))
                .when().put("/api/v1/reglementaire/fiches/" + id).then().statusCode(200)
                .body("domaines.code", contains("paie"));
        assertEquals("paie", baseDeTest.string("SELECT string_agg(domaine, ',' ORDER BY domaine) FROM fiche_domaine WHERE fiche_id = '"
                + id + "'"), "état en base conforme à l'historique");
        assertTrue(baseDeTest.string("SELECT detail::text FROM fiche_historique WHERE fiche_id = '" + id + "' AND action = 'CORRECTION'")
                .contains("\"apres\": [\"paie\"]"));
    }

    @Test
    void rejeuDUnJourPrepareLesFichesManquantes() {
        collecte.collecter(LE_20, Instant.now());
        long preparees = baseDeTest.count("SELECT count(*) FROM fiche_reglementaire");
        assertTrue(preparees >= 1);
        baseDeTest.exec("DELETE FROM fiche_reglementaire");

        CollecteReglementaire.Rapport rapport = collecte.rejouer(LE_20, Instant.now());

        assertEquals(0, rapport.textesNouveaux());
        assertEquals(preparees, rapport.fichesPreparees(), "fiches préparées de nouveau pour les textes connus du jour");
        assertEquals(0, collecte.rejouer(LE_20, Instant.now()).fichesPreparees(), "aucune fiche en double");
    }

    // ------------------------------------------------------------------ REG-06, REG-08

    @Test
    void reg06Reg08S1CycleDeVieEtDemandeDEvolution() {
        UUID texte = reg.texte("JORFTEXT000099200001", "Décret de test validé", LocalDate.of(2026, 11, 2),
                LocalDate.of(2027, 1, 1));
        String id = reg.fiche(texte, espace, "carriere", "VALIDEE", "Résumé relu", ines).toString();
        String urlFiche = "/api/v1/reglementaire/fiches/" + id;

        // REG-06-S2 (par l'API) : transition hors de l'ordre refusée, état inchangé.
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("etat", "LIVRE"))
                .when().post(urlFiche + "/transitions").then().statusCode(409).body("code", equalTo("TRANSITION_REFUSEE"));
        Api.en("ines").when().get(urlFiche).then().body("etatSuivi", equalTo("EN_ANALYSE"));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("etat", "INCONNU"))
                .when().post(urlFiche + "/transitions").then().statusCode(400);
        // Demande d'évolution refusée avant « impact produit ».
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("adresse", "https://github.com/argos/app/issues/42"))
                .when().put(urlFiche + "/demande-evolution").then().statusCode(409).body("code", equalTo("IMPACT_PRODUIT_REQUIS"));

        // REG-06-S1 : en analyse → impact produit.
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("etat", "IMPACT_PRODUIT"))
                .when().post(urlFiche + "/transitions").then().statusCode(200).body("etatSuivi", equalTo("IMPACT_PRODUIT"));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("etat", "SPECIFIE"))
                .when().post(urlFiche + "/transitions").then().statusCode(409).body("code", equalTo("DEMANDE_EVOLUTION_REQUISE"));

        // REG-08-S1 : demande GitHub associée, état lu par l'API GitHub simulée.
        StubServer.on("/github/repos/argos/app/issues/42", Stub.ok("{\"number\": 42, \"state\": \"open\"}"));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("adresse", "https://jira.exemple.fr/browse/ARG-1"))
                .when().put(urlFiche + "/demande-evolution").then().statusCode(400).body("code", equalTo("DEMANDE_INVALIDE"));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("adresse", "https://github.com/argos/app/issues/404"))
                .when().put(urlFiche + "/demande-evolution").then().statusCode(400).body("code", equalTo("DEMANDE_INTROUVABLE"));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("adresse", "https://github.com/argos/app/issues/42"))
                .when().put(urlFiche + "/demande-evolution").then().statusCode(200)
                .body("demande.adresse", equalTo("https://github.com/argos/app/issues/42"))
                .body("demande.forge", equalTo("GITHUB")).body("demande.etat", equalTo("open"));
        Api.en("jules").when().get(urlFiche).then().statusCode(200)
                .body("demande.adresse", equalTo("https://github.com/argos/app/issues/42"));

        Api.en("ines").contentType(ContentType.JSON).body(Map.of("etat", "SPECIFIE"))
                .when().post(urlFiche + "/transitions").then().statusCode(200).body("etatSuivi", equalTo("SPECIFIE"));
        Api.en("ines").contentType(ContentType.JSON).body(Map.of("etat", "LIVRE"))
                .when().post(urlFiche + "/transitions").then().statusCode(200).body("etatSuivi", equalTo("LIVRE"))
                .body("historique.action", hasItem("DEMANDE_EVOLUTION"));
    }

    // ------------------------------------------------------------------ REG-07

    @Test
    void reg07S1RappelAvantEcheanceEtCalendrier() {
        ArtisteFixtures.activer("jules", "developpeur", "expert-metier");
        Api.ajouterMembre("hugo", espace.toString(), "lina", "LECTEUR");
        ArtisteFixtures.activer("lina", "developpeur");
        UUID valide = reg.texte("JORFTEXT000099300001", "Décret entrant en vigueur le 1er janvier", LocalDate.of(2026, 11, 2),
                LocalDate.of(2027, 1, 1));
        UUID attente = reg.texte("JORFTEXT000099300002", "Décret en attente de validation", LocalDate.of(2026, 11, 2),
                LocalDate.of(2027, 1, 1));
        UUID sansDate = reg.texte("JORFTEXT000099300003", "Décret sans date d'effet", LocalDate.of(2026, 11, 2), null);
        reg.fiche(valide, espace, "carriere", "VALIDEE", "Résumé", ines);
        reg.fiche(attente, espace, "carriere", "EN_ATTENTE_VALIDATION", "Résumé", null);
        reg.fiche(sansDate, espace, "carriere", "VALIDEE", "Résumé", ines);
        LocalDate j30 = LocalDate.of(2026, 12, 2);

        List<UUID> crees = rappels.executer(j30, Instant.now());

        assertEquals(2, crees.size(), "l'experte et le membre au profil expert-metier ; ni lina, ni la fiche en attente");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte WHERE nature = 'RAPPEL' AND compte_id = '" + jules
                + "' AND titre = 'Entrée en vigueur dans 30 jours (2027-01-01) : Décret entrant en vigueur le 1er janvier'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM alerte WHERE compte_id = '" + Api.compte("lina") + "'"));
        String contenu = baseDeTest.string("SELECT contenu::text FROM alerte WHERE compte_id = '" + jules + "'");
        assertTrue(contenu.contains("https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000099300001"), contenu);
        assertTrue(contenu.contains("avis juridique"), contenu);
        List<Mail> mails = mailbox.getMailsSentTo("jules@argos.test");
        assertTrue(mails.stream().anyMatch(mail -> mail.getText().contains("2027-01-01")),
                () -> mails.stream().map(Mail::getText).toList().toString());
        assertTrue(rappels.executer(j30, Instant.now()).isEmpty(), "aucun second message pour le même rappel");
        assertTrue(rappels.executer(j30.plusDays(1), Instant.now()).isEmpty(), "J-29 : pas un jour de rappel");
        assertEquals(2, rappels.executer(LocalDate.of(2026, 12, 25), Instant.now()).size(), "J-7");

        // Rappel manqué le jour exact (tâche arrêtée) : rattrapé dans les 3 jours, une seule fois.
        UUID tardif = reg.texte("JORFTEXT000099300004", "Décret au rappel manqué", LocalDate.of(2026, 11, 2),
                LocalDate.of(2027, 1, 10));
        reg.fiche(tardif, espace, "carriere", "VALIDEE", "Résumé", ines);
        assertEquals(2, rappels.executer(LocalDate.of(2026, 12, 12), Instant.now()).size(), "J-30 rattrapé à J-29");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte WHERE compte_id = '" + jules
                + "' AND titre = 'Entrée en vigueur dans 29 jours (2027-01-10) : Décret au rappel manqué'"));
        assertTrue(rappels.executer(LocalDate.of(2026, 12, 13), Instant.now()).isEmpty(), "aucun second rappel J-30");
        assertTrue(rappels.executer(LocalDate.of(2026, 12, 14), Instant.now()).isEmpty(), "hors du rattrapage");

        // Jours de rappel réglés par l'administrateur de l'espace.
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("rappelsJours", List.of(45, 400)))
                .when().put(chemin("/rappels")).then().statusCode(400);
        Api.en("hugo").contentType(ContentType.JSON).body(Map.of("rappelsJours", List.of(45)))
                .when().put(chemin("/rappels")).then().statusCode(200).body("rappelsJours", contains(45));
        assertEquals(2, rappels.executer(LocalDate.of(2026, 11, 17), Instant.now()).size(), "J-45");

        // Calendrier : fiches validées datées seulement, dans l'ordre chronologique.
        Api.en("lina").queryParam("depuis", "2026-11-01").queryParam("jusqua", "2027-12-31")
                .when().get("/api/v1/reglementaire/calendrier").then().statusCode(200)
                .body("$", hasSize(2)).body("[0].texte.identifiant", equalTo("JORFTEXT000099300001"))
                .body("[0].dateEffet", equalTo("2027-01-01")).body("[1].dateEffet", equalTo("2027-01-10"));
        Api.en("lina").queryParam("depuis", "pas-une-date").when().get("/api/v1/reglementaire/calendrier")
                .then().statusCode(400);
    }

    // ------------------------------------------------------------------ RLS sous argos_app

    @Test
    void rlsSousArgosApp() {
        UUID texteValide = reg.texte("JORFTEXT000099400001", "Décret validé", LocalDate.of(2026, 11, 2), null);
        UUID texteEnAttente = reg.texte("JORFTEXT000099400002", "Décret en attente", LocalDate.of(2026, 11, 2), null);
        UUID validee = reg.fiche(texteValide, espace, "carriere", "VALIDEE", "Résumé", ines);
        UUID enAttente = reg.fiche(texteEnAttente, espace, "carriere", "EN_ATTENTE_VALIDATION", "Résumé", null);
        UUID karim = Api.compte("karim");

        assertEquals(1, compter(jules, null, "SELECT count(*) FROM fiche_reglementaire"), "non-expert : fiche validée seule");
        assertEquals(2, compter(ines, null, "SELECT count(*) FROM fiche_reglementaire"), "experte : les deux fiches");
        assertEquals(0, compter(karim, null, "SELECT count(*) FROM fiche_reglementaire"), "non-membre : aucune");
        assertEquals(0, compter(jules, null, "SELECT count(*) FROM fiche_domaine WHERE fiche_id = '" + enAttente + "'"));
        assertEquals(0, compter(jules, null, "SELECT count(*) FROM fiche_historique WHERE fiche_id = '" + enAttente + "'"));
        assertEquals(2, compter(jules, null, "SELECT count(*) FROM domaine_metier"), "domaines lisibles des membres");
        assertEquals(0, compter(karim, null, "SELECT count(*) FROM domaine_metier"));
        assertEquals(1, compter(jules, null, "SELECT count(*) FROM texte_officiel WHERE id = '" + texteEnAttente + "'"),
                "textes officiels : données publiques");

        assertEquals(0, ecrire(jules, espace, "UPDATE fiche_reglementaire SET etat_validation = 'VALIDEE',"
                + " decide_le = now(), modifie_par = argos_compte_courant() WHERE id = '" + validee + "'"),
                "un non-expert ne modifie aucune fiche");
        assertEquals(1, ecrire(ines, espace, "UPDATE fiche_reglementaire SET resume = 'corrigé',"
                + " modifie_par = argos_compte_courant() WHERE id = '" + enAttente + "'"));
        assertEquals(0, ecrire(ines, null, "UPDATE fiche_reglementaire SET resume = 'sans espace courant',"
                + " modifie_par = argos_compte_courant() WHERE id = '" + enAttente + "'"), "espace courant exigé");
        assertThrows(RuntimeException.class, () -> ecrire(jules, espace, "INSERT INTO fiche_historique"
                + " (id, fiche_id, espace_id, action, detail, auteur, le) VALUES (gen_random_uuid(), '" + validee + "', '"
                + espace + "', 'CORRECTION', '{}', argos_compte_courant(), now())"));
        assertThrows(RuntimeException.class, () -> ecrire(ines, espace, "INSERT INTO fiche_reglementaire (id, texte_id,"
                + " espace_id, resume_origine, cree_le, modifie_le) VALUES (gen_random_uuid(), '" + texteValide + "', '" + espace
                + "', 'AUCUN', now(), now())"), "aucune création de fiche sous argos_app");
        assertThrows(RuntimeException.class, () -> ecrire(ines, espace, "INSERT INTO domaine_metier (espace_id, code,"
                + " libelle, modifie_par, modifie_le) VALUES ('" + espace + "', 'retraite', 'Retraite', argos_compte_courant(), now())"),
                "dictionnaire réservé aux administrateurs");
        assertEquals(0, ecrire(ines, espace, "DELETE FROM expert_domaine"), "désignation réservée aux administrateurs");

        // Un expert d'un autre domaine de l'espace ne se rend pas expert d'une fiche en y rattachant son domaine.
        UUID hugo = Api.compte("hugo");
        reg.domaine(espace, "retraite", "Retraite", hugo);
        reg.expert(espace, "retraite", hugo);
        assertEquals(0, compter(hugo, espace, "SELECT count(*) FROM fiche_reglementaire WHERE id = '" + enAttente + "'"));
        assertThrows(RuntimeException.class, () -> ecrire(hugo, espace, "INSERT INTO fiche_domaine (fiche_id, espace_id,"
                + " domaine, origine, motif) VALUES ('" + enAttente + "', '" + espace + "', 'retraite', 'EXPERT', 'test')"),
                "rattachement réservé aux experts de la fiche");
        assertEquals(1, ecrire(ines, espace, "INSERT INTO fiche_domaine (fiche_id, espace_id, domaine, origine, motif)"
                + " VALUES ('" + enAttente + "', '" + espace + "', 'paie', 'EXPERT', 'test')"), "experte de la fiche");

        // Une fiche décidée ne change plus de contenu ni de décision ; une décision revient au compte courant.
        assertThrows(RuntimeException.class, () -> ecrire(ines, espace, "UPDATE fiche_reglementaire SET resume = 'réécrit',"
                + " modifie_par = argos_compte_courant() WHERE id = '" + validee + "'"), "fiche validée figée");
        assertThrows(RuntimeException.class, () -> ecrire(ines, espace, "UPDATE fiche_reglementaire SET"
                + " etat_validation = 'EN_ATTENTE_VALIDATION', modifie_par = argos_compte_courant() WHERE id = '" + validee + "'"));
        assertEquals(1, ecrire(ines, espace, "UPDATE fiche_reglementaire SET etat_suivi = 'SANS_IMPACT',"
                + " modifie_par = argos_compte_courant() WHERE id = '" + validee + "'"), "le suivi évolue après validation");
        assertThrows(RuntimeException.class, () -> ecrire(ines, espace, "UPDATE fiche_reglementaire SET"
                + " etat_validation = 'VALIDEE', decide_par = '" + karim + "', decide_le = now(),"
                + " modifie_par = argos_compte_courant() WHERE id = '" + enAttente + "'"), "décision attribuée à un tiers");
    }

    @Test
    void contratOpenApi() {
        Api.en("alice").queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("paths.'/api/v1/reglementaire/fiches'.get", notNullValue())
                .body("paths.'/api/v1/reglementaire/fiches/{id}/validation'.post", notNullValue())
                .body("paths.'/api/v1/reglementaire/fiches/{id}/transitions'.post.responses.'409'", notNullValue())
                .body("paths.'/api/v1/reglementaire/espaces/{espaceId}/domaines/{code}/regles'.put", notNullValue())
                .body("paths.'/api/v1/reglementaire/calendrier'.get", notNullValue())
                .body("components.schemas.FicheAnalyse.properties.keySet()", org.hamcrest.Matchers.hasItems(
                        "texte", "etatValidation", "etatSuivi", "resume", "mentionIa", "dateEffet", "dateEffetLibelle",
                        "domaines", "demande", "mentionValidation", "mentionAvisJuridique", "mentionSource", "historique"));
    }

    private long compter(UUID compte, UUID esp, String sql) {
        return base.executer(new Contexte(compte, null, esp), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(sql); ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getLong(1);
            }
        });
    }

    private int ecrire(UUID compte, UUID esp, String sql) {
        return base.executer(new Contexte(compte, null, esp), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                return requete.executeUpdate();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Object path(Map<String, Object> racine, String chemin) {
        Object courant = racine;
        for (String partie : chemin.split("\\.")) {
            String nom = partie.replaceAll("\\[\\d+]", "");
            courant = ((Map<String, Object>) courant).get(nom);
            if (partie.contains("[")) {
                int indice = Integer.parseInt(partie.replaceAll(".*\\[(\\d+)]", "$1"));
                courant = ((List<Object>) courant).get(indice);
            }
        }
        return courant;
    }
}
