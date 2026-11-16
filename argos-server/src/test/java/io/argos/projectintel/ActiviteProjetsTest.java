package io.argos.projectintel;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.SimulatedSyntheseIa;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.workspace.ContexteBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Activité des projets (tâches 1.1, 1.2, 2.2) : lecture GitHub et GitLab simulée par le serveur HTTP
 * local, agrégation sans identité, consultation sous RLS, quotas, synthèse marquée, purge à 13 mois.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ActiviteProjetsTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Inject
    TestDb db;

    @Inject
    ProjectDirectory projets;

    @Inject
    CollecteActivite collecte;

    @Inject
    PurgeActivite purge;

    @Inject
    ActiviteQueries queries;

    @Inject
    ContexteBase base;

    @Inject
    SimulatedSyntheseIa ia;

    private UUID espace;

    @BeforeEach
    void reset() {
        db.reset();
        espace = Api.nouvelEspace("alice", "Équipe activité");
    }

    private static String il(int jours) {
        return Instant.now().minus(jours, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /**
     * Dépôt GitHub simulé : 3 tickets ouverts et 2 fermés sur 30 jours (dont un créé il y a 40 jours),
     * 2 pull requests ouvertes dont 1 fusionnée, 3 exécutions d'Actions, 1 version publiée, 1 jalon
     * « v1.0 » à 27 tickets fermés sur 30. Chaque élément porte des champs d'identité (bob, carol).
     */
    static void depotGithub(String depot) {
        String base = "/github/repos/" + depot;
        StubServer.on(base + "/issues", Stub.ok("""
                [
                 {"number":1,"title":"Bug","user":{"login":"bob"},"assignee":{"login":"carol"},"created_at":"%s","closed_at":null},
                 {"number":2,"title":"Doc","user":{"login":"bob"},"created_at":"%s","closed_at":"%s"},
                 {"number":3,"title":"Vieux","user":{"login":"carol"},"created_at":"%s","closed_at":"%s"},
                 {"number":4,"title":"Neuf","user":{"login":"bob"},"created_at":"%s","closed_at":null},
                 {"number":5,"title":"PR","user":{"login":"bob"},"created_at":"%s","closed_at":"%s",
                  "pull_request":{"url":"x","merged_at":"%s"}},
                 {"number":6,"title":"PR2","user":{"login":"carol"},"created_at":"%s","closed_at":null,
                  "pull_request":{"url":"y","merged_at":null}}
                ]""".formatted(il(5), il(10), il(8), il(40), il(3), il(1), il(6), il(4), il(4), il(2))));
        StubServer.on(base + "/actions/runs", Stub.ok("""
                {"total_count":3,"workflow_runs":[
                  {"created_at":"%s","status":"completed","conclusion":"success","actor":{"login":"bob"}},
                  {"created_at":"%s","status":"completed","conclusion":"failure","actor":{"login":"carol"}},
                  {"created_at":"%s","status":"in_progress","conclusion":null}]}""".formatted(il(2), il(3), il(0))));
        StubServer.on(base + "/releases", Stub.ok("""
                [{"tag_name":"v0.9","published_at":"%s","draft":false,"author":{"login":"bob"}},
                 {"tag_name":"brouillon","published_at":null,"draft":true},
                 {"tag_name":"v0.1","published_at":"%s","draft":false}]""".formatted(il(6), il(200))));
        StubServer.on(base + "/milestones", Stub.ok("""
                [{"number":7,"title":"v1.0","state":"open","due_on":"2026-12-01T08:00:00Z","open_issues":3,
                  "closed_issues":27,"creator":{"login":"bob"}},
                 {"number":2,"title":"v0.5","state":"closed","closed_at":"%s","open_issues":0,"closed_issues":4}]
                """.formatted(il(300))));
    }

    private UUID projetGithub(String depot) {
        return projets.create(espace, "app", new RepositoryRef("GITHUB", depot, "main", null, null), List.of());
    }

    @Test
    void pin01S1ProjetReliéGithubIndicateursSur30Jours() {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");

        assertEquals(CollecteActivite.Issue.A_JOUR, collecte.collecterProjet(projet, Instant.now()));

        LocalDate aujourdhui = LocalDate.now(PARIS);
        Api.en("alice").queryParam("jours", 30).when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200)
                .body("periode.jours", equalTo(30))
                .body("periode.fin", equalTo(aujourdhui.toString()))
                .body("periode.debut", equalTo(aujourdhui.minusDays(29).toString()))
                .body("compteurs.ticketsOuverts", equalTo(3))
                .body("compteurs.ticketsFermes", equalTo(2))
                .body("compteurs.fusionsOuvertes", equalTo(2))
                .body("compteurs.fusionsFusionnees", equalTo(1))
                .body("compteurs.pipelinesReussis", equalTo(1))
                .body("compteurs.pipelinesEchoues", equalTo(1))
                .body("compteurs.pipelinesAutres", equalTo(1))
                .body("compteurs.versionsPubliees", equalTo(1))
                .body("jalons", hasSize(1))
                .body("jalons[0].titre", equalTo("v1.0"))
                .body("jalons[0].etat", equalTo("OUVERT"))
                .body("jalons[0].echeance", equalTo("2026-12-01"))
                .body("fraicheur.etat", equalTo("A_JOUR"))
                .body("fraicheur.complet", equalTo(true))
                .body("fraicheur.derniereMiseAJour", not(nullValue()));
        // Sur 7 jours, seuls les événements de la semaine comptent.
        Api.en("alice").queryParam("jours", 7).when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200).body("compteurs.ticketsOuverts", equalTo(2)).body("compteurs.ticketsFermes", equalTo(1))
                .body("compteurs.versionsPubliees", equalTo(1));
        Api.en("alice").queryParam("jours", 400).when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(400).body("code", equalTo("PERIODE_INVALIDE"));
    }

    @Test
    void contratGithubLectureSeuleSansRedirectionNiJeton() {
        depotGithub("org/app");
        collecte.collecterProjet(projetGithub("org/app"), Instant.now());

        List<StubServer.Received> requetes = StubServer.received("/github/");
        assertEquals(4, requetes.size(), "une page par liste : " + requetes.stream().map(StubServer.Received::pathAndQuery).toList());
        assertTrue(requetes.stream().allMatch(r -> "GET".equals(r.method())), "lecture seule : GET uniquement");
        assertTrue(requetes.stream().anyMatch(r -> r.pathAndQuery().startsWith("/github/repos/org/app/issues?state=all&since=")));
        assertTrue(requetes.stream().anyMatch(r -> r.pathAndQuery().startsWith("/github/repos/org/app/actions/runs?created=%3E%3D")));
        assertTrue(requetes.stream().allMatch(r -> r.headers().keySet().stream().noneMatch(h -> h.equalsIgnoreCase("Authorization"))),
                "aucun jeton configuré, aucun envoyé");
        assertEquals(List.of("application/vnd.github+json"), requetes.get(0).headers().get("Accept"));
    }

    @Test
    void contratGitlab() {
        String base = "/gitlab/api/v4/projects/groupe%2Fapp";
        StubServer.on(base + "/issues", Stub.ok("""
                [{"iid":1,"author":{"username":"bob"},"created_at":"%s","closed_at":"%s"},
                 {"iid":2,"assignees":[{"username":"carol"}],"created_at":"%s","closed_at":null}]""".formatted(il(9), il(2), il(4))));
        StubServer.on(base + "/merge_requests", Stub.ok("""
                [{"iid":3,"author":{"username":"bob"},"created_at":"%s","merged_at":"%s","merged_by":{"username":"carol"}}]"""
                .formatted(il(6), il(5))));
        StubServer.on(base + "/pipelines", Stub.ok("""
                [{"id":1,"status":"success","created_at":"%s"},{"id":2,"status":"failed","created_at":"%s"},
                 {"id":3,"status":"canceled","created_at":"%s"},{"id":4,"status":"success","created_at":"%s"}]"""
                .formatted(il(1), il(2), il(3), il(45))));
        StubServer.on(base + "/releases", Stub.ok("""
                [{"tag_name":"v2","released_at":"%s","author":{"username":"bob"}}]""".formatted(il(3))));
        StubServer.on(base + "/milestones", Stub.ok("""
                [{"id":11,"iid":1,"title":"Sprint 4","state":"active","due_date":"2026-12-15","updated_at":"%s"},
                 {"id":12,"iid":2,"title":"Ancien","state":"closed","updated_at":"%s"}]""".formatted(il(1), il(100))));
        StubServer.on(base + "/issues_statistics", Stub.ok("""
                {"statistics":{"counts":{"all":30,"closed":27,"opened":3}}}"""));
        UUID projet = projets.create(espace, "app", new RepositoryRef("GITLAB", "groupe/app", "main", null, null), List.of());

        assertEquals(CollecteActivite.Issue.A_JOUR, collecte.collecterProjet(projet, Instant.now()));

        Api.en("alice").when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200)
                .body("compteurs.ticketsOuverts", equalTo(2))
                .body("compteurs.ticketsFermes", equalTo(1))
                .body("compteurs.fusionsOuvertes", equalTo(1))
                .body("compteurs.fusionsFusionnees", equalTo(1))
                .body("compteurs.pipelinesReussis", equalTo(1))
                .body("compteurs.pipelinesEchoues", equalTo(1))
                .body("compteurs.pipelinesAutres", equalTo(1))
                .body("compteurs.versionsPubliees", equalTo(1))
                .body("jalons", hasSize(1))
                .body("jalons[0].titre", equalTo("Sprint 4"))
                .body("jalons[0].avancement.libelle", equalTo("27 sur 30 (90 %)"));
        assertTrue(StubServer.received("/gitlab/").stream().allMatch(r -> "GET".equals(r.method())));
        assertTrue(StubServer.received(base + "/issues_statistics?milestone=Sprint%204").size() == 1,
                "le titre du jalon est encodé dans l'adresse");
    }

    @Test
    void pin02S1EtS2AvancementAvecDenominateurSeulement() {
        depotGithub("org/app");
        UUID avecJalon = projetGithub("org/app");
        collecte.collecterProjet(avecJalon, Instant.now());
        Api.en("alice").when().get("/api/v1/projects/" + avecJalon + "/activite")
                .then().statusCode(200)
                .body("jalons[0].ticketsFermes", equalTo(27))
                .body("jalons[0].ticketsTotal", equalTo(30))
                .body("jalons[0].avancement.libelle", equalTo("27 sur 30 (90 %)"))
                .body("jalons[0].avancement.pourcentage", equalTo(90));

        StubServer.on("/github/repos/org/sans-jalon/issues", Stub.ok("[]"));
        StubServer.on("/github/repos/org/sans-jalon/actions/runs", Stub.ok("{\"workflow_runs\":[]}"));
        StubServer.on("/github/repos/org/sans-jalon/releases", Stub.ok("[]"));
        StubServer.on("/github/repos/org/sans-jalon/milestones", Stub.ok("[]"));
        UUID sansJalon = projetGithub("org/sans-jalon");
        collecte.collecterProjet(sansJalon, Instant.now());
        String corps = Api.en("alice").when().get("/api/v1/projects/" + sansJalon + "/activite")
                .then().statusCode(200).body("jalons", empty()).extract().asString();
        assertFalse(corps.contains("%") || corps.contains("pourcentage") || corps.contains("avancement"),
                "aucun pourcentage d'avancement sans jalon : " + corps);
    }

    @Test
    void pin03S1AucunIndicateurParPersonne() throws Exception {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        collecte.collecterProjet(projet, Instant.now());

        String corps = Api.en("alice").queryParam("contributeur", "bob").when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200).body("compteurs.ticketsOuverts", equalTo(3)).extract().asString();
        assertFalse(corps.contains("bob") || corps.contains("carol"), "aucune identité restituée : " + corps);
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/activite/contributeurs/bob").then().statusCode(404);

        // Aucune identité enregistrée, et aucune colonne qui pourrait en porter (revue du schéma outillée).
        assertEquals(0, db.count("""
                SELECT (SELECT count(*) FROM activite_compteur c WHERE c::text ~* '(bob|carol)')
                     + (SELECT count(*) FROM activite_jalon j WHERE j::text ~* '(bob|carol)')
                     + (SELECT count(*) FROM activite_collecte a WHERE a::text ~* '(bob|carol)')"""));
        assertEquals(0, db.count("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name IN ('activite_compteur', 'activite_jalon', 'activite_collecte')
                  AND column_name ~* '(auteur|author|user|login|assign|review|contribut|personne|person|mail|compte|account|membre|owner|par$|_by$)'"""));
        assertEquals(20, db.count("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name IN ('activite_compteur', 'activite_jalon', 'activite_collecte')"""),
                "colonnes revues : toute nouvelle colonne impose de revoir ce test");
        Api.en("alice").queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("paths.keySet().findAll { it.contains('/activite') }", hasSize(2))
                .body("paths.'/api/v1/projects/{projectId}/activite'.get.parameters.name", hasItems("jours"))
                .body("paths.'/api/v1/projects/{projectId}/activite'.get.parameters.name", not(hasItems("contributeur")));
    }

    @Test
    void pin04S1SyntheseMarqueeEtFondeeSurLesIndicateurs() {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        String url = "/api/v1/projects/" + projet + "/activite/synthese";

        Api.en("alice").when().get(url).then().statusCode(200).body("etat", equalTo("SANS_DONNEES"))
                .body("mention", equalTo("produite par l'IA")).body("texte", nullValue());
        assertTrue(ia.recues().isEmpty(), "aucun appel sans donnée collectée");

        collecte.collecterProjet(projet, Instant.now());
        Api.en("alice").when().get(url).then().statusCode(200)
                .body("etat", equalTo("DISPONIBLE"))
                .body("mention", equalTo("produite par l'IA"))
                .body("texte", containsString("27 sur 30 (90 %)"))
                .body("periode.jours", equalTo(30))
                .body("indicateurs", hasItems("compteurs.ticketsOuverts", "compteurs.ticketsFermes", "jalons"));
        assertEquals(1, ia.recues().size());
        assertEquals(3, ia.recues().get(0).compteurs().ticketsOuverts(), "la synthèse reçoit les indicateurs affichés");

        ia.configure(SimulatedSyntheseIa.Mode.CHIFFRE_INVENTE);
        Api.en("alice").when().get(url).then().statusCode(200).body("etat", equalTo("REFUSEE")).body("texte", nullValue())
                .body("mention", equalTo("produite par l'IA"));
        ia.configure(SimulatedSyntheseIa.Mode.UNAVAILABLE);
        Api.en("alice").when().get(url).then().statusCode(200).body("etat", equalTo("INDISPONIBLE"));
        ia.configure(SimulatedSyntheseIa.Mode.BUDGET_EXHAUSTED);
        Api.en("alice").when().get(url).then().statusCode(200).body("etat", equalTo("PLAFOND_ATTEINT"));
        assertTrue(ia.recues().isEmpty(), "plafond atteint : aucun appel");
        ia.configure(SimulatedSyntheseIa.Mode.REFUSED);
        Api.en("alice").when().get(url).then().statusCode(200).body("etat", equalTo("REFUSEE"));
        // Les indicateurs restent consultables quel que soit l'état de la synthèse.
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(200)
                .body("compteurs.ticketsOuverts", equalTo(3));
    }

    @Test
    void pin05S1QuotaEpuiseCollecteReporteeEtAgeAffiche() {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        UUID autre = projetGithub("org/autre");
        Instant premiere = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        assertEquals(CollecteActivite.Issue.A_JOUR, collecte.collecterProjet(projet, premiere));

        Instant reinitialisation = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        StubServer.reset();
        StubServer.on("/github/repos/org/app/issues", new Stub(403, "{\"message\":\"API rate limit exceeded\"}",
                Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(reinitialisation.getEpochSecond())), 0));

        CollecteActivite.Bilan bilan = collecte.collecter(Instant.now());

        assertEquals(new CollecteActivite.Bilan(0, 2, 0), bilan, "le second projet GitHub est reporté sans appel");
        assertEquals(1, StubServer.received("/github/").size(), "aucun appel après le quota épuisé");
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200)
                .body("fraicheur.etat", equalTo("REPORTEE_QUOTA"))
                .body("fraicheur.derniereMiseAJour", equalTo(premiere.toString()))
                .body("fraicheur.prochaineCollecte", equalTo(reinitialisation.toString()))
                .body("fraicheur.motif", containsString("quota"))
                .body("compteurs.ticketsOuverts", equalTo(2));
        Api.en("alice").when().get("/api/v1/projects/" + autre + "/activite")
                .then().statusCode(200).body("fraicheur.etat", equalTo("REPORTEE_QUOTA"))
                .body("fraicheur.derniereMiseAJour", nullValue());

        // Avant la réinitialisation, rien n'est tenté ; après, la collecte reprend.
        assertEquals(new CollecteActivite.Bilan(0, 0, 0), collecte.collecter(reinitialisation.minusSeconds(60)));
        assertEquals(1, StubServer.received("/github/").size());
        depotGithub("org/app");
        StubServer.on("/github/repos/org/autre/issues", Stub.ok("[]"));
        StubServer.on("/github/repos/org/autre/actions/runs", Stub.ok("{\"workflow_runs\":[]}"));
        StubServer.on("/github/repos/org/autre/releases", Stub.ok("[]"));
        StubServer.on("/github/repos/org/autre/milestones", Stub.ok("[]"));
        assertEquals(new CollecteActivite.Bilan(2, 0, 0), collecte.collecter(reinitialisation.plusSeconds(1)));
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200).body("fraicheur.etat", equalTo("A_JOUR"))
                .body("fraicheur.derniereMiseAJour", equalTo(reinitialisation.plusSeconds(1).toString()));
    }

    @Test
    void echecDeForgeSansJetonDansLeMotifEtCollecteIdempotente() {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        collecte.collecterProjet(projet, Instant.now());
        long lignes = db.count("SELECT count(*) FROM activite_compteur WHERE project_id = '" + projet + "'");
        collecte.collecterProjet(projet, Instant.now());
        assertEquals(lignes, db.count("SELECT count(*) FROM activite_compteur WHERE project_id = '" + projet + "'"));
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/activite").then().body("compteurs.ticketsOuverts", equalTo(3));

        StubServer.on("/github/repos/org/app/issues", Stub.status(500));
        assertEquals(CollecteActivite.Issue.ECHEC, collecte.collecterProjet(projet, Instant.now()));
        Api.en("alice").when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(200)
                .body("fraicheur.etat", equalTo("ECHEC")).body("fraicheur.motif", equalTo("HTTP 500 de localhost"))
                .body("compteurs.ticketsOuverts", equalTo(3));
        // Redirection non suivie : le jeton ne part jamais vers un autre hôte.
        StubServer.on("/github/repos/org/app/issues", new Stub(302, "", Map.of("Location", "http://ailleurs.example/"), 0));
        assertEquals(CollecteActivite.Issue.ECHEC, collecte.collecterProjet(projet, Instant.now()));
    }

    @Test
    void cloisonnementSousArgosAppEtRefusJournalise() throws Exception {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        collecte.collecterProjet(projet, Instant.now());
        UUID alice = Api.compte("alice");
        UUID jules = Api.compte("jules");

        // Garde-fou en base : sous argos_app, un non-membre ne lit aucune ligne d'activité.
        List<Long> vuParAlice = compterSousArgosApp(alice, projet);
        assertTrue(vuParAlice.get(0) > 0 && vuParAlice.get(1) == 1 && vuParAlice.get(2) == 1, "membre : " + vuParAlice);
        assertEquals(List.of(0L, 0L, 0L), compterSousArgosApp(jules, projet), "non-membre : aucune ligne");
        VueActivite vueJules = queries.activite(projet, jules, 30);
        assertEquals(0, vueJules.compteurs().ticketsOuverts());
        assertTrue(vueJules.jalons().isEmpty());
        assertEquals("JAMAIS_COLLECTE", vueJules.fraicheur().etat());
        assertNull(vueJules.fraicheur().derniereMiseAJour());

        // Par l'API : 404 sans révéler l'existence, refus journalisé.
        Api.en("jules").when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(404);
        Api.en("jules").when().get("/api/v1/projects/" + projet + "/activite/synthese").then().statusCode(404);
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + jules
                + "' AND ressource = 'GET /api/v1/projects/" + projet + "/activite' AND motif = 'NON_MEMBRE'"));
        Api.en("alice").when().get("/api/v1/projects/" + UUID.randomUUID() + "/activite").then().statusCode(404);
        io.restassured.RestAssured.given().when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(401);
    }

    private List<Long> compterSousArgosApp(UUID compte, UUID projet) {
        return base.executer(new ContexteBase.Contexte(compte, null, null), c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
                    SELECT (SELECT count(*) FROM activite_compteur WHERE project_id = '%1$s'),
                           (SELECT count(*) FROM activite_jalon WHERE project_id = '%1$s'),
                           (SELECT count(*) FROM activite_collecte WHERE project_id = '%1$s'),
                           current_user""".formatted(projet))) {
                rs.next();
                assertEquals("argos_app", rs.getString(4), "lecture sous argos_app, jamais sous le super-utilisateur");
                return List.of(rs.getLong(1), rs.getLong(2), rs.getLong(3));
            }
        });
    }

    @Test
    void purgeDeLHistoriqueAuDelaDeTreizeMois() {
        UUID projet = projetGithub("org/app");
        LocalDate aujourdhui = LocalDate.of(2026, 11, 16);
        db.exec("""
                INSERT INTO activite_compteur(project_id, jour, type, valeur) VALUES
                  ('%1$s', '2025-10-15', 'TICKET_OUVERT', 4),
                  ('%1$s', '2025-10-16', 'TICKET_OUVERT', 5),
                  ('%1$s', '2026-11-10', 'TICKET_FERME', 2)""".formatted(projet));
        db.exec("""
                INSERT INTO activite_jalon(project_id, reference, titre, etat, tickets_fermes, tickets_total, releve_le) VALUES
                  ('%1$s', '1', 'ancien', 'FERME', 1, 1, '2025-09-01T00:00:00Z'),
                  ('%1$s', '2', 'récent', 'OUVERT', 1, 2, '2026-11-15T00:00:00Z')""".formatted(projet));
        db.exec("""
                INSERT INTO activite_collecte(project_id, etat, derniere_mise_a_jour, prochaine_collecte, donnees_depuis, tentee_le)
                VALUES ('%s', 'A_JOUR', now(), now(), '2025-09-01', now())""".formatted(projet));

        int supprimees = purge.purger(aujourdhui);

        assertEquals(2, supprimees, "un compteur du 15 octobre 2025 et un jalon relevé en septembre 2025");
        assertEquals("2025-10-16,2026-11-10", db.string(
                "SELECT string_agg(jour::text, ',' ORDER BY jour) FROM activite_compteur WHERE project_id = '" + projet + "'"),
                "le 16 octobre 2025 est à 13 mois exactement : conservé");
        assertEquals("récent", db.string("SELECT string_agg(titre, ',') FROM activite_jalon WHERE project_id = '" + projet + "'"));
        assertEquals("2025-10-16", db.string("SELECT donnees_depuis::text FROM activite_collecte WHERE project_id = '" + projet + "'"));
        assertEquals(0, purge.purger(aujourdhui), "rejouée, la purge ne supprime rien de plus");
    }
}
