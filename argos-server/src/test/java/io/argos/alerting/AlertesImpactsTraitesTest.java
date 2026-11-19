package io.argos.alerting;

import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.impact.CorrelationEngine;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Aucune alerte pour un impact écarté ou résolu (changement automatiser-chaine-veille, volet C, tâche 3.4 ; ALR-07,
 * décision J5 du porteur du projet, 2026-11-19). Chaîne réelle : collecte OSV simulée, corrélation, transitions par
 * l'API avec des jetons réels, évaluation des règles, résumés et envoi par la boîte aux lettres simulée.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class AlertesImpactsTraitesTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";
    private static final String HAUTE = "[{\"type\":\"SEVERITY\",\"value\":\"HIGH\"}]";

    @Inject
    TestDb db;

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

    private UUID espace;
    private int versionAvis;

    /** alice administre l'espace ; jules y est LECTEUR (destinataire), karim CONTRIBUTEUR (traite les impacts). */
    @BeforeEach
    void reset() {
        db.reset();
        espace = Api.nouvelEspace("alice", "Espace alertes traitées");
        Api.compte("jules");
        Api.compte("karim");
        Api.ajouterMembre("alice", espace.toString(), "jules", "LECTEUR");
        Api.ajouterMembre("alice", espace.toString(), "karim", "CONTRIBUTEUR");
        versionAvis = 10;
    }

    private UUID projet(String nom, Instant genere, String... purls) {
        UUID projet = projects.create(espace, nom, null, null);
        importer(projet, genere, purls);
        return projet;
    }

    private void importer(UUID projet, Instant genere, String... purls) {
        List<String[]> composants = new java.util.ArrayList<>();
        for (int i = 0; i < purls.length; i++) {
            composants.add(new String[] {"c" + i, purls[i]});
        }
        inventories.importSbom(projet, Fixtures.cyclonedx("1.6", genere.toString(), composants, List.of("c0"))
                .getBytes(StandardCharsets.UTF_8), "ci");
    }

    private UUID publier(String id, String corrige) {
        String modifie = "2026-11-" + (versionAvis++) + "T00:00:00Z";
        String json = Fixtures.osv(id, modifie, "Maven", "org.exemple:lib", LIB, "0", corrige)
                .replace("\"severity\":\"HIGH\"", "\"severity\":\"CRITICAL\"");
        Fixtures.stubOsv(id, modifie, json);
        synchronizer.synchronize("OSV");
        return db.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private static void regle(String utilisateur, String canal, String frequence) {
        Api.en(utilisateur).contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":" + HAUTE + ",\"channel\":\"" + canal + "\",\"frequency\":\""
                        + frequence + "\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);
    }

    private static void transition(String utilisateur, UUID impact, Map<String, String> corps) {
        Api.en(utilisateur).contentType(ContentType.JSON).body(corps)
                .when().post("/api/v1/impacts/" + impact + "/transitions").then().statusCode(200);
    }

    private long alertes(String condition) {
        return db.count("SELECT count(*) FROM alerte WHERE " + condition);
    }

    private static Instant recent() {
        return Instant.now().minus(2, ChronoUnit.DAYS);
    }

    @Test
    void alr07S1ImpactEcarteAvantLaRemiseAuPoste() {
        UUID projet = projet("app", recent(), LIB + "@1.2.0");
        regle("jules", "desktop", "immediate");
        UUID signal = publier("GHSA-ALR7-1", "1.3.0");
        assertEquals(1, alertes("canal = 'DESKTOP' AND etat = 'EN_ATTENTE'"), "alerte en attente de remise au poste");
        UUID impact = db.uuid("SELECT id FROM project_impact WHERE project_id = '" + projet + "'");

        transition("karim", impact, Map.of("vers", "ECARTE", "motif", "Bibliothèque non chargée en production"));

        assertEquals(1, alertes("canal = 'DESKTOP' AND etat = 'ANNULEE'"));
        Api.en("jules").queryParam("canal", "DESKTOP").queryParam("etat", "EN_ATTENTE")
                .when().get("/api/v1/alertes").then().statusCode(200).body("$", empty());

        // Rouvert par un contributeur puis recorrélé : l'impact redevient éligible (ALR-07, ALR-01).
        transition("karim", impact, Map.of("vers", "OUVERT"));
        engine.correlate(signal, Optional.empty());
        assertEquals(1, alertes("canal = 'DESKTOP' AND etat = 'EN_ATTENTE'"), "même alerte remise en attente (ALR-03)");
        assertEquals(1, alertes("nature = 'IMMEDIATE'"));
    }

    @Test
    void alr07S1AlerteEmailEnAttenteNonEnvoyee() {
        projet("app", recent(), LIB + "@1.2.0");
        regle("jules", "email", "immediate");
        publier("GHSA-ALR7-2", "1.3.0");
        assertEquals(1, alertes("canal = 'EMAIL' AND etat = 'ENVOYEE'"));
        UUID impact = db.uuid("SELECT id FROM project_impact");
        // Alerte en attente d'une nouvelle tentative d'envoi, comme après un échec SMTP.
        db.exec("UPDATE alerte SET etat = 'EN_ATTENTE', envoye_le = NULL, prochain_essai = now() - interval '1 minute'");
        mailbox.clear();

        transition("karim", impact, Map.of("vers", "ECARTE", "motif", "Faux positif"));
        assertEquals(1, alertes("canal = 'EMAIL' AND etat = 'ANNULEE'"));

        // Garde-fou de l'envoi, si l'annulation n'avait pas eu lieu : l'alerte n'est pas diffusée.
        db.exec("UPDATE alerte SET etat = 'EN_ATTENTE'");
        Map<UUID, EnvoiAlertes.Issue> issues = envoi.traiterEnAttente(Instant.now());
        assertEquals(List.of(EnvoiAlertes.Issue.IGNOREE), List.copyOf(issues.values()));
        assertEquals(1, alertes("canal = 'EMAIL' AND etat = 'ANNULEE'"));
        assertTrue(mailbox.getMailsSentTo("jules@argos.test").isEmpty(), "aucune alerte envoyée à jules");
    }

    @Test
    void impactEcarteJamaisEvalueParUneNouvelleCorrelation() {
        UUID projet = projet("app", recent(), LIB + "@1.2.0");
        publier("GHSA-ALR7-3", "1.3.0");
        UUID impact = db.uuid("SELECT id FROM project_impact WHERE project_id = '" + projet + "'");
        transition("karim", impact, Map.of("vers", "ECARTE", "motif", "Code mort"));
        regle("jules", "email", "immediate");

        publier("GHSA-ALR7-3", "1.4.0");

        assertEquals("ECARTE", db.string("SELECT state FROM project_impact WHERE id = '" + impact + "'"));
        assertEquals(0, alertes("nature = 'IMMEDIATE'"), "aucune alerte pour un impact écarté");
        assertTrue(mailbox.getMailsSentTo("jules@argos.test").isEmpty());
    }

    @Test
    void alr07S2ResumeSansImpactResolu() {
        UUID projet = projet("app", Instant.now().minus(40, ChronoUnit.DAYS), LIB + "@1.2.0", "pkg:maven/org.autre/outil@1.0.0");
        regle("jules", "email", "daily");
        publier("GHSA-ALR7-4", "1.3.0");
        UUID impact = db.uuid("SELECT id FROM project_impact WHERE project_id = '" + projet + "'");
        assertEquals("PROBABLE", db.string("SELECT level FROM project_impact WHERE id = '" + impact + "'"));
        assertEquals(1, db.count("SELECT count(*) FROM resume_element WHERE frequence = 'DAILY'"));

        // Résolu par le système avant l'heure du résumé : le composant quitte l'inventaire.
        importer(projet, recent(), "pkg:maven/org.autre/outil@1.0.0");
        assertEquals("RESOLU", db.string("SELECT state FROM project_impact WHERE id = '" + impact + "'"));

        List<UUID> crees = resumes.composer("DAILY", Instant.now().plus(1, ChronoUnit.DAYS));

        assertTrue(crees.isEmpty(), "aucun résumé sans élément restant (ALR-04)");
        assertEquals(0, alertes("nature = 'DAILY'"));
        assertEquals(0, db.count("SELECT count(*) FROM resume_element"), "élément retiré du résumé");
        assertTrue(mailbox.getMailsSentTo("jules@argos.test").isEmpty());
    }
}
