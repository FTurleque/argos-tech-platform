package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.scm.ActiviteDepot;
import io.argos.scm.ParametresForges;
import io.argos.scm.RepositoryActivity;
import io.argos.scm.RepositoryRef;
import io.argos.scm.ScmException;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Caractérisation de {@link RepositoryActivity} (PIN-01, PIN-05) : pagination bornée, arrêt des versions
 * anciennes, nombre maximal de jalons, ordre des requêtes, conversion des réponses et messages d'erreur.
 * Forges simulées par le serveur HTTP local ; aucune forge réelle n'est appelée.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ActiviteDepotCaracterisationTest {

    private static final Instant DEPUIS = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DEPOT_GITHUB = "/github/repos/org/depot";
    private static final String PROJET_GITLAB = "/gitlab/api/v4/projects/groupe%2Fdepot";

    @BeforeEach
    void reset() {
        StubServer.reset();
    }

    private static RepositoryActivity activite(int pagesMax, int jalonsMax) {
        ParametresForges forges = new ParametresForges(Duration.ofSeconds(2), StubServer.base() + "/gitlab",
                StubServer.base() + "/github", Optional.empty(), Optional.empty());
        return new RepositoryActivity(forges, pagesMax, jalonsMax, 5_242_880, Clock.systemUTC());
    }

    private static ActiviteDepot github(RepositoryActivity activite) {
        return activite.lire(new RepositoryRef("GITHUB", "org/depot", "main", null, null), DEPUIS);
    }

    private static ActiviteDepot gitlab(RepositoryActivity activite) {
        return activite.lire(new RepositoryRef("GITLAB", "groupe/depot", "main", null, null), DEPUIS);
    }

    private static String repete(int repetitions, String element) {
        return IntStream.range(0, repetitions).mapToObj(rang -> element).collect(Collectors.joining(",", "[", "]"));
    }

    private static void githubVide() {
        StubServer.programmer(DEPOT_GITHUB + "/issues", Stub.succes("[]"));
        StubServer.programmer(DEPOT_GITHUB + "/actions/runs", Stub.succes("{\"workflow_runs\":[]}"));
        StubServer.programmer(DEPOT_GITHUB + "/releases", Stub.succes("[]"));
        StubServer.programmer(DEPOT_GITHUB + "/milestones", Stub.succes("[]"));
    }

    private static void gitlabVide() {
        StubServer.programmer(PROJET_GITLAB + "/issues", Stub.succes("[]"));
        StubServer.programmer(PROJET_GITLAB + "/merge_requests", Stub.succes("[]"));
        StubServer.programmer(PROJET_GITLAB + "/pipelines", Stub.succes("[]"));
        StubServer.programmer(PROJET_GITLAB + "/releases", Stub.succes("[]"));
        StubServer.programmer(PROJET_GITLAB + "/milestones", Stub.succes("[]"));
    }

    private static List<String> requetes(String prefixe) {
        return StubServer.received(prefixe).stream().map(StubServer.Received::pathAndQuery).toList();
    }

    @Test
    void githubConversionEtOrdreDesRequetes() {
        StubServer.programmer(DEPOT_GITHUB + "/issues", Stub.succes("""
                [{"created_at":"2026-01-05T10:00:00Z","closed_at":"2026-01-06T10:00:00Z"},
                 {"created_at":"2026-01-07T10:00:00Z","closed_at":null,"pull_request":{"merged_at":"2026-01-08T10:00:00Z"}},
                 {"created_at":"pas une date","closed_at":""}]"""));
        StubServer.programmer(DEPOT_GITHUB + "/actions/runs", Stub.succes("""
                {"workflow_runs":[{"created_at":"2026-01-02T00:00:00Z","conclusion":"success"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":"timed_out"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":"STARTUP_FAILURE"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":"cancelled"},
                  {"created_at":"2026-01-02T00:00:00Z","conclusion":null},
                  {"created_at":"2025-12-31T23:59:59Z","conclusion":"success"},
                  {"created_at":null,"conclusion":"success"}]}"""));
        StubServer.programmer(DEPOT_GITHUB + "/releases", Stub.succes("""
                [{"published_at":"2026-01-03T00:00:00Z","draft":false},{"published_at":"2026-01-04T00:00:00Z","draft":true},
                 {"published_at":"2025-06-01T00:00:00Z","draft":false},{"published_at":null,"draft":false}]"""));
        StubServer.programmer(DEPOT_GITHUB + "/milestones", Stub.succes("""
                [{"number":7,"title":"  v1.0\\u0007 ","state":"OPEN","due_on":"2026-12-01T08:00:00Z","open_issues":3,"closed_issues":27},
                 {"number":8,"title":"","state":"closed","closed_at":"2026-01-10T00:00:00Z","open_issues":-1,"closed_issues":-4},
                 {"number":9,"title":"vieux","state":"closed","closed_at":"2025-01-10T00:00:00Z"},
                 {"number":10,"title":"ferme sans date","state":"closed"},
                 {"number":null,"title":"sans numero","state":"open"}]"""));

        ActiviteDepot lecture = github(activite(10, 50));

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
        assertEquals(List.of(DEPOT_GITHUB + "/issues?state=all&since=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                DEPOT_GITHUB + "/actions/runs?created=%3E%3D2026-01-01&per_page=100&page=1",
                DEPOT_GITHUB + "/releases?per_page=100&page=1",
                DEPOT_GITHUB + "/milestones?state=all&per_page=100&page=1"), requetes("/github/"));
    }

    @Test
    void githubTicketsTronquesAuNombreMaximalDePages() {
        githubVide();
        StubServer.programmer(DEPOT_GITHUB + "/issues", Stub.succes(repete(100, "{\"created_at\":\"2026-01-05T10:00:00Z\"}")));

        ActiviteDepot lecture = github(activite(2, 50));

        assertFalse(lecture.complet());
        assertEquals(200, lecture.tickets().size());
        assertEquals(2, requetes(DEPOT_GITHUB + "/issues").size());
    }

    @Test
    void githubExecutionsTronqueesAuNombreMaximalDePages() {
        githubVide();
        StubServer.programmer(DEPOT_GITHUB + "/actions/runs",
                Stub.succes("{\"workflow_runs\":"
                        + repete(100, "{\"created_at\":\"2026-01-05T10:00:00Z\",\"conclusion\":\"success\"}") + "}"));

        ActiviteDepot lecture = github(activite(2, 50));

        assertFalse(lecture.complet());
        assertEquals(200, lecture.pipelines().size());
        assertEquals(2, requetes(DEPOT_GITHUB + "/actions/runs").size());
    }

    @Test
    void githubExecutionsSansListeEtPagePleineSuivie() {
        githubVide();
        StubServer.programmer(DEPOT_GITHUB + "/actions/runs",
                Stub.succes("{\"workflow_runs\":"
                        + repete(100, "{\"created_at\":\"2026-01-05T10:00:00Z\",\"conclusion\":\"success\"}") + "}"),
                Stub.succes("{}"));

        ActiviteDepot lecture = github(activite(3, 50));

        assertTrue(lecture.complet());
        assertEquals(100, lecture.pipelines().size());
        assertEquals(2, requetes(DEPOT_GITHUB + "/actions/runs").size());
    }

    @Test
    void githubVersionsArreteesDesQuUnePagePleineEstAncienne() {
        githubVide();
        StubServer.programmer(DEPOT_GITHUB + "/releases",
                Stub.succes(repete(100, "{\"published_at\":\"2025-01-05T10:00:00Z\",\"draft\":false}")));

        ActiviteDepot lecture = github(activite(5, 50));

        assertTrue(lecture.complet(), "l'arrêt sur une page ancienne n'est pas une troncature");
        assertTrue(lecture.versions().isEmpty());
        assertEquals(1, requetes(DEPOT_GITHUB + "/releases").size());
    }

    @Test
    void githubJalonsBornes() {
        githubVide();
        StubServer.programmer(DEPOT_GITHUB + "/milestones", Stub.succes("""
                [{"number":1,"title":"a","state":"open"},{"number":2,"title":"b","state":"open"}]"""));

        ActiviteDepot lecture = github(activite(5, 1));

        assertFalse(lecture.complet());
        assertEquals(List.of("1"), lecture.jalons().stream().map(ActiviteDepot.Jalon::reference).toList());
    }

    @Test
    void gitlabConversionJalonsBornesEtStatistiques() {
        gitlabVide();
        StubServer.programmer(PROJET_GITLAB + "/releases", Stub.succes("""
                [{"released_at":null,"created_at":"2026-01-04T00:00:00Z"},{"released_at":"2025-01-04T00:00:00Z","created_at":"2026-01-04T00:00:00Z"}]"""));
        StubServer.programmer(PROJET_GITLAB + "/pipelines", Stub.succes("""
                [{"created_at":"2026-01-02T00:00:00Z","status":"SUCCESS"},{"created_at":"2026-01-02T00:00:00Z","status":"failed"},
                 {"created_at":"2026-01-02T00:00:00Z","status":null},{"created_at":"2025-01-02T00:00:00Z","status":"failed"}]"""));
        StubServer.programmer(PROJET_GITLAB + "/milestones", Stub.succes("""
                [{"id":11,"title":"Sprint 4","state":"active","due_date":"2026-12-15"},
                 {"id":12,"title":null,"state":"active"},
                 {"id":13,"title":"Ferme","state":"closed","updated_at":"2026-01-09T00:00:00Z","due_date":"invalide"},
                 {"id":14,"title":"Encore","state":"active"}]"""));
        StubServer.programmer(PROJET_GITLAB + "/issues_statistics?milestone=Sprint%204",
                Stub.succes("{\"statistics\":{\"counts\":{\"all\":3,\"closed\":9}}}"));
        StubServer.programmer(PROJET_GITLAB + "/issues_statistics?milestone=Ferme", Stub.succes("{}"));

        ActiviteDepot lecture = gitlab(activite(5, 2));

        assertFalse(lecture.complet(), "troisième jalon retenu au-delà de la borne");
        assertEquals(List.of(new ActiviteDepot.Version(Instant.parse("2026-01-04T00:00:00Z"))), lecture.versions());
        assertEquals(List.of(ActiviteDepot.Issue.REUSSI, ActiviteDepot.Issue.ECHOUE, ActiviteDepot.Issue.AUTRE),
                lecture.pipelines().stream().map(ActiviteDepot.Pipeline::issue).toList());
        assertEquals(List.of(new ActiviteDepot.Jalon("11", "Sprint 4", true, LocalDate.of(2026, 12, 15), 3, 3),
                new ActiviteDepot.Jalon("13", "Ferme", false, null, 0, 0)), lecture.jalons());
        assertEquals(List.of(PROJET_GITLAB + "/issues?updated_after=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                PROJET_GITLAB + "/merge_requests?updated_after=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                PROJET_GITLAB + "/pipelines?updated_after=2026-01-01T00%3A00%3A00Z&per_page=100&page=1",
                PROJET_GITLAB + "/releases?per_page=100&page=1",
                PROJET_GITLAB + "/milestones?per_page=100&page=1",
                PROJET_GITLAB + "/issues_statistics?milestone=Sprint%204",
                PROJET_GITLAB + "/issues_statistics?milestone=Ferme"), requetes("/gitlab/"));
    }

    @Test
    void gitlabFusionsTronqueesAuNombreMaximalDePages() {
        gitlabVide();
        StubServer.programmer(PROJET_GITLAB + "/merge_requests", Stub.succes(repete(100, "{\"created_at\":\"2026-01-05T10:00:00Z\"}")));

        ActiviteDepot lecture = gitlab(activite(1, 50));

        assertFalse(lecture.complet());
        assertEquals(100, lecture.fusions().size());
    }

    @Test
    void erreursDeForgeEtDepotsInvalides() {
        RepositoryActivity activite = activite(5, 5);
        StubServer.programmer(DEPOT_GITHUB + "/issues", Stub.status(500));
        assertEquals("HTTP 500 de localhost", assertThrows(ScmException.class, () -> github(activite)).getMessage());

        StubServer.programmer(DEPOT_GITHUB + "/issues", Stub.succes("{pas du json"));
        assertEquals("réponse illisible de localhost", assertThrows(ScmException.class, () -> github(activite)).getMessage());

        assertEquals("dépôt GitHub invalide : attendu owner/repo", assertThrows(ScmException.class,
                () -> activite.lire(new RepositoryRef("GITHUB", "sans-barre", "main", null, null), DEPUIS)).getMessage());
        assertEquals("dépôt GitLab invalide : identifiant ou chemin attendu", assertThrows(ScmException.class,
                () -> activite.lire(new RepositoryRef("gitlab", " ", "main", null, null), DEPUIS)).getMessage());
        assertEquals("forge non prise en charge : bitbucket", assertThrows(ScmException.class,
                () -> activite.lire(new RepositoryRef("bitbucket", "a/b", "main", null, null), DEPUIS)).getMessage());
    }
}
