package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/**
 * Lecture de l'activité d'un dépôt par {@link RepositoryActivity} (PIN-01, PIN-02, PIN-05) face aux forges simulées
 * par un serveur HTTP local, sans Quarkus : pagination bornée, arrêt des versions anciennes, nombre maximal de jalons,
 * ordre et contrat des requêtes (lecture seule, aucun jeton non configuré), conversion des réponses, dates de repli et
 * messages d'erreur. Aucune forge réelle n'est appelée.
 */
class RepositoryActivityTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T10:00:00Z");
    private static final Clock HORLOGE = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    /** Délai de connexion et d'attente des en-têtes, large : aucun test n'attend son expiration. */
    private static final Duration DELAI = Duration.ofSeconds(5);
    private static final long TAILLE_MAX = 5_242_880;
    private static final int PAGES_MAX = 10;
    private static final int JALONS_MAX = 50;
    private static final int PAGE_PLEINE = RepositoryActivity.PAR_PAGE;
    private static final int FENETRE_JOURS = 30;

    // Forges simulées sous des préfixes dédiés, avec leurs propres jetons.
    private static final Instant DEPUIS_FORGES = Instant.parse("2026-11-01T00:00:00Z");
    private static final int PAGES_BORNEES = 2;
    private static final int JALONS_BORNES = 1;
    private static final String JETON_GITHUB = "jeton-gh-simule";
    private static final String JETON_GITLAB = "jeton-gl-simule";
    private static final String DEPOT_GH = "/forge-gh/repos/org/depot";
    private static final String PROJET_GL = "/forge-gl/api/v4/projects/42";
    private static final RepositoryRef DEPOT_GITHUB = new RepositoryRef("GITHUB", "org/depot", "main", null, "sbom/bom.json");
    private static final RepositoryRef DEPOT_GITLAB = new RepositoryRef("GITLAB", "42", "main", "sbom", "bom.json");

    // Caractérisation et contrat, sous les préfixes de la configuration de test, sans jeton.
    private static final Instant DEPUIS = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DEPOT_GITHUB_CONTRAT = "/github/repos/org/depot";
    private static final String PROJET_GITLAB_CONTRAT = "/gitlab/api/v4/projects/groupe%2Fdepot";

    private static ServeurHttpSimule serveur;

    @BeforeAll
    static void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
    }

    @AfterAll
    static void arreter() {
        serveur.close();
    }

    @BeforeEach
    void reinitialiser() {
        serveur.reset();
    }

    // ------------------------------------------------------------------ activité GitHub, forges simulées

    @Test
    void activiteGithubBorneeParLeNombreDePagesEtDeJalons() {
        serveur.programmer(DEPOT_GH + "/issues", Stub.succes(repete(PAGE_PLEINE,
                "{\"created_at\": \"2026-11-02T00:00:00Z\", \"closed_at\": null}")));
        serveur.programmer(DEPOT_GH + "/actions/runs", Stub.succes("""
                {"workflow_runs": [{"created_at": "2026-11-03T00:00:00Z", "conclusion": "success"},
                  {"created_at": "2026-11-03T01:00:00Z", "conclusion": "timed_out"},
                  {"created_at": "2026-11-03T02:00:00Z", "conclusion": null},
                  {"created_at": "2026-11-03T03:00:00Z", "conclusion": "cancelled"},
                  {"created_at": "2026-10-01T00:00:00Z", "conclusion": "success"}]}
                """));
        serveur.programmer(DEPOT_GH + "/releases", Stub.succes("""
                [{"published_at": "2026-11-04T00:00:00Z", "draft": false}, {"published_at": "2026-11-05T00:00:00Z", "draft": true},
                 {"published_at": "2026-10-04T00:00:00Z", "draft": false}, {"published_at": null, "draft": false}]
                """));
        serveur.programmer(DEPOT_GH + "/milestones", Stub.succes("""
                [{"number": null, "title": "Sans numéro", "state": "open"},
                 {"number": 7, "title": "Version 7", "state": "open", "due_on": "2026-12-01T00:00:00Z", "open_issues": 3,
                  "closed_issues": -1},
                 {"number": 8, "title": "Version 8", "state": "open"},
                 {"number": 6, "title": "Ancienne", "state": "closed", "closed_at": "2026-01-01T00:00:00Z"}]
                """));

        ActiviteDepot activite = forgesSimulees().lire(DEPOT_GITHUB, DEPUIS_FORGES);

        assertFalse(activite.complet(), "pages et jalons bornés");
        assertEquals(PAGES_BORNEES * PAGE_PLEINE, activite.tickets().size());
        assertEquals(List.of(ActiviteDepot.Issue.REUSSI, ActiviteDepot.Issue.ECHOUE, ActiviteDepot.Issue.AUTRE,
                ActiviteDepot.Issue.AUTRE), activite.pipelines().stream().map(ActiviteDepot.Pipeline::issue).toList());
        assertEquals(List.of(new ActiviteDepot.Version(Instant.parse("2026-11-04T00:00:00Z"))), activite.versions());
        assertEquals(List.of(new ActiviteDepot.Jalon("7", "Version 7", true, LocalDate.parse("2026-12-01"), 0, 3)), activite.jalons());
    }

    @Test
    void activiteGithubRefuseUnDepotInvalideEtSignaleLesErreursDeForge() {
        RepositoryActivity lecteur = forgesSimulees();
        assertThrows(ScmException.class, () -> lecteur.lire(new RepositoryRef("GITHUB", "pas-de-barre", "main", null, null),
                DEPUIS_FORGES));
        assertThrows(ScmException.class, () -> lecteur.lire(new RepositoryRef(null, "org/depot", "main", null, null), DEPUIS_FORGES));

        serveur.programmer(DEPOT_GH + "/issues", Stub.status(500));
        assertTrue(assertThrows(ScmException.class, () -> lecteur.lire(DEPOT_GITHUB, DEPUIS_FORGES)).getMessage()
                .startsWith("HTTP 500"));

        serveur.programmer(DEPOT_GH + "/issues", Stub.succes("{illisible"));
        assertTrue(assertThrows(ScmException.class, () -> lecteur.lire(DEPOT_GITHUB, DEPUIS_FORGES)).getMessage()
                .startsWith("réponse illisible"));

        serveur.programmer(DEPOT_GH + "/issues", new Stub(429, "", Map.of("retry-after", "60")));
        assertThrows(QuotaEpuise.class, () -> lecteur.lire(DEPOT_GITHUB, DEPUIS_FORGES));
    }

    // ------------------------------------------------------------------ activité GitLab, forges simulées

    @Test
    void activiteGitlabAvecCompteursDeJalonsEtDatesDeRepli() {
        serveur.programmer(PROJET_GL + "/issues",
                Stub.succes("[{\"created_at\": \"2026-11-02T00:00:00Z\", \"closed_at\": \"illisible\"}]"));
        serveur.programmer(PROJET_GL + "/merge_requests",
                Stub.succes("[{\"created_at\": \"2026-11-02T00:00:00Z\", \"merged_at\": null}]"));
        serveur.programmer(PROJET_GL + "/pipelines", Stub.succes("""
                [{"created_at": "2026-11-03T00:00:00Z", "status": "success"}, {"created_at": "2026-11-03T00:00:00Z", "status": "failed"},
                 {"created_at": "2026-11-03T00:00:00Z", "status": "running"}, {"created_at": "2026-11-03T00:00:00Z", "status": null}]
                """));
        serveur.programmer(PROJET_GL + "/releases", Stub.succes("""
                [{"released_at": null, "created_at": "2026-11-06T00:00:00Z"}, {"released_at": "2026-10-06T00:00:00Z"}]
                """));
        serveur.programmer(PROJET_GL + "/milestones", Stub.succes("""
                [{"id": 11, "title": "Sprint 11", "state": "active", "due_date": "2026-12-15"},
                 {"id": 12, "title": null, "state": "active"},
                 {"id": 10, "title": "Sprint 10", "state": "closed", "updated_at": "2026-01-01T00:00:00Z"}]
                """));
        serveur.programmer(PROJET_GL + "/issues_statistics", Stub.succes("{\"statistics\": {\"counts\": {\"all\": 4, \"closed\": 9}}}"));

        ActiviteDepot activite = forgesSimulees().lire(DEPOT_GITLAB, DEPUIS_FORGES);

        assertTrue(activite.complet());
        assertEquals(List.of(new ActiviteDepot.Ticket(Instant.parse("2026-11-02T00:00:00Z"), null)), activite.tickets());
        assertEquals(List.of(ActiviteDepot.Issue.REUSSI, ActiviteDepot.Issue.ECHOUE, ActiviteDepot.Issue.AUTRE, ActiviteDepot.Issue.AUTRE),
                activite.pipelines().stream().map(ActiviteDepot.Pipeline::issue).toList());
        assertEquals(List.of(new ActiviteDepot.Version(Instant.parse("2026-11-06T00:00:00Z"))), activite.versions());
        assertEquals(List.of(new ActiviteDepot.Jalon("11", "Sprint 11", true, LocalDate.parse("2026-12-15"), 4, 4)), activite.jalons(),
                "tickets fermés bornés au total");
    }

    @Test
    void jalonGitlabSansStatistiquesCompteZeroEtDepotVideRefuse() {
        for (String chemin : List.of("/issues", "/merge_requests", "/pipelines", "/releases")) {
            serveur.programmer(PROJET_GL + chemin, Stub.succes("[]"));
        }
        serveur.programmer(PROJET_GL + "/milestones", Stub.succes("[{\"id\": 11, \"title\": \"Sprint 11\", \"state\": \"active\"}]"));
        serveur.programmer(PROJET_GL + "/issues_statistics", Stub.succes("{}"));

        ActiviteDepot activite = forgesSimulees().lire(DEPOT_GITLAB, DEPUIS_FORGES);

        assertEquals(List.of(new ActiviteDepot.Jalon("11", "Sprint 11", true, null, 0, 0)), activite.jalons());
        assertThrows(ScmException.class, () -> forgesSimulees().lire(new RepositoryRef("GITLAB", " ", "main", null, null),
                DEPUIS_FORGES));
    }

    // ------------------------------------------------------------------ caractérisation GitHub

    @Test
    void githubConversionEtOrdreDesRequetes() {
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/issues", Stub.succes("""
                [{"created_at":"2026-01-05T10:00:00Z","closed_at":"2026-01-06T10:00:00Z"},
                 {"created_at":"2026-01-07T10:00:00Z","closed_at":null,"pull_request":{"merged_at":"2026-01-08T10:00:00Z"}},
                 {"created_at":"pas une date","closed_at":""}]"""));
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/actions/runs", Stub.succes("""
                {"workflow_runs":[{"created_at":"2026-01-02T00:00:00Z","conclusion":"success"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":"timed_out"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":"STARTUP_FAILURE"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":"cancelled"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":null},
                  {"created_at":"2025-12-31T23:59:59Z","conclusion":"success"},
                  {"created_at":null,"conclusion":"success"}]}"""));
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/releases", Stub.succes("""
                [{"published_at":"2026-01-03T00:00:00Z","draft":false},{"published_at":"2026-01-04T00:00:00Z","draft":true},
                 {"published_at":"2025-06-01T00:00:00Z","draft":false},{"published_at":null,"draft":false}]"""));
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/milestones", Stub.succes("""
                [{"number":7,"title":"  v1.0\\u0007 ","state":"OPEN","due_on":"2026-12-01T08:00:00Z","open_issues":3,"closed_issues":27},
                 {"number":8,"title":"","state":"closed","closed_at":"2026-01-10T00:00:00Z","open_issues":-1,"closed_issues":-4},
                 {"number":9,"title":"vieux","state":"closed","closed_at":"2025-01-10T00:00:00Z"},
                 {"number":10,"title":"ferme sans date","state":"closed"},
                 {"number":null,"title":"sans numero","state":"open"}]"""));

        ActiviteDepot lecture = github(activite(PAGES_MAX, JALONS_MAX));

        assertTrue(lecture.complet());
        assertEquals(List.of(new ActiviteDepot.Ticket(Instant.parse("2026-01-05T10:00:00Z"), Instant.parse("2026-01-06T10:00:00Z")),
                new ActiviteDepot.Ticket(null, null)), lecture.tickets());
        assertEquals(List.of(new ActiviteDepot.Fusion(Instant.parse("2026-01-07T10:00:00Z"), Instant.parse("2026-01-08T10:00:00Z"))),
                lecture.fusions());
        assertEquals(List.of(ActiviteDepot.Issue.REUSSI, ActiviteDepot.Issue.ECHOUE, ActiviteDepot.Issue.ECHOUE,
                ActiviteDepot.Issue.AUTRE, ActiviteDepot.Issue.AUTRE),
                lecture.pipelines().stream().map(ActiviteDepot.Pipeline::issue).toList());
        assertEquals(List.of(new ActiviteDepot.Version(Instant.parse("2026-01-03T00:00:00Z"))), lecture.versions());
        assertEquals(List.of(new ActiviteDepot.Jalon("7", "v1.0", true, LocalDate.of(2026, 12, 1), 27, 30),
                new ActiviteDepot.Jalon("8", "(sans titre)", false, null, 0, 0)), lecture.jalons());
        assertEquals(List.of(DEPOT_GITHUB_CONTRAT + "/issues?state=all&since=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                DEPOT_GITHUB_CONTRAT + "/actions/runs?created=%3E%3D2026-01-01&per_page=100&page=1",
                DEPOT_GITHUB_CONTRAT + "/releases?per_page=100&page=1",
                DEPOT_GITHUB_CONTRAT + "/milestones?state=all&per_page=100&page=1"), requetes("/github/"));
    }

    @Test
    void githubTicketsTronquesAuNombreMaximalDePages() {
        githubVide();
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/issues", Stub.succes(repete(PAGE_PLEINE, "{\"created_at\":\"2026-01-05T10:00:00Z\"}")));

        ActiviteDepot lecture = github(activite(PAGES_BORNEES, JALONS_MAX));

        assertFalse(lecture.complet());
        assertEquals(PAGES_BORNEES * PAGE_PLEINE, lecture.tickets().size());
        assertEquals(PAGES_BORNEES, requetes(DEPOT_GITHUB_CONTRAT + "/issues").size());
    }

    @Test
    void githubExecutionsTronqueesAuNombreMaximalDePages() {
        githubVide();
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/actions/runs", Stub.succes("{\"workflow_runs\":"
                + repete(PAGE_PLEINE, "{\"created_at\":\"2026-01-05T10:00:00Z\",\"conclusion\":\"success\"}") + "}"));

        ActiviteDepot lecture = github(activite(PAGES_BORNEES, JALONS_MAX));

        assertFalse(lecture.complet());
        assertEquals(PAGES_BORNEES * PAGE_PLEINE, lecture.pipelines().size());
        assertEquals(PAGES_BORNEES, requetes(DEPOT_GITHUB_CONTRAT + "/actions/runs").size());
    }

    @Test
    void githubExecutionsSansListeEtPagePleineSuivie() {
        githubVide();
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/actions/runs", Stub.succes("{\"workflow_runs\":"
                        + repete(PAGE_PLEINE, "{\"created_at\":\"2026-01-05T10:00:00Z\",\"conclusion\":\"success\"}") + "}"),
                Stub.succes("{}"));

        ActiviteDepot lecture = github(activite(3, JALONS_MAX));

        assertTrue(lecture.complet());
        assertEquals(PAGE_PLEINE, lecture.pipelines().size());
        assertEquals(2, requetes(DEPOT_GITHUB_CONTRAT + "/actions/runs").size());
    }

    @Test
    void githubVersionsArreteesDesQuUnePagePleineEstAncienne() {
        githubVide();
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/releases",
                Stub.succes(repete(PAGE_PLEINE, "{\"published_at\":\"2025-01-05T10:00:00Z\",\"draft\":false}")));

        ActiviteDepot lecture = github(activite(5, JALONS_MAX));

        assertTrue(lecture.complet(), "l'arrêt sur une page ancienne n'est pas une troncature");
        assertTrue(lecture.versions().isEmpty());
        assertEquals(1, requetes(DEPOT_GITHUB_CONTRAT + "/releases").size());
    }

    @Test
    void githubJalonsBornes() {
        githubVide();
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/milestones", Stub.succes("""
                [{"number":1,"title":"a","state":"open"},{"number":2,"title":"b","state":"open"}]"""));

        ActiviteDepot lecture = github(activite(5, JALONS_BORNES));

        assertFalse(lecture.complet());
        assertEquals(List.of("1"), lecture.jalons().stream().map(ActiviteDepot.Jalon::reference).toList());
    }

    // ------------------------------------------------------------------ caractérisation GitLab

    @Test
    void gitlabConversionJalonsBornesEtStatistiques() {
        gitlabVide();
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/releases", Stub.succes("""
                [{"released_at":null,"created_at":"2026-01-04T00:00:00Z"},
                 {"released_at":"2025-01-04T00:00:00Z","created_at":"2026-01-04T00:00:00Z"}]"""));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/pipelines", Stub.succes("""
                [{"created_at":"2026-01-02T00:00:00Z","status":"SUCCESS"},{"created_at":"2026-01-02T00:00:00Z","status":"failed"},
                 {"created_at":"2026-01-02T00:00:00Z","status":null},{"created_at":"2025-01-02T00:00:00Z","status":"failed"}]"""));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/milestones", Stub.succes("""
                [{"id":11,"title":"Sprint 4","state":"active","due_date":"2026-12-15"},
                 {"id":12,"title":null,"state":"active"},
                 {"id":13,"title":"Ferme","state":"closed","updated_at":"2026-01-09T00:00:00Z","due_date":"invalide"},
                 {"id":14,"title":"Encore","state":"active"}]"""));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/issues_statistics?milestone=Sprint%204",
                Stub.succes("{\"statistics\":{\"counts\":{\"all\":3,\"closed\":9}}}"));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/issues_statistics?milestone=Ferme", Stub.succes("{}"));

        ActiviteDepot lecture = gitlab(activite(5, 2));

        assertFalse(lecture.complet(), "troisième jalon retenu au-delà de la borne");
        assertEquals(List.of(new ActiviteDepot.Version(Instant.parse("2026-01-04T00:00:00Z"))), lecture.versions());
        assertEquals(List.of(ActiviteDepot.Issue.REUSSI, ActiviteDepot.Issue.ECHOUE, ActiviteDepot.Issue.AUTRE),
                lecture.pipelines().stream().map(ActiviteDepot.Pipeline::issue).toList());
        assertEquals(List.of(new ActiviteDepot.Jalon("11", "Sprint 4", true, LocalDate.of(2026, 12, 15), 3, 3),
                new ActiviteDepot.Jalon("13", "Ferme", false, null, 0, 0)), lecture.jalons());
        assertEquals(List.of(PROJET_GITLAB_CONTRAT + "/issues?updated_after=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                PROJET_GITLAB_CONTRAT + "/merge_requests?updated_after=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                PROJET_GITLAB_CONTRAT + "/pipelines?updated_after=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                PROJET_GITLAB_CONTRAT + "/releases?per_page=100&page=1",
                PROJET_GITLAB_CONTRAT + "/milestones?per_page=100&page=1",
                PROJET_GITLAB_CONTRAT + "/issues_statistics?milestone=Sprint%204",
                PROJET_GITLAB_CONTRAT + "/issues_statistics?milestone=Ferme"), requetes("/gitlab/"));
    }

    @Test
    void gitlabFusionsTronqueesAuNombreMaximalDePages() {
        gitlabVide();
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/merge_requests",
                Stub.succes(repete(PAGE_PLEINE, "{\"created_at\":\"2026-01-05T10:00:00Z\"}")));

        ActiviteDepot lecture = gitlab(activite(1, JALONS_MAX));

        assertFalse(lecture.complet());
        assertEquals(PAGE_PLEINE, lecture.fusions().size());
    }

    @Test
    void erreursDeForgeEtDepotsInvalides() {
        RepositoryActivity activite = activite(5, 5);
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/issues", Stub.status(500));
        assertEquals("HTTP 500 de localhost", assertThrows(ScmException.class, () -> github(activite)).getMessage());

        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/issues", Stub.succes("{pas du json"));
        assertEquals("réponse illisible de localhost", assertThrows(ScmException.class, () -> github(activite)).getMessage());

        assertEquals("dépôt GitHub invalide : attendu owner/repo", assertThrows(ScmException.class,
                () -> activite.lire(new RepositoryRef("GITHUB", "sans-barre", "main", null, null), DEPUIS)).getMessage());
        assertEquals("dépôt GitLab invalide : identifiant ou chemin attendu", assertThrows(ScmException.class,
                () -> activite.lire(new RepositoryRef("gitlab", " ", "main", null, null), DEPUIS)).getMessage());
        assertEquals("forge non prise en charge : bitbucket", assertThrows(ScmException.class,
                () -> activite.lire(new RepositoryRef("bitbucket", "a/b", "main", null, null), DEPUIS)).getMessage());
    }

    // ------------------------------------------------------------------ contrat de lecture des forges

    /**
     * Contrat GitHub (PIN-01) : une page par liste, lecture seule (GET), filtres de date dans l'adresse, média JSON de
     * l'API, et aucun jeton envoyé quand aucun n'est configuré. Les champs d'identité servis (bob, carol) sont ignorés.
     */
    @Test
    void contratGithubLectureSeuleSansRedirectionNiJeton() {
        String depot = "/github/repos/org/app";
        serveur.programmer(depot + "/issues", Stub.succes("""
                [{"number":1,"title":"Bug","user":{"login":"bob"},"assignee":{"login":"carol"},"created_at":"%s","closed_at":null},
                 {"number":5,"title":"PR","user":{"login":"bob"},"created_at":"%s","closed_at":"%s",
                  "pull_request":{"url":"x","merged_at":"%s"}}]""".formatted(ilYa(5), ilYa(6), ilYa(4), ilYa(4))));
        serveur.programmer(depot + "/actions/runs", Stub.succes("""
                {"total_count":2,"workflow_runs":[
                  {"created_at":"%s","status":"completed","conclusion":"success","actor":{"login":"bob"}},
                  {"created_at":"%s","status":"in_progress","conclusion":null}]}""".formatted(ilYa(2), ilYa(0))));
        serveur.programmer(depot + "/releases", Stub.succes("""
                [{"tag_name":"v0.9","published_at":"%s","draft":false,"author":{"login":"bob"}}]""".formatted(ilYa(6))));
        serveur.programmer(depot + "/milestones", Stub.succes("""
                [{"number":7,"title":"v1.0","state":"open","due_on":"2026-12-01T08:00:00Z","open_issues":3,
                  "closed_issues":27,"creator":{"login":"bob"}}]"""));

        activite(PAGES_MAX, JALONS_MAX).lire(new RepositoryRef("GITHUB", "org/app", "main", null, null), debutFenetre());

        List<ServeurHttpSimule.Received> requetes = serveur.received("/github/");
        assertEquals(4, requetes.size(),
                "une page par liste : " + requetes.stream().map(ServeurHttpSimule.Received::pathAndQuery).toList());
        assertTrue(requetes.stream().allMatch(requete -> "GET".equals(requete.method())), "lecture seule : GET uniquement");
        assertTrue(requetes.stream().anyMatch(requete -> requete.pathAndQuery().startsWith(depot + "/issues?state=all&since=")));
        assertTrue(requetes.stream().anyMatch(requete -> requete.pathAndQuery().startsWith(depot + "/actions/runs?created=%3E%3D")));
        assertTrue(requetes.stream().allMatch(requete -> requete.headers().keySet().stream()
                        .noneMatch(entete -> entete.equalsIgnoreCase("Authorization"))),
                "aucun jeton configuré, aucun envoyé");
        assertEquals(List.of("application/vnd.github+json"), requetes.get(0).headers().get("Accept"));
    }

    /**
     * Contrat GitLab (PIN-01, PIN-02) : tickets, fusions, pipelines de la fenêtre, version publiée, jalon actif avec ses
     * statistiques (27 tickets fermés sur 30) et jalon ancien écarté ; lecture seule, titre du jalon encodé dans
     * l'adresse des statistiques. Les champs d'identité servis sont ignorés.
     */
    @Test
    void contratGitlab() {
        String projet = "/gitlab/api/v4/projects/groupe%2Fapp";
        serveur.programmer(projet + "/issues", Stub.succes("""
                [{"iid":1,"author":{"username":"bob"},"created_at":"%s","closed_at":"%s"},
                 {"iid":2,"assignees":[{"username":"carol"}],"created_at":"%s","closed_at":null}]""".formatted(ilYa(9), ilYa(2), ilYa(4))));
        serveur.programmer(projet + "/merge_requests", Stub.succes("""
                [{"iid":3,"author":{"username":"bob"},"created_at":"%s","merged_at":"%s","merged_by":{"username":"carol"}}]"""
                .formatted(ilYa(6), ilYa(5))));
        serveur.programmer(projet + "/pipelines", Stub.succes("""
                [{"id":1,"status":"success","created_at":"%s"},{"id":2,"status":"failed","created_at":"%s"},
                 {"id":3,"status":"canceled","created_at":"%s"},{"id":4,"status":"success","created_at":"%s"}]"""
                .formatted(ilYa(1), ilYa(2), ilYa(3), ilYa(45))));
        serveur.programmer(projet + "/releases", Stub.succes("""
                [{"tag_name":"v2","released_at":"%s","author":{"username":"bob"}}]""".formatted(ilYa(3))));
        serveur.programmer(projet + "/milestones", Stub.succes("""
                [{"id":11,"iid":1,"title":"Sprint 4","state":"active","due_date":"2026-12-15","updated_at":"%s"},
                 {"id":12,"iid":2,"title":"Ancien","state":"closed","updated_at":"%s"}]""".formatted(ilYa(1), ilYa(100))));
        serveur.programmer(projet + "/issues_statistics", Stub.succes("""
                {"statistics":{"counts":{"all":30,"closed":27,"opened":3}}}"""));

        ActiviteDepot lecture = activite(PAGES_MAX, JALONS_MAX)
                .lire(new RepositoryRef("GITLAB", "groupe/app", "main", null, null), debutFenetre());

        assertEquals(List.of(new ActiviteDepot.Ticket(jour(9), jour(2)), new ActiviteDepot.Ticket(jour(4), null)), lecture.tickets());
        assertEquals(List.of(new ActiviteDepot.Fusion(jour(6), jour(5))), lecture.fusions());
        assertEquals(List.of(ActiviteDepot.Issue.REUSSI, ActiviteDepot.Issue.ECHOUE, ActiviteDepot.Issue.AUTRE),
                lecture.pipelines().stream().map(ActiviteDepot.Pipeline::issue).toList());
        assertEquals(List.of(new ActiviteDepot.Version(jour(3))), lecture.versions());
        assertEquals(List.of(new ActiviteDepot.Jalon("11", "Sprint 4", true, LocalDate.of(2026, 12, 15), 27, 30)), lecture.jalons());
        assertTrue(serveur.received("/gitlab/").stream().allMatch(requete -> "GET".equals(requete.method())));
        assertEquals(1, serveur.received(projet + "/issues_statistics?milestone=Sprint%204").size(),
                "le titre du jalon est encodé dans l'adresse");
    }

    private static RepositoryActivity forgesSimulees() {
        ParametresForges forges = new ParametresForges(DELAI, serveur.base() + "/forge-gl",
                serveur.base() + "/forge-gh", Optional.of(JETON_GITLAB), Optional.of(JETON_GITHUB));
        return new RepositoryActivity(forges, PAGES_BORNEES, JALONS_BORNES, TAILLE_MAX, HORLOGE);
    }

    private static RepositoryActivity activite(int pagesMax, int jalonsMax) {
        ParametresForges forges = new ParametresForges(DELAI, serveur.base() + "/gitlab",
                serveur.base() + "/github", Optional.empty(), Optional.empty());
        return new RepositoryActivity(forges, pagesMax, jalonsMax, TAILLE_MAX, HORLOGE);
    }

    private static ActiviteDepot github(RepositoryActivity activite) {
        return activite.lire(new RepositoryRef("GITHUB", "org/depot", "main", null, null), DEPUIS);
    }

    private static ActiviteDepot gitlab(RepositoryActivity activite) {
        return activite.lire(new RepositoryRef("GITLAB", "groupe/depot", "main", null, null), DEPUIS);
    }

    private static void githubVide() {
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/issues", Stub.succes("[]"));
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/actions/runs", Stub.succes("{\"workflow_runs\":[]}"));
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/releases", Stub.succes("[]"));
        serveur.programmer(DEPOT_GITHUB_CONTRAT + "/milestones", Stub.succes("[]"));
    }

    private static void gitlabVide() {
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/issues", Stub.succes("[]"));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/merge_requests", Stub.succes("[]"));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/pipelines", Stub.succes("[]"));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/releases", Stub.succes("[]"));
        serveur.programmer(PROJET_GITLAB_CONTRAT + "/milestones", Stub.succes("[]"));
    }

    private static List<String> requetes(String prefixe) {
        return serveur.received(prefixe).stream().map(ServeurHttpSimule.Received::pathAndQuery).toList();
    }

    private static String repete(int repetitions, String element) {
        return IntStream.range(0, repetitions).mapToObj(rang -> element).collect(Collectors.joining(",", "[", "]"));
    }

    /** Début de la fenêtre de collecte : trente jours avant l'instant de référence. */
    private static Instant debutFenetre() {
        return MAINTENANT.minus(FENETRE_JOURS, ChronoUnit.DAYS);
    }

    private static Instant jour(int joursAvant) {
        return MAINTENANT.minus(joursAvant, ChronoUnit.DAYS);
    }

    private static String ilYa(int joursAvant) {
        return jour(joursAvant).toString();
    }
}
