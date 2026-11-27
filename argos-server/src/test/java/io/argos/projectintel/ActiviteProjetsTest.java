package io.argos.projectintel;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.ProjectDirectory;
import io.argos.scm.ActiviteDepot;
import io.argos.scm.ParametresForges;
import io.argos.scm.RepositoryActivity;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.SimulatedSyntheseIa;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.Substitution;
import io.argos.testsupport.TestDb;
import io.argos.commun.ContexteBase;
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
    TestDb baseDeTest;

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
    SimulatedSyntheseIa syntheseIa;

    @Inject
    RepositoryActivity forges;

    private UUID espace;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Équipe activité");
    }

    private static String ilYa(int jours) {
        return Instant.now().minus(jours, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /**
     * Dépôt GitHub simulé : 3 tickets ouverts et 2 fermés sur 30 jours (dont un créé il y a 40 jours),
     * 2 pull requests ouvertes dont 1 fusionnée, 3 exécutions d'Actions, 1 version publiée, 1 jalon
     * « v1.0 » à 27 tickets fermés sur 30. Chaque élément porte des champs d'identité (bob, carol).
     */
    static void depotGithub(String depot) {
        String base = "/github/repos/" + depot;
        StubServer.programmer(base + "/issues", Stub.succes("""
                [
                 {"number":1,"title":"Bug","user":{"login":"bob"},"assignee":{"login":"carol"},"created_at":"%s","closed_at":null},
                 {"number":2,"title":"Doc","user":{"login":"bob"},"created_at":"%s","closed_at":"%s"},
                 {"number":3,"title":"Vieux","user":{"login":"carol"},"created_at":"%s","closed_at":"%s"},
                 {"number":4,"title":"Neuf","user":{"login":"bob"},"created_at":"%s","closed_at":null},
                 {"number":5,"title":"PR","user":{"login":"bob"},"created_at":"%s","closed_at":"%s",
                  "pull_request":{"url":"x","merged_at":"%s"}},
                 {"number":6,"title":"PR2","user":{"login":"carol"},"created_at":"%s","closed_at":null,
                  "pull_request":{"url":"y","merged_at":null}}
                ]""".formatted(ilYa(5), ilYa(10), ilYa(8), ilYa(40), ilYa(3), ilYa(1), ilYa(6), ilYa(4), ilYa(4), ilYa(2))));
        StubServer.programmer(base + "/actions/runs", Stub.succes("""
                {"total_count":3,"workflow_runs":[
                  {"created_at":"%s","status":"completed","conclusion":"success","actor":{"login":"bob"}},
                  {"created_at":"%s","status":"completed","conclusion":"failure","actor":{"login":"carol"}},
                  {"created_at":"%s","status":"in_progress","conclusion":null}]}""".formatted(ilYa(2), ilYa(3), ilYa(0))));
        StubServer.programmer(base + "/releases", Stub.succes("""
                [{"tag_name":"v0.9","published_at":"%s","draft":false,"author":{"login":"bob"}},
                 {"tag_name":"brouillon","published_at":null,"draft":true},
                 {"tag_name":"v0.1","published_at":"%s","draft":false}]""".formatted(ilYa(6), ilYa(200))));
        StubServer.programmer(base + "/milestones", Stub.succes("""
                [{"number":7,"title":"v1.0","state":"open","due_on":"2026-12-01T08:00:00Z","open_issues":3,
                  "closed_issues":27,"creator":{"login":"bob"}},
                 {"number":2,"title":"v0.5","state":"closed","closed_at":"%s","open_issues":0,"closed_issues":4}]
                """.formatted(ilYa(300))));
    }

    private UUID projetGithub(String depot) {
        return projets.create(espace, "app", new RepositoryRef("GITHUB", depot, "main", null, null), List.of());
    }

    /**
     * Frontière d'erreur du passage (arc42 §8.5) : une erreur imprévue pour un projet compte un échec, le projet suivant
     * est collecté et le passage planifié relance l'erreur.
     */
    @Test
    void projetEnErreurImprevueNInterromptPasLePassage() {
        depotGithub("demo/argos");
        projets.create(espace, "fautif", new RepositoryRef("GITHUB", "demo/fautif", "main", null, null), List.of());
        projets.create(espace, "sain", new RepositoryRef("GITHUB", "demo/argos", "main", null, null), List.of());
        RepositoryActivity reel = Substitution.reel(forges);
        IllegalArgumentException imprevue = new IllegalArgumentException("erreur imprévue simulée");
        ParametresForges forgesFactices = new ParametresForges(Duration.ofSeconds(1), "http://localhost", "http://localhost",
                Optional.empty(), Optional.empty());
        RepositoryActivity doublure = new RepositoryActivity(forgesFactices, 1, 0, 1, Clock.systemUTC()) {
            @Override
            public ActiviteDepot lire(RepositoryRef ref, Instant depuis) {
                if ("demo/fautif".equals(ref.repository())) {
                    throw imprevue;
                }
                return reel.lire(ref, depuis);
            }
        };

        CollecteActivite.Bilan bilan;
        try (Substitution substitution = Substitution.remplacer(collecte, "forges", doublure)) {
            bilan = collecte.collecter(Instant.now());
        }

        assertEquals(1, bilan.aJour(), "le projet sain est collecté");
        assertEquals(1, bilan.echecs());
        assertEquals(List.of(imprevue), bilan.erreursImprevues());
        assertThrows(IllegalArgumentException.class, bilan::relancerErreurImprevue);
    }

    /**
     * AUD-PER-13 : les forges sont collectées chacune sur sa tâche, les projets échus seulement ; au passage suivant,
     * aucun projet n'est échu et aucune forge n'est appelée.
     */
    @Test
    void lesForgesSontCollecteesEnParalleleEtLesProjetsNonEchusSautes() {
        projets.create(espace, "app github", new RepositoryRef("GITHUB", "demo/github", "main", null, null), List.of());
        projets.create(espace, "app gitlab", new RepositoryRef("GITLAB", "groupe/gitlab", "main", null, null), List.of());
        Map<String, String> filsParForge = new ConcurrentHashMap<>();
        ParametresForges forgesFactices = new ParametresForges(Duration.ofSeconds(1), "http://localhost", "http://localhost",
                Optional.empty(), Optional.empty());
        RepositoryActivity doublure = new RepositoryActivity(forgesFactices, 1, 0, 1, Clock.systemUTC()) {
            @Override
            public ActiviteDepot lire(RepositoryRef ref, Instant depuis) {
                filsParForge.put(ref.provider(), Thread.currentThread().getName());
                return new ActiviteDepot(List.of(), List.of(), List.of(), List.of(), List.of(), true);
            }
        };

        CollecteActivite.Bilan premier;
        CollecteActivite.Bilan second;
        Map<String, String> filsDuPremierPassage;
        try (Substitution substitution = Substitution.remplacer(collecte, "forges", doublure)) {
            premier = collecte.collecter(Instant.now());
            filsDuPremierPassage = Map.copyOf(filsParForge);
            filsParForge.clear();
            second = collecte.collecter(Instant.now());
        }

        assertEquals(2, premier.aJour());
        assertEquals(Set.of("GITHUB", "GITLAB"), filsDuPremierPassage.keySet());
        assertTrue(filsDuPremierPassage.values().stream().allMatch(fil -> fil.equals("argos-collecte-activite")),
                filsDuPremierPassage.toString());
        assertEquals(0, second.aJour(), "aucun projet échu au passage suivant");
        assertTrue(filsParForge.isEmpty(), "aucune forge appelée pour un projet non échu");
    }

    @Test
    void pin01S1ProjetReliéGithubIndicateursSur30Jours() {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");

        assertEquals(CollecteActivite.Issue.A_JOUR, collecte.collecterProjet(projet, Instant.now()));

        LocalDate aujourdhui = LocalDate.now(PARIS);
        Api.enTantQue("alice").queryParam("jours", 30).when().get("/api/v1/projects/" + projet + "/activite")
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
        Api.enTantQue("alice").queryParam("jours", 7).when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200).body("compteurs.ticketsOuverts", equalTo(2)).body("compteurs.ticketsFermes", equalTo(1))
                .body("compteurs.versionsPubliees", equalTo(1));
        Api.enTantQue("alice").queryParam("jours", 400).when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(400).body("code", equalTo("PERIODE_INVALIDE"));
    }

    @Test
    void pin02S1EtS2AvancementAvecDenominateurSeulement() {
        depotGithub("org/app");
        UUID avecJalon = projetGithub("org/app");
        collecte.collecterProjet(avecJalon, Instant.now());
        Api.enTantQue("alice").when().get("/api/v1/projects/" + avecJalon + "/activite")
                .then().statusCode(200)
                .body("jalons[0].ticketsFermes", equalTo(27))
                .body("jalons[0].ticketsTotal", equalTo(30))
                .body("jalons[0].avancement.libelle", equalTo("27 sur 30 (90 %)"))
                .body("jalons[0].avancement.pourcentage", equalTo(90));

        StubServer.programmer("/github/repos/org/sans-jalon/issues", Stub.succes("[]"));
        StubServer.programmer("/github/repos/org/sans-jalon/actions/runs", Stub.succes("{\"workflow_runs\":[]}"));
        StubServer.programmer("/github/repos/org/sans-jalon/releases", Stub.succes("[]"));
        StubServer.programmer("/github/repos/org/sans-jalon/milestones", Stub.succes("[]"));
        UUID sansJalon = projetGithub("org/sans-jalon");
        collecte.collecterProjet(sansJalon, Instant.now());
        String corps = Api.enTantQue("alice").when().get("/api/v1/projects/" + sansJalon + "/activite")
                .then().statusCode(200).body("jalons", empty()).extract().asString();
        assertFalse(corps.contains("%") || corps.contains("pourcentage") || corps.contains("avancement"),
                "aucun pourcentage d'avancement sans jalon : " + corps);
    }

    @Test
    void pin03S1AucunIndicateurParPersonne() throws Exception {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        collecte.collecterProjet(projet, Instant.now());

        String corps = Api.enTantQue("alice").queryParam("contributeur", "bob").when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200).body("compteurs.ticketsOuverts", equalTo(3)).extract().asString();
        assertFalse(corps.contains("bob") || corps.contains("carol"), "aucune identité restituée : " + corps);
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/activite/contributeurs/bob").then().statusCode(404);

        // Aucune identité enregistrée, et aucune colonne qui pourrait en porter (revue du schéma outillée).
        assertEquals(0, baseDeTest.count("""
                SELECT (SELECT count(*) FROM activite_compteur c WHERE c::text ~* '(bob|carol)')
                     + (SELECT count(*) FROM activite_jalon j WHERE j::text ~* '(bob|carol)')
                     + (SELECT count(*) FROM activite_collecte a WHERE a::text ~* '(bob|carol)')"""));
        assertEquals(0, baseDeTest.count("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name IN ('activite_compteur', 'activite_jalon', 'activite_collecte')
                  AND column_name ~* '(auteur|author|user|login|assign|review|contribut|personne|person|mail|compte|account|membre|owner|par$|_by$)'"""));
        assertEquals(20, baseDeTest.count("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name IN ('activite_compteur', 'activite_jalon', 'activite_collecte')"""),
                "colonnes revues : toute nouvelle colonne impose de revoir ce test");
        Api.enTantQue("alice").queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("paths.keySet().findAll { it.contains('/activite') }", hasSize(3))
                .body("paths.'/api/v1/projects/{projectId}/activite/collecte'.keySet()", contains("post"))
                .body("paths.'/api/v1/projects/{projectId}/activite'.get.parameters.name", hasItems("jours"))
                .body("paths.'/api/v1/projects/{projectId}/activite'.get.parameters.name", not(hasItems("contributeur")));
    }

    @Test
    void pin04S1SyntheseMarqueeEtFondeeSurLesIndicateurs() {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        String url = "/api/v1/projects/" + projet + "/activite/synthese";

        Api.enTantQue("alice").when().get(url).then().statusCode(200).body("etat", equalTo("SANS_DONNEES"))
                .body("mention", equalTo("produite par l'IA")).body("texte", nullValue());
        assertTrue(syntheseIa.recues().isEmpty(), "aucun appel sans donnée collectée");

        collecte.collecterProjet(projet, Instant.now());
        Api.enTantQue("alice").when().get(url).then().statusCode(200)
                .body("etat", equalTo("DISPONIBLE"))
                .body("mention", equalTo("produite par l'IA"))
                .body("texte", containsString("27 sur 30 (90 %)"))
                .body("periode.jours", equalTo(30))
                .body("indicateurs", hasItems("compteurs.ticketsOuverts", "compteurs.ticketsFermes", "jalons"));
        assertEquals(1, syntheseIa.recues().size());
        assertEquals(3, syntheseIa.recues().get(0).compteurs().ticketsOuverts(), "la synthèse reçoit les indicateurs affichés");

        syntheseIa.configure(SimulatedSyntheseIa.Mode.CHIFFRE_INVENTE);
        Api.enTantQue("alice").when().get(url).then().statusCode(200).body("etat", equalTo("REFUSEE")).body("texte", nullValue())
                .body("mention", equalTo("produite par l'IA"));
        syntheseIa.configure(SimulatedSyntheseIa.Mode.UNAVAILABLE);
        Api.enTantQue("alice").when().get(url).then().statusCode(200).body("etat", equalTo("INDISPONIBLE"));
        syntheseIa.configure(SimulatedSyntheseIa.Mode.BUDGET_EXHAUSTED);
        Api.enTantQue("alice").when().get(url).then().statusCode(200).body("etat", equalTo("PLAFOND_ATTEINT"));
        assertTrue(syntheseIa.recues().isEmpty(), "plafond atteint : aucun appel");
        syntheseIa.configure(SimulatedSyntheseIa.Mode.REFUSED);
        Api.enTantQue("alice").when().get(url).then().statusCode(200).body("etat", equalTo("REFUSEE"));
        // Les indicateurs restent consultables quel que soit l'état de la synthèse.
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(200)
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
        StubServer.programmer("/github/repos/org/app/issues", new Stub(403, "{\"message\":\"API rate limit exceeded\"}",
                Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(reinitialisation.getEpochSecond()))));

        CollecteActivite.Bilan bilan = collecte.collecter(Instant.now());

        assertEquals(new CollecteActivite.Bilan(0, 2, 0, List.of()), bilan, "le second projet GitHub est reporté sans appel");
        assertEquals(1, StubServer.received("/github/").size(), "aucun appel après le quota épuisé");
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200)
                .body("fraicheur.etat", equalTo("REPORTEE_QUOTA"))
                .body("fraicheur.derniereMiseAJour", equalTo(premiere.toString()))
                .body("fraicheur.prochaineCollecte", equalTo(reinitialisation.toString()))
                .body("fraicheur.motif", containsString("quota"))
                .body("compteurs.ticketsOuverts", equalTo(2));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + autre + "/activite")
                .then().statusCode(200).body("fraicheur.etat", equalTo("REPORTEE_QUOTA"))
                .body("fraicheur.derniereMiseAJour", nullValue());

        // Avant la réinitialisation, rien n'est tenté ; après, la collecte reprend.
        assertEquals(new CollecteActivite.Bilan(0, 0, 0, List.of()), collecte.collecter(reinitialisation.minusSeconds(60)));
        assertEquals(1, StubServer.received("/github/").size());
        depotGithub("org/app");
        StubServer.programmer("/github/repos/org/autre/issues", Stub.succes("[]"));
        StubServer.programmer("/github/repos/org/autre/actions/runs", Stub.succes("{\"workflow_runs\":[]}"));
        StubServer.programmer("/github/repos/org/autre/releases", Stub.succes("[]"));
        StubServer.programmer("/github/repos/org/autre/milestones", Stub.succes("[]"));
        assertEquals(new CollecteActivite.Bilan(2, 0, 0, List.of()), collecte.collecter(reinitialisation.plusSeconds(1)));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/activite")
                .then().statusCode(200).body("fraicheur.etat", equalTo("A_JOUR"))
                .body("fraicheur.derniereMiseAJour", equalTo(reinitialisation.plusSeconds(1).toString()));
    }

    @Test
    void echecDeForgeSansJetonDansLeMotifEtCollecteIdempotente() {
        depotGithub("org/app");
        UUID projet = projetGithub("org/app");
        collecte.collecterProjet(projet, Instant.now());
        long lignes = baseDeTest.count("SELECT count(*) FROM activite_compteur WHERE project_id = '" + projet + "'");
        collecte.collecterProjet(projet, Instant.now());
        assertEquals(lignes, baseDeTest.count("SELECT count(*) FROM activite_compteur WHERE project_id = '" + projet + "'"));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/activite").then().body("compteurs.ticketsOuverts", equalTo(3));

        StubServer.programmer("/github/repos/org/app/issues", Stub.status(500));
        assertEquals(CollecteActivite.Issue.ECHEC, collecte.collecterProjet(projet, Instant.now()));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(200)
                .body("fraicheur.etat", equalTo("ECHEC")).body("fraicheur.motif", equalTo("HTTP 500 de localhost"))
                .body("compteurs.ticketsOuverts", equalTo(3));
        // Redirection non suivie : le jeton ne part jamais vers un autre hôte.
        StubServer.programmer("/github/repos/org/app/issues", new Stub(302, "", Map.of("Location", "http://ailleurs.example/")));
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
        Api.enTantQue("jules").when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(404);
        Api.enTantQue("jules").when().get("/api/v1/projects/" + projet + "/activite/synthese").then().statusCode(404);
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + jules
                + "' AND ressource = 'GET /api/v1/projects/" + projet + "/activite' AND motif = 'NON_MEMBRE'"));
        Api.enTantQue("alice").when().get("/api/v1/projects/" + UUID.randomUUID() + "/activite").then().statusCode(404);
        io.restassured.RestAssured.given().when().get("/api/v1/projects/" + projet + "/activite").then().statusCode(401);
    }

    private List<Long> compterSousArgosApp(UUID compte, UUID projet) {
        return base.executer(new ContexteBase.Contexte(compte, null, null), connexion -> {
            try (Statement instruction = connexion.createStatement(); ResultSet resultat = instruction.executeQuery("""
                    SELECT (SELECT count(*) FROM activite_compteur WHERE project_id = '%1$s'),
                           (SELECT count(*) FROM activite_jalon WHERE project_id = '%1$s'),
                           (SELECT count(*) FROM activite_collecte WHERE project_id = '%1$s'),
                           current_user""".formatted(projet))) {
                resultat.next();
                assertEquals("argos_app", resultat.getString(4), "lecture sous argos_app, jamais sous le super-utilisateur");
                return List.of(resultat.getLong(1), resultat.getLong(2), resultat.getLong(3));
            }
        });
    }

    @Test
    void purgeDeLHistoriqueAuDelaDeTreizeMois() {
        UUID projet = projetGithub("org/app");
        LocalDate aujourdhui = LocalDate.of(2026, 11, 16);
        baseDeTest.exec("""
                INSERT INTO activite_compteur(project_id, jour, type, valeur) VALUES
                  ('%1$s', '2025-10-15', 'TICKET_OUVERT', 4),
                  ('%1$s', '2025-10-16', 'TICKET_OUVERT', 5),
                  ('%1$s', '2026-11-10', 'TICKET_FERME', 2)""".formatted(projet));
        baseDeTest.exec("""
                INSERT INTO activite_jalon(project_id, reference, titre, etat, tickets_fermes, tickets_total, releve_le) VALUES
                  ('%1$s', '1', 'ancien', 'FERME', 1, 1, '2025-09-01T00:00:00Z'),
                  ('%1$s', '2', 'récent', 'OUVERT', 1, 2, '2026-11-15T00:00:00Z')""".formatted(projet));
        baseDeTest.exec("""
                INSERT INTO activite_collecte(project_id, etat, derniere_mise_a_jour, prochaine_collecte, donnees_depuis, tentee_le)
                VALUES ('%s', 'A_JOUR', now(), now(), '2025-09-01', now())""".formatted(projet));

        int supprimees = purge.purger(aujourdhui);

        assertEquals(2, supprimees, "un compteur du 15 octobre 2025 et un jalon relevé en septembre 2025");
        assertEquals("2025-10-16,2026-11-10", baseDeTest.string(
                "SELECT string_agg(jour::text, ',' ORDER BY jour) FROM activite_compteur WHERE project_id = '" + projet + "'"),
                "le 16 octobre 2025 est à 13 mois exactement : conservé");
        assertEquals("récent", baseDeTest.string("SELECT string_agg(titre, ',') FROM activite_jalon WHERE project_id = '" + projet + "'"));
        assertEquals("2025-10-16", baseDeTest.string(
                "SELECT donnees_depuis::text FROM activite_collecte WHERE project_id = '" + projet + "'"));
        assertEquals(0, purge.purger(aujourdhui), "rejouée, la purge ne supprime rien de plus");
    }
}
