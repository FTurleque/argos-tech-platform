package io.argos.alerting;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import io.argos.impact.CorrelationEngine;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.SimulatedResumeIa;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Alertes immédiates, regroupement, résumés périodiques, résumé IA non bloquant, e-mail et contrat
 * du client desktop (changement {@code diffuser-alertes-et-digests}, tâches 2.1 à 3.3 et 5.2).
 * Toute la chaîne est réelle : collecte OSV simulée par un serveur HTTP local, corrélation,
 * évaluation des règles, boîte aux lettres simulée de Quarkus Mailer.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class AlertesTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";
    private static final String HAUTE = "[{\"type\":\"SEVERITY\",\"value\":\"HIGH\"}]";
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Inject
    TestDb baseDeTest;

    @Inject
    DataSource dataSource;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    CorrelationEngine engine;

    @Inject
    ResumesPeriodiques resumes;

    @Inject
    EnvoiAlertes envoi;

    @Inject
    MockMailbox mailbox;

    @Inject
    SimulatedResumeIa resumeIa;

    @ConfigProperty(name = "argos.alerting.resume-quotidien.cron")
    String cronQuotidien;

    @ConfigProperty(name = "argos.alerting.resume-hebdomadaire.cron")
    String cronHebdomadaire;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    private UUID spaceA;
    private int versionAvis;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        spaceA = Api.nouvelEspace("alice", "Espace A");
        versionAvis = 10;
    }

    // ------------------------------------------------------------------ outils

    private UUID projet(UUID espace, String nom, String purl) {
        UUID project = projects.create(espace, nom, null, null);
        inventories.importSbom(project, Fixtures.cyclonedx("1.6", Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", purl}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        return project;
    }

    /** Publie un avis OSV de la gravité donnée ; {@code purl} nul : avis sans PURL (impact « à vérifier »). */
    private UUID publier(String id, String purl, String gravite) {
        String modifie = "2026-11-" + (versionAvis++) + "T00:00:00Z";
        String json = Fixtures.osv(id, modifie, "Maven", "org.exemple:lib", purl, "0", "1.3.0")
                .replace("\"severity\":\"HIGH\"", "\"severity\":\"" + gravite + "\"");
        Fixtures.stubOsv(id, modifie, json);
        synchronizer.synchronize("OSV");
        return baseDeTest.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private static void regle(String utilisateur, String canal, String frequence, String filtres) {
        Api.enTantQue(utilisateur).contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":" + filtres + ",\"channel\":\"" + canal + "\",\"frequency\":\""
                        + frequence + "\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);
    }

    private long alertes(String condition) {
        return baseDeTest.count("SELECT count(*) FROM alerte WHERE " + condition);
    }

    private static Instant lundiProchain8h() {
        return ZonedDateTime.now(PARIS).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(8).withMinute(0).withSecond(0).withNano(0).toInstant();
    }

    // ------------------------------------------------------------------ 2.1 unicité

    @Test
    void cleDIdempotenceUniqueParSignalCompteEtCanal() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);
        publier("GHSA-UNIQ", LIB, "CRITICAL");
        assertEquals(1, alertes("nature = 'IMMEDIATE'"));

        SQLException doublon = assertThrows(SQLException.class, () -> {
            try (Connection connexion = dataSource.getConnection(); Statement instruction = connexion.createStatement()) {
                instruction.execute("""
                        INSERT INTO alerte(id, nature, compte_id, canal, signal_id, titre, contenu, etat_resume_ia, etat, cree_le)
                        SELECT gen_random_uuid(), 'IMMEDIATE', compte_id, canal, signal_id, 'doublon', contenu,
                               'SANS_OBJET', 'EN_ATTENTE', now() FROM alerte
                        """);
            }
        });
        assertEquals("23505", doublon.getSQLState(), "violation de la clé unique (signal, compte, canal)");
    }

    // ------------------------------------------------------------------ 2.2 règles

    @Test
    void alr01S1EtAlr06S1FailleCritiqueSurUnProjetSuiviEnvoyeeParEmail() {
        projet(spaceA, "portail-rh", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);

        UUID signal = publier("GHSA-ALR1", LIB, "CRITICAL");

        assertEquals(1, alertes("nature = 'IMMEDIATE' AND canal = 'EMAIL' AND etat = 'ENVOYEE'"));
        List<Mail> mails = mailbox.getMailsSentTo("alice@argos.test");
        assertEquals(1, mails.size());
        Mail mail = mails.get(0);
        assertTrue(mail.getSubject().contains("CRITICAL"), mail.getSubject());
        assertTrue(mail.getSubject().contains("GHSA-ALR1"), mail.getSubject());
        String corps = mail.getText();
        assertTrue(corps.contains("Vulnérabilité de test GHSA-ALR1"), "titre");
        assertTrue(corps.contains("Gravité : CRITICAL"), "gravité");
        assertTrue(corps.contains("portail-rh (EXACT)"), "projet autorisé du destinataire");
        assertTrue(corps.contains("OSV GHSA-ALR1 <https://osv.dev/vulnerability/GHSA-ALR1>"), "source");
        assertTrue(corps.contains("/signaux/" + signal), "lien vers l'élément");
    }

    @Test
    void graviteMinimaleInconnueRefuseeALaCreationDeLaRegle() {
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"SEVERITY\",\"value\":\"URGENTE\"}],"
                        + "\"channel\":\"email\",\"frequency\":\"immediate\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribut", equalTo("filters"));
    }

    @Test
    void alr01S2GraviteInsuffisanteDansLeProchainResume() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);

        UUID signal = publier("GHSA-LOW", LIB, "LOW");

        assertEquals(0, alertes("nature = 'IMMEDIATE'"));
        assertTrue(mailbox.getMailsSentTo("alice@argos.test").isEmpty());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM resume_element WHERE frequence = 'DAILY' AND canal = 'EMAIL'"
                + " AND signal_id = '" + signal + "'"));

        List<UUID> crees = resumes.composer("DAILY", Instant.now().plus(1, ChronoUnit.DAYS));
        assertEquals(1, crees.size());
        Mail resume = mailbox.getMailsSentTo("alice@argos.test").get(0);
        assertTrue(resume.getSubject().startsWith("[Argos] Résumé quotidien"), resume.getSubject());
        assertTrue(resume.getText().contains("GHSA-LOW"));
    }

    @Test
    void alr02S1CorrespondanceAVerifierSansAlerteEtVisibleDansLeFil() {
        UUID projet = projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "immediate", "[{\"type\":\"PROJECT\",\"value\":\"" + projet + "\"}]");

        UUID signal = publier("GHSA-NOPURL", null, "CRITICAL");

        assertEquals("A_VERIFIER", baseDeTest.string("SELECT level FROM project_impact WHERE signal_id = '" + signal + "'"));
        assertEquals(0, alertes("true"));
        assertTrue(mailbox.getMailsSentTo("alice@argos.test").isEmpty());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM resume_element WHERE signal_id = '" + signal + "'"));
        Api.enTantQue("alice").when().get("/api/v1/me/watch")
                .then().statusCode(200)
                .body("find { it.signalId == '" + signal + "' }.impacts[0].level", equalTo("A_VERIFIER"));
    }

    @Test
    void cloisonnementLesProjetsCitesSontCeuxDesEspacesDuDestinataire() {
        UUID spaceB = Api.nouvelEspace("jules", "Espace B");
        Api.ajouterMembre("alice", spaceA.toString(), "karim", "LECTEUR");
        projet(spaceA, "projet-a", LIB + "@1.2.0");
        projet(spaceB, "projet-b", LIB + "@1.1.0");
        // Socle TEAM obligatoire de l'espace A : ses membres en héritent (ABO-03).
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body("{\"scope\":\"TEAM\",\"teamWorkspaceId\":\"" + spaceA + "\",\"filters\":" + HAUTE
                        + ",\"channel\":\"email\",\"frequency\":\"immediate\",\"mandatory\":true}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);
        regle("jules", "email", "immediate", HAUTE);

        publier("GHSA-CLOIS", LIB, "HIGH");

        String karim = mailbox.getMailsSentTo("karim@argos.test").get(0).getText();
        assertTrue(karim.contains("projet-a"));
        assertFalse(karim.contains("projet-b"), "aucune donnée d'un espace non autorisé (ACC-04)");
        String jules = mailbox.getMailsSentTo("jules@argos.test").get(0).getText();
        assertTrue(jules.contains("projet-b"));
        assertFalse(jules.contains("projet-a"));
        assertEquals(1, mailbox.getMailsSentTo("alice@argos.test").size());
    }

    // ------------------------------------------------------------------ 2.3 regroupement

    @Test
    void alr03S1SignalTouchantTroisProjetsUneSeuleAlerte() {
        projet(spaceA, "alpha", LIB + "@1.0.0");
        projet(spaceA, "beta", LIB + "@1.1.0");
        projet(spaceA, "gamma", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);
        regle("alice", "email", "immediate", "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]");
        regle("alice", "desktop", "immediate", HAUTE);

        UUID signal = publier("GHSA-TROIS", LIB, "CRITICAL");
        // Rejeu de la corrélation : aucune seconde alerte (D2).
        engine.correlate(signal, Optional.empty());

        assertEquals(1, alertes("canal = 'EMAIL'"), "une seule alerte malgré deux règles et un rejeu");
        assertEquals(1, alertes("canal = 'DESKTOP'"), "une alerte par canal");
        assertEquals(1, mailbox.getMailsSentTo("alice@argos.test").size());
        String corps = mailbox.getMailsSentTo("alice@argos.test").get(0).getText();
        assertTrue(corps.contains("alpha (EXACT), beta (EXACT), gamma (EXACT)"), corps);
    }

    // ------------------------------------------------------------------ 2.4 IA non bloquante (QS-10)

    @Test
    void alr05S1PlafondIaAtteintAlerteEnvoyeeSansResume() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);
        resumeIa.configure(SimulatedResumeIa.Mode.BUDGET_EXHAUSTED);

        publier("GHSA-PLAFOND", LIB, "CRITICAL");

        assertEquals(1, alertes("etat = 'ENVOYEE' AND etat_resume_ia = 'PLAFOND_ATTEINT' AND resume_ia IS NULL"));
        assertTrue(resumeIa.calls().isEmpty(), "aucun appel au-delà du plafond");
        assertTrue(mailbox.getMailsSentTo("alice@argos.test").get(0).getText().contains("Résumé IA non disponible."));
    }

    @Test
    void qs10IaIndisponibleRefuseeOuTropLenteNeRetientPasLAlerte() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);

        resumeIa.configure(SimulatedResumeIa.Mode.UNAVAILABLE);
        publier("GHSA-PANNE", LIB, "CRITICAL");
        resumeIa.configure(SimulatedResumeIa.Mode.REFUSED);
        publier("GHSA-REFUS", LIB, "CRITICAL");
        resumeIa.configure(SimulatedResumeIa.Mode.SLOW);
        Instant debut = Instant.now();
        publier("GHSA-LENT", LIB, "CRITICAL");
        Duration attente = Duration.between(debut, Instant.now());
        resumeIa.configure(SimulatedResumeIa.Mode.AVAILABLE);
        publier("GHSA-OK", LIB, "CRITICAL");

        assertEquals("INDISPONIBLE", etatIa("GHSA-PANNE"));
        assertEquals("REFUSE", etatIa("GHSA-REFUS"));
        assertEquals("INDISPONIBLE", etatIa("GHSA-LENT"));
        assertTrue(attente.compareTo(Duration.ofSeconds(4)) < 0, "le résumé lent n'est pas attendu : " + attente);
        assertEquals("DISPONIBLE", etatIa("GHSA-OK"));
        assertEquals(4, alertes("etat = 'ENVOYEE'"));
        assertTrue(mailbox.getMailsSentTo("alice@argos.test").stream()
                .anyMatch(message -> message.getText().contains("Résumé (produit par l'IA, à vérifier sur les sources)")));
    }

    private String etatIa(String externe) {
        return baseDeTest.string("SELECT a.etat_resume_ia FROM alerte a JOIN signal s ON s.id = a.signal_id"
                + " WHERE s.external_id = '" + externe + "'");
    }

    // ------------------------------------------------------------------ 3.1 résumés

    @Test
    void horairesDesResumesConformesALaDecisionQ3() {
        assertEquals("Europe/Paris", fuseau);
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        // Horloge simulée : un mercredi à 09:30, heure de Paris.
        ZonedDateTime mercredi = ZonedDateTime.of(2026, 11, 18, 9, 30, 0, 0, PARIS);
        ZonedDateTime quotidien = ExecutionTime.forCron(parser.parse(cronQuotidien)).nextExecution(mercredi).orElseThrow();
        ZonedDateTime hebdo = ExecutionTime.forCron(parser.parse(cronHebdomadaire)).nextExecution(mercredi).orElseThrow();
        assertEquals(ZonedDateTime.of(2026, 11, 19, 8, 0, 0, 0, PARIS), quotidien);
        assertEquals(ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, PARIS), hebdo);
        assertEquals(DayOfWeek.MONDAY, hebdo.getDayOfWeek());
    }

    @Test
    void alr04S1ResumeHebdomadaireAvecSources() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "weekly", "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]");

        publier("GHSA-HEBDO1", LIB, "CRITICAL");
        publier("GHSA-HEBDO2", LIB, "MODERATE");
        assertEquals(0, alertes("nature = 'IMMEDIATE'"), "une règle hebdomadaire n'alerte pas immédiatement");

        Instant lundi = lundiProchain8h();
        List<UUID> crees = resumes.composer("WEEKLY", lundi);

        assertEquals(1, crees.size());
        Mail mail = mailbox.getMailsSentTo("alice@argos.test").get(0);
        assertTrue(mail.getSubject().startsWith("[Argos] Résumé hebdomadaire : 2 éléments cette semaine"), mail.getSubject());
        assertTrue(mail.getText().contains("OSV GHSA-HEBDO1"));
        assertTrue(mail.getText().contains("OSV GHSA-HEBDO2"));
        Api.enTantQue("alice").when().get("/api/v1/alertes/" + crees.get(0))
                .then().statusCode(200)
                .body("nature", equalTo("WEEKLY"))
                .body("contenu.elements", hasSize(2))
                .body("contenu.elements[0].sources[0].nom", equalTo("OSV"));

        // La semaine suivante, rien de nouveau : aucun résumé (ALR-04-S2) et aucun doublon.
        assertTrue(resumes.composer("WEEKLY", lundi.plus(7, ChronoUnit.DAYS)).isEmpty());
        assertEquals(1, mailbox.getMailsSentTo("alice@argos.test").size());
    }

    @Test
    void alr04S2RienDeNouveauAucunResume() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "daily", HAUTE);

        assertTrue(resumes.composer("DAILY", Instant.now().plus(1, ChronoUnit.DAYS)).isEmpty());
        assertTrue(resumes.composer("WEEKLY", lundiProchain8h()).isEmpty());
        assertEquals(0, alertes("true"));
        assertTrue(mailbox.getMailsSentTo("alice@argos.test").isEmpty());
    }

    // ------------------------------------------------------------------ 3.3 contrat desktop

    @Test
    void contratDuClientDesktopListeRemiseEtCloisonnement() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "desktop", "immediate", HAUTE);
        publier("GHSA-POSTE", LIB, "CRITICAL");

        String id = Api.enTantQue("alice").queryParam("canal", "desktop").queryParam("etat", "EN_ATTENTE")
                .when().get("/api/v1/alertes")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].nature", equalTo("IMMEDIATE"))
                .body("[0].canal", equalTo("DESKTOP"))
                .body("[0].titre", equalTo("Vulnérabilité de test GHSA-POSTE"))
                .body("[0].gravite", equalTo("CRITICAL"))
                .body("[0].contenu.elements[0].projets[0].nom", equalTo("app"))
                .body("[0].contenu.elements[0].lien", containsString("/signaux/"))
                .body("[0].contenu.etatResumeIa", equalTo("DISPONIBLE"))
                .extract().path("[0].id");
        assertTrue(mailbox.getMailsSentTo("alice@argos.test").isEmpty(), "aucun e-mail pour une règle desktop");

        // Une alerte d'un autre compte est introuvable et la tentative est journalisée.
        long refus = baseDeTest.count("SELECT count(*) FROM refus_acces");
        Api.compte("jules");
        Api.enTantQue("jules").when().get("/api/v1/alertes/" + id).then().statusCode(404);
        Api.enTantQue("jules").when().post("/api/v1/alertes/" + id + "/remise").then().statusCode(404);
        assertEquals(refus + 2, baseDeTest.count("SELECT count(*) FROM refus_acces"));
        Api.enTantQue("jules").when().get("/api/v1/alertes").then().statusCode(200).body("$", hasSize(0));

        Api.enTantQue("alice").when().post("/api/v1/alertes/" + id + "/remise")
                .then().statusCode(200).body("etat", equalTo("ENVOYEE")).body("envoyeLe", notNullValue());
        Api.enTantQue("alice").queryParam("canal", "DESKTOP").queryParam("etat", "EN_ATTENTE")
                .when().get("/api/v1/alertes").then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    void contratOpenApiDesAlertes() {
        Api.enTantQue("alice").queryParam("format", "json")
                .when().get("/q/openapi")
                .then().statusCode(200)
                .body("paths.'/api/v1/alertes'.get", notNullValue())
                .body("paths.'/api/v1/alertes/{id}'.get", notNullValue())
                .body("paths.'/api/v1/alertes/{id}/remise'.post", notNullValue())
                .body("components.schemas.AlerteVue.properties.keySet()", hasItems("id", "nature", "canal", "etat",
                        "titre", "gravite", "contenu", "creeLe", "envoyeLe"))
                .body("components.schemas.ContenuAlerte.properties.keySet()", hasItems("elements", "resumeIa",
                        "etatResumeIa"));
    }

    @Test
    void remiseRefuseePourUneAlerteEmail() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);
        publier("GHSA-MAIL", LIB, "CRITICAL");
        String id = baseDeTest.string("SELECT id FROM alerte");
        Api.enTantQue("alice").when().post("/api/v1/alertes/" + id + "/remise")
                .then().statusCode(409).body("code", equalTo("CANAL_NON_DESKTOP"));
    }

    // ------------------------------------------------------------------ RLS sous argos_app

    @Test
    void rlsUnCompteNeVoitQueSesAlertes() throws Exception {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);
        regle("alice", "desktop", "immediate", HAUTE);
        publier("GHSA-RLSALR", LIB, "CRITICAL");
        UUID alice = Api.compte("alice");
        UUID jules = Api.compte("jules");
        try (Connection connexion = dataSource.getConnection(); Statement instruction = connexion.createStatement()) {
            connexion.setAutoCommit(false);
            instruction.execute("SET LOCAL ROLE argos_app");
            assertEquals("argos_app", string(instruction, "SELECT current_user"));
            assertEquals(0, count(instruction, "SELECT count(*) FROM alerte"), "sans contexte, aucune ligne");
            instruction.execute("SELECT set_config('argos.compte_courant', '" + jules + "', true)");
            assertEquals(0, count(instruction, "SELECT count(*) FROM alerte"));
            assertEquals(0, instruction.executeUpdate("UPDATE alerte SET etat = 'ENVOYEE'"), "aucune alerte d'autrui modifiable");
            instruction.execute("SELECT set_config('argos.compte_courant', '" + alice + "', true)");
            assertEquals(2, count(instruction, "SELECT count(*) FROM alerte"));
            assertEquals(1, instruction.executeUpdate("UPDATE alerte SET etat = 'ENVOYEE'"), "seule l'alerte DESKTOP est remise");
            SQLException ecriture = assertThrows(SQLException.class,
                    () -> instruction.execute("UPDATE alerte SET titre = 'modifié'"));
            assertEquals("42501", ecriture.getSQLState(), "seuls l'état et la date de remise sont modifiables");
            connexion.rollback();
            connexion.setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------ 5.2 parcours

    @Test
    void parcoursFailleCritiqueAlerteEmailEtDesktopPuisResumeDuLendemainSansDoublon() {
        projet(spaceA, "portail", LIB + "@1.2.0");
        regle("alice", "email", "immediate", HAUTE);
        regle("alice", "desktop", "immediate", HAUTE);
        regle("alice", "email", "daily", "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]");

        Instant debut = Instant.now();
        UUID critique = publier("GHSA-E2E-CRIT", LIB, "CRITICAL");
        Instant fin = Instant.now();

        assertEquals("EXACT", baseDeTest.string("SELECT level FROM project_impact WHERE signal_id = '" + critique + "'"));
        assertEquals(1, alertes("canal = 'EMAIL' AND etat = 'ENVOYEE'"));
        assertEquals(1, alertes("canal = 'DESKTOP' AND etat = 'EN_ATTENTE'"));
        Duration delai = Duration.between(debut, fin);
        assertTrue(delai.compareTo(Duration.ofHours(1)) < 0, "alerte en moins de la cible (Q-4) : " + delai);
        System.out.printf("[parcours ALR] collecte → alerte e-mail et desktop : %d ms%n", delai.toMillis());

        UUID faible = publier("GHSA-E2E-LOW", LIB, "LOW");
        List<UUID> lendemain = resumes.composer("DAILY", Instant.now().plus(1, ChronoUnit.DAYS));

        // Un résumé par canal des règles concernées : e-mail et desktop.
        assertEquals(2, lendemain.size());
        assertEquals(1, alertes("nature = 'DAILY' AND canal = 'EMAIL' AND etat = 'ENVOYEE'"));
        assertEquals(1, alertes("nature = 'DAILY' AND canal = 'DESKTOP' AND etat = 'EN_ATTENTE'"));
        List<Mail> mails = mailbox.getMailsSentTo("alice@argos.test");
        assertEquals(2, mails.size(), "une alerte puis un résumé");
        String resume = mails.get(1).getText();
        assertTrue(resume.contains("GHSA-E2E-LOW"), "l'élément non alerté figure dans le résumé");
        assertFalse(resume.contains("GHSA-E2E-CRIT"), "l'élément déjà alerté n'est pas répété");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM resume_element WHERE signal_id = '" + critique + "'"));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM resume_element WHERE signal_id = '" + faible
                + "' AND alerte_id IS NOT NULL"));
        assertTrue(resumes.composer("DAILY", Instant.now().plus(2, ChronoUnit.DAYS)).isEmpty(), "rien de nouveau");
        // Diffusion répétée : aucun second envoi.
        envoi.traiterEnAttente(Instant.now().plus(2, ChronoUnit.DAYS));
        assertEquals(2, mailbox.getMailsSentTo("alice@argos.test").size());
    }

    private static String string(Statement instruction, String sql) throws Exception {
        try (ResultSet resultat = instruction.executeQuery(sql)) {
            resultat.next();
            return resultat.getString(1);
        }
    }

    private static long count(Statement instruction, String sql) throws Exception {
        try (ResultSet resultat = instruction.executeQuery(sql)) {
            resultat.next();
            return resultat.getLong(1);
        }
    }
}
