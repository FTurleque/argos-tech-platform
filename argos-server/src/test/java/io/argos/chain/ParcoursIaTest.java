package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.aigateway.ClasseDonnee;
import io.argos.aigateway.DemandeIa;
import io.argos.aigateway.EntreeIa;
import io.argos.aigateway.PasserelleIa;
import io.argos.aigateway.RegistrePrompts;
import io.argos.aigateway.ReponseIa;
import io.argos.impact.AnalyseComplementaireIa;
import io.argos.impact.ComplementaryAnalysis;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.SimulatedResumeIa;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Parcours de la passerelle IA de bout en bout (changement {@code encadrer-appels-ia}, tâches 2.5 et
 * 4.2) : collecte OSV simulée, corrélation, alerte avec résumé produit par l'API Ollama simulée, mention
 * IA et sources restituées par l'API REST et l'e-mail ; refus d'une donnée confidentielle ; quota
 * d'espace épuisé puis alerte diffusée sans résumé ; résumé IA d'une actualité web ; analyse
 * complémentaire de la corrélation. Aucun modèle réel ni aucune requête réseau externe.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ParcoursIaTest {

    static final String CHAT = "/ollama/api/chat";
    static final String LIB = "pkg:maven/org.exemple/lib";
    static final String HAUTE = "[{\"type\":\"SEVERITY\",\"value\":\"HIGH\"}]";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    SimulatedResumeIa resumeIa;

    @Inject
    PasserelleIa passerelle;

    @Inject
    AnalyseComplementaireIa analyse;

    @Inject
    MockMailbox mailbox;

    @ConfigProperty(name = "argos.ia.fuseau")
    ZoneId fuseau;

    private UUID spaceA;
    private int versionAvis;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        resumeIa.configure(SimulatedResumeIa.Mode.PASSERELLE);
        spaceA = Api.nouvelEspace("alice", "Espace IA");
        versionAvis = 10;
    }

    static Stub ollama(String contenu) {
        try {
            return Stub.succes(new ObjectMapper().writeValueAsString(Map.of("model", "qwen2.5:1.5b",
                    "message", Map.of("role", "assistant", "content", contenu), "done", true,
                    "prompt_eval_count", 30, "eval_count", 12)));
        } catch (Exception erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    private UUID projet(UUID espace, String nom, String purl) {
        UUID project = projects.create(espace, nom, null, null);
        if (purl != null) {
            inventories.importSbom(project, Fixtures.cyclonedx("1.6", Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                    List.<String[]>of(new String[] {"c0", purl}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        }
        return project;
    }

    private UUID publier(String id) {
        String modifie = "2026-11-" + (versionAvis++) + "T00:00:00Z";
        String json = Fixtures.osv(id, modifie, "Maven", "org.exemple:lib", LIB, "0", "1.3.0")
                .replace("\"severity\":\"HIGH\"", "\"severity\":\"CRITICAL\"");
        Fixtures.stubOsv(id, modifie, json);
        synchronizer.synchronize("OSV");
        return baseDeTest.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private static void regle(String canal) {
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":" + HAUTE + ",\"channel\":\"" + canal
                        + "\",\"frequency\":\"immediate\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);
    }

    // ------------------------------------------------------------------ 4.2 a) résumé d'un avis public, tracé

    @Test
    void iag01S1EtIag05S1AlerteAvecResumeIaTraceMentionEtSources() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("email");
        regle("desktop");
        StubServer.programmer(CHAT, ollama("{\"resume\": \"Faille critique dans org.exemple:lib, corrigée en 1.3.0.\"}"));

        publier("GHSA-IA-OK");

        Api.enTantQue("alice").queryParam("canal", "desktop").queryParam("etat", "EN_ATTENTE")
                .when().get("/api/v1/alertes")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].contenu.etatResumeIa", equalTo("DISPONIBLE"))
                .body("[0].contenu.resumeIa", equalTo("Faille critique dans org.exemple:lib, corrigée en 1.3.0."))
                .body("[0].contenu.mentionResumeIa", equalTo("produit par l'IA"))
                .body("[0].contenu.elements[0].sources[0].identifiant", equalTo("GHSA-IA-OK"))
                .body("[0].contenu.elements[0].sources[0].url", equalTo("https://osv.dev/vulnerability/GHSA-IA-OK"));
        String corps = mailbox.getMailsSentTo("alice@argos.test").get(0).getText();
        assertTrue(corps.contains("Résumé (produit par l'IA, à vérifier sur les sources) :"), corps);
        assertTrue(corps.contains("GHSA-IA-OK"), corps);

        assertEquals(1, StubServer.received(CHAT).size(), "un seul appel pour toutes les alertes du signal");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_appel WHERE tache = 'resume-alerte' AND issue = 'PRODUIT'"
                + " AND espace_id = '" + spaceA + "' AND modele = 'qwen2.5:1.5b' AND version_prompt = 1"
                + " AND jetons_entree = 30 AND jetons_sortie = 12 AND cout_eur = 0"));
    }

    // ------------------------------------------------------------------ 4.2 b) refus d'une donnée confidentielle

    @Test
    void iag02S1DonneeConfidentielleRefuseeEtJournalisee() {
        StubServer.programmer(CHAT, ollama("{\"resume\": \"interdit\"}"));

        ReponseIa reponse = passerelle.demander(new DemandeIa(RegistrePrompts.RESUME_ALERTE, spaceA,
                List.of(EntreeIa.publique("titre", "Avis"), new EntreeIa("contexte", "Clé de chiffrement du projet",
                        ClasseDonnee.CONFIDENTIAL)), List.of()));

        assertEquals(ReponseIa.Issue.REFUS_CLASSE, reponse.issue());
        assertTrue(StubServer.received(CHAT).isEmpty());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_appel WHERE issue = 'REFUS_CLASSE' AND espace_id = '" + spaceA + "'"));
    }

    // ------------------------------------------------------------------ 4.2 c) plafond atteint, alerte sans résumé

    @Test
    void iag03S1QuotaDEspaceEpuiseAlerteDiffuseeSansResume() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("email");
        StubServer.programmer(CHAT, ollama("{\"resume\": \"ne doit pas être demandé\"}"));
        baseDeTest.exec("INSERT INTO ia_quota_jour (jour, portee, appels) VALUES ('" + LocalDate.now(fuseau) + "', '" + spaceA
                + "', 100)");

        publier("GHSA-IA-QUOTA");

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte WHERE etat = 'ENVOYEE' AND etat_resume_ia = 'PLAFOND_ATTEINT'"
                + " AND resume_ia IS NULL"));
        assertTrue(StubServer.received(CHAT).isEmpty(), "aucun appel au-delà du quota de l'espace");
        assertTrue(mailbox.getMailsSentTo("alice@argos.test").get(0).getText().contains("Résumé IA non disponible."));
        Api.enTantQue("alice").when().get("/api/v1/alertes").then().statusCode(200)
                .body("[0].contenu.mentionResumeIa", nullValue());
    }

    @Test
    void ollamaInjoignableAlerteDiffuseeSansResume() {
        projet(spaceA, "app", LIB + "@1.2.0");
        regle("email");

        publier("GHSA-IA-PANNE");

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte WHERE etat = 'ENVOYEE' AND etat_resume_ia = 'INDISPONIBLE'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_appel WHERE issue = 'INDISPONIBLE'"));
    }

    // ------------------------------------------------------------------ IAG-05 : veille web

    @Test
    void iag05S1ResumeIaDUneActualiteWebAvecMentionEtSources() {
        StubServer.programmer("/ia/atom.xml", VeilleWebTest.contenu(VeilleWebTest.ATOM, VeilleWebTest.atom("Blog",
                VeilleWebTest.entree("Quarkus 3.40 LTS", "https://blog.exemple.test/quarkus-340",
                        "Quarkus 3.40 devient la version de support long. " + VeilleWebTest.PIEGE))));
        VeilleWebTest.ajouterFlux("alice", "ESPACE", spaceA, "/ia/atom.xml");
        String id = VeilleWebTest.actualites("alice").extract().path("[0].id");
        StubServer.programmer(CHAT, ollama("{\"resume\": \"Quarkus 3.40 est une version LTS.\"}"));

        Api.enTantQue("alice").when().post("/api/v1/veille-web/actualites/" + id + "/resume-ia")
                .then().statusCode(200)
                .body("etat", equalTo("DISPONIBLE"))
                .body("methode", equalTo("IA_V1"))
                .body("texte", equalTo("Quarkus 3.40 est une version LTS."))
                .body("mention", equalTo("produit par l'IA"))
                .body("sources", hasItem("https://blog.exemple.test/quarkus-340"));
        String message = StubServer.received(CHAT).get(0).body();
        assertTrue(message.contains("classe=\\\"PUBLIC\\\""), message);
        assertTrue(message.contains(VeilleWebTest.PIEGE), "le piège reste une donnée balisée");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_appel WHERE tache = 'resume-actualite' AND issue = 'PRODUIT'"
                + " AND espace_id = '" + spaceA + "'"));

        // Un non-membre ne peut ni lire ni faire résumer l'actualité ; le refus est journalisé.
        long refus = baseDeTest.count("SELECT count(*) FROM refus_acces");
        Api.compte("jules");
        Api.enTantQue("jules").when().post("/api/v1/veille-web/actualites/" + id + "/resume-ia").then().statusCode(404);
        assertEquals(refus + 1, baseDeTest.count("SELECT count(*) FROM refus_acces"));
        assertEquals(1, StubServer.received(CHAT).size());

        // Le résumé produit est gardé : une nouvelle demande ne sollicite pas le modèle.
        StubServer.programmer(CHAT, Stub.status(503));
        Api.enTantQue("alice").when().post("/api/v1/veille-web/actualites/" + id + "/resume-ia")
                .then().statusCode(200)
                .body("etat", equalTo("DISPONIBLE"))
                .body("texte", equalTo("Quarkus 3.40 est une version LTS."))
                .body("mention", equalTo("produit par l'IA"));
        assertEquals(1, StubServer.received(CHAT).size(), "résumé gardé en mémoire : aucun nouvel appel");
        assertEquals(1, baseDeTest.count("SELECT coalesce(sum(appels), 0) FROM ia_quota_jour WHERE portee = 'INSTANCE'"));
    }

    /** Sans IA disponible, ou au-delà du quota du compte, la réponse reprend l'extrait déterministe, sans mention. */
    @Test
    void resumeIaDUneActualiteSansIaOuAuDelaDuQuotaDuCompte() {
        StubServer.programmer("/ia/atom2.xml", VeilleWebTest.contenu(VeilleWebTest.ATOM, VeilleWebTest.atom("Blog",
                VeilleWebTest.entree("Quarkus 3.41", "https://blog.exemple.test/quarkus-341",
                        "Quarkus 3.41 apporte des corrections."))));
        VeilleWebTest.ajouterFlux("alice", "ESPACE", spaceA, "/ia/atom2.xml");
        String id = VeilleWebTest.actualites("alice").extract().path("[0].id");

        StubServer.programmer(CHAT, Stub.status(503));
        Api.enTantQue("alice").when().post("/api/v1/veille-web/actualites/" + id + "/resume-ia")
                .then().statusCode(200)
                .body("etat", equalTo("INDISPONIBLE"))
                .body("methode", equalTo("EXTRAIT_V1"))
                .body("mention", nullValue())
                .body("texte", startsWith("Quarkus 3.41 apporte des corrections."));

        UUID alice = Api.compte("alice");
        baseDeTest.exec("INSERT INTO ia_quota_jour (jour, portee, appels) VALUES ('" + LocalDate.now(fuseau) + "', 'C"
                + alice.toString().replace("-", "") + "', 20) ON CONFLICT (jour, portee) DO UPDATE SET appels = 20");
        StubServer.programmer(CHAT, ollama("{\"resume\": \"ne doit pas être demandé\"}"));
        int recues = StubServer.received(CHAT).size();
        Api.enTantQue("alice").when().post("/api/v1/veille-web/actualites/" + id + "/resume-ia")
                .then().statusCode(200)
                .body("etat", equalTo("PLAFOND_ATTEINT"))
                .body("methode", equalTo("EXTRAIT_V1"))
                .body("mention", nullValue());
        assertEquals(recues, StubServer.received(CHAT).size(), "aucun appel au-delà du quota du compte");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_appel WHERE tache = 'resume-actualite' AND issue = 'QUOTA_COMPTE'"));
    }

    // ------------------------------------------------------------------ analyse complémentaire de la corrélation

    @Test
    void analyseDeCorrelationNeTransmetQueLesProjetsDeLEspaceEtNeCreeAucunImpact() {
        UUID spaceB = Api.nouvelEspace("alice", "Autre espace");
        projet(spaceA, "app", LIB + "@1.2.0");
        UUID autre = projet(spaceA, "service-paiement", null);
        projet(spaceB, "projet-confidentiel-b", null);
        resumeIa.configure(SimulatedResumeIa.Mode.AVAILABLE);
        UUID signal = publier("GHSA-IA-ANALYSE");
        List<UUID> impacts = List.of(baseDeTest.uuid("SELECT id FROM project_impact WHERE signal_id = '" + signal + "'"));
        long impactsAvant = baseDeTest.count("SELECT count(*) FROM project_impact");
        StubServer.reset();
        StubServer.programmer(CHAT, ollama("{\"projets\": [1, 7], \"justification\": \"Même bibliothèque probable.\"}"));

        assertTrue(analyse.budgetAvailable());
        List<ComplementaryAnalysis.Suggestion> suggestions = analyse.analyze(signal, impacts);

        assertEquals(List.of(new ComplementaryAnalysis.Suggestion(autre, "Même bibliothèque probable.")), suggestions,
                "le numéro hors liste est ignoré");
        String message = StubServer.received(CHAT).get(0).body();
        assertTrue(message.contains("1. service-paiement"), message);
        assertTrue(message.contains("classe=\\\"INTERNAL\\\""), message);
        assertFalse(message.contains("projet-confidentiel-b"), "aucun projet d'un autre espace");
        assertFalse(message.contains(". app\\n"), "le projet déjà touché n'est pas candidat");
        assertEquals(impactsAvant, baseDeTest.count("SELECT count(*) FROM project_impact"), "aucun impact créé (COR-04)");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_appel WHERE tache = 'analyse-correlation' AND espace_id = '"
                + spaceA + "'"));
    }
}
