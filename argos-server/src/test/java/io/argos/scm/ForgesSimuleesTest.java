package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Connecteurs de forges face aux forges simulées (INV-01, INV-04, PIN-01) : fichier absent ou dépôt
 * introuvable, jeton refusé, quota épuisé, erreur de forge, réponse trop volumineuse ou illisible,
 * pagination bornée, jalons bornés, forge non prise en charge. Les connecteurs sont construits avec leurs
 * propres paramètres (jetons, bornes) et interrogent le serveur simulé sous des préfixes dédiés.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ForgesSimuleesTest {

    private static final Duration DELAI = Duration.ofSeconds(1);
    private static final long TAILLE_MAX = 1_000_000;
    private static final long TAILLE_REDUITE = 10;
    private static final int PAGES_MAX = 2;
    private static final int JALONS_MAX = 1;
    private static final Instant DEPUIS = Instant.parse("2026-11-01T00:00:00Z");
    private static final String JETON_GITHUB = "jeton-gh-simule";
    private static final String JETON_GITLAB = "jeton-gl-simule";
    private static final String DEPOT_GH = "/forge-gh/repos/org/depot";
    private static final String PROJET_GL = "/forge-gl/api/v4/projects/42";
    private static final RepositoryRef DEPOT_GITHUB = new RepositoryRef("GITHUB", "org/depot", "main", null, "sbom/bom.json");
    private static final RepositoryRef DEPOT_GITLAB = new RepositoryRef("GITLAB", "42", "main", "sbom", "bom.json");

    private RepositoryFiles fichiers;

    @BeforeEach
    void preparer() {
        StubServer.reset();
        fichiers = new RepositoryFiles(parametres(), TAILLE_MAX, Clock.systemUTC());
    }

    // ------------------------------------------------------------------ fichiers et SBOM

    @Test
    void fichierGithubLuAvecLeJetonEtCheminNettoye() {
        StubServer.programmer(DEPOT_GH + "/contents/docs/sbom.json", Stub.succes("{\"bomFormat\": \"CycloneDX\"}"));

        Optional<byte[]> contenu = fichiers.lecture(DEPOT_GITHUB).file("../docs/./sbom.json");

        assertArrayEquals("{\"bomFormat\": \"CycloneDX\"}".getBytes(StandardCharsets.UTF_8), contenu.orElseThrow());
        StubServer.Received requete = StubServer.received(DEPOT_GH + "/contents").get(0);
        assertEquals(List.of("Bearer " + JETON_GITHUB), requete.headers().get("Authorization"));
        assertTrue(requete.pathAndQuery().endsWith("?ref=main"), requete.pathAndQuery());
    }

    @Test
    void fichierGithubAbsentDUnDepotExistantVerifieLeDepotUneSeuleFois() {
        StubServer.programmer(DEPOT_GH, Stub.succes("{\"full_name\": \"org/depot\"}"));
        RepositoryFiles.Lecture lecture = fichiers.lecture(DEPOT_GITHUB);

        assertEquals(Optional.empty(), lecture.file("package.json"));
        assertEquals(Optional.empty(), lecture.file("requirements.txt"));
        assertEquals(Optional.empty(), lecture.latestSbom());

        assertEquals(1, StubServer.received(DEPOT_GH).stream().filter(requete -> requete.pathAndQuery().equals(DEPOT_GH)).count());
    }

    @Test
    void depotGithubIntrouvableOuRefuseApresUn404() {
        assertThrows(DepotIntrouvable.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json"));

        StubServer.programmer(DEPOT_GH, Stub.status(403));
        assertTrue(assertThrows(AccesRefuse.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .contains("(HTTP 403)"));
    }

    @Test
    void projetGitlabIntrouvableDistingueDUnFichierAbsent() {
        StubServer.programmer(PROJET_GL + "/repository/files/package.json/raw",
                new Stub(404, "{\"message\": \"404 File Not Found\"}", Map.of(), false, null));
        assertEquals(Optional.empty(), fichiers.lecture(DEPOT_GITLAB).file("package.json"));
        assertEquals(List.of(JETON_GITLAB), StubServer.received(PROJET_GL).get(0).headers().get("Private-token"));

        StubServer.programmer(PROJET_GL + "/repository/files/package.json/raw", new Stub(404,
                "{\"message\": \"" + RepositoryFiles.PROJET_GITLAB_INTROUVABLE + "\"}", Map.of(), false, null));
        assertThrows(DepotIntrouvable.class, () -> fichiers.lecture(DEPOT_GITLAB).file("package.json"));
    }

    @Test
    void sbomGitlabLueDansLArtefactDuJob() {
        StubServer.programmer(PROJET_GL + "/jobs/artifacts/main/raw/bom.json", Stub.succes("SPDXVersion: SPDX-2.3"));

        SbomDocument document = fichiers.lecture(DEPOT_GITLAB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.SPDX_TAG_VALUE, document.format());
        assertTrue(StubServer.received(PROJET_GL).get(0).pathAndQuery().endsWith("?job=sbom"));
    }

    @Test
    void sbomSansCheminOuSansJobGitlabAbsenteSansAppel() {
        assertEquals(Optional.empty(), fichiers.lecture(new RepositoryRef("GITHUB", "org/depot", "main", null, " ")).latestSbom());
        assertEquals(Optional.empty(), fichiers.lecture(new RepositoryRef("GITLAB", "42", "main", null, "bom.json")).latestSbom());
        assertEquals(List.of(), StubServer.received("/forge-"));
    }

    @Test
    void forgeNonPriseEnChargeEtDepotGithubInvalideRefuses() {
        RepositoryRef bitbucket = new RepositoryRef("BITBUCKET", "org/depot", "main", "job", "bom.json");
        assertThrows(ScmException.class, () -> fichiers.lecture(bitbucket).file("package.json"));
        assertThrows(ScmException.class, () -> fichiers.lecture(bitbucket).latestSbom());
        assertThrows(ScmException.class, () -> fichiers.lecture(new RepositoryRef("GITHUB", "../..", "main", null, null)).file("x"));
    }

    @Test
    void jetonRefuseErreurDeForgeEtRedirectionDistingues() {
        String fichier = DEPOT_GH + "/contents/package.json";
        StubServer.programmer(fichier, Stub.status(401));
        assertTrue(assertThrows(AccesRefuse.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .contains("(HTTP 401)"));

        StubServer.programmer(fichier, Stub.status(500));
        ScmException erreur = assertThrows(ScmException.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json"));
        assertTrue(erreur.getMessage().startsWith("HTTP 500"), erreur.getMessage());
        assertFalse(erreur.getMessage().contains(JETON_GITHUB), "aucun jeton dans le message");

        StubServer.programmer(fichier, new Stub(302, "", Map.of("Location", "https://ailleurs.invalid/"), false, null));
        assertTrue(assertThrows(ScmException.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .startsWith("HTTP 302"));
    }

    @Test
    void quotaEpuiseSignaleAvecSaDateDeReprise() {
        StubServer.programmer(DEPOT_GH + "/contents/package.json", new Stub(403, "", Map.of("x-ratelimit-remaining", "0",
                "x-ratelimit-reset", String.valueOf(Instant.now().plusSeconds(600).getEpochSecond())), false, null));

        QuotaEpuise quota = assertThrows(QuotaEpuise.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json"));

        assertTrue(quota.reprise().isAfter(Instant.now()), String.valueOf(quota.reprise()));
    }

    @Test
    void reponseAuDelaDeLaTailleMaximaleRefusee() {
        RepositoryFiles bornes = new RepositoryFiles(parametres(), TAILLE_REDUITE, Clock.systemUTC());
        StubServer.programmer(DEPOT_GH + "/contents/package.json", Stub.succes("{\"dependencies\": {}}"));

        assertTrue(assertThrows(ScmException.class, () -> bornes.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .contains("taille maximale"));
    }

    @Test
    void forgeInjoignableSignaleeSansJeton() {
        RepositoryFiles injoignable = new RepositoryFiles(new ParametresForges(DELAI, "http://localhost:1/gl", "http://localhost:1/gh",
                Optional.of(JETON_GITLAB), Optional.of(JETON_GITHUB)), TAILLE_MAX, Clock.systemUTC());

        ScmException erreur = assertThrows(ScmException.class, () -> injoignable.lecture(DEPOT_GITHUB).file("package.json"));

        assertTrue(erreur.getMessage().startsWith("erreur réseau"), erreur.getMessage());
    }

    // ------------------------------------------------------------------ activité GitHub

    @Test
    void activiteGithubBorneeParLeNombreDePagesEtDeJalons() {
        StubServer.programmer(DEPOT_GH + "/issues", Stub.succes(pagePleine(
                "{\"created_at\": \"2026-11-02T00:00:00Z\", \"closed_at\": null}")));
        StubServer.programmer(DEPOT_GH + "/actions/runs", Stub.succes("""
                {"workflow_runs": [{"created_at": "2026-11-03T00:00:00Z", "conclusion": "success"},
                  {"created_at": "2026-11-03T01:00:00Z", "conclusion": "timed_out"},
                  {"created_at": "2026-11-03T02:00:00Z", "conclusion": null},
                  {"created_at": "2026-11-03T03:00:00Z", "conclusion": "cancelled"},
                  {"created_at": "2026-10-01T00:00:00Z", "conclusion": "success"}]}
                """));
        StubServer.programmer(DEPOT_GH + "/releases", Stub.succes("""
                [{"published_at": "2026-11-04T00:00:00Z", "draft": false}, {"published_at": "2026-11-05T00:00:00Z", "draft": true},
                 {"published_at": "2026-10-04T00:00:00Z", "draft": false}, {"published_at": null, "draft": false}]
                """));
        StubServer.programmer(DEPOT_GH + "/milestones", Stub.succes("""
                [{"number": null, "title": "Sans numéro", "state": "open"},
                 {"number": 7, "title": "Version 7", "state": "open", "due_on": "2026-12-01T00:00:00Z", "open_issues": 3,
                  "closed_issues": -1},
                 {"number": 8, "title": "Version 8", "state": "open"},
                 {"number": 6, "title": "Ancienne", "state": "closed", "closed_at": "2026-01-01T00:00:00Z"}]
                """));

        ActiviteDepot activite = activite().lire(DEPOT_GITHUB, DEPUIS);

        assertFalse(activite.complet(), "pages et jalons bornés");
        assertEquals(PAGES_MAX * RepositoryActivity.PAR_PAGE, activite.tickets().size());
        assertEquals(List.of(ActiviteDepot.Issue.REUSSI, ActiviteDepot.Issue.ECHOUE, ActiviteDepot.Issue.AUTRE,
                ActiviteDepot.Issue.AUTRE), activite.pipelines().stream().map(ActiviteDepot.Pipeline::issue).toList());
        assertEquals(List.of(new ActiviteDepot.Version(Instant.parse("2026-11-04T00:00:00Z"))), activite.versions());
        assertEquals(List.of(new ActiviteDepot.Jalon("7", "Version 7", true, LocalDate.parse("2026-12-01"), 0, 3)), activite.jalons());
    }

    @Test
    void activiteGithubRefuseUnDepotInvalideEtSignaleLesErreursDeForge() {
        RepositoryActivity lecteur = activite();
        assertThrows(ScmException.class, () -> lecteur.lire(new RepositoryRef("GITHUB", "pas-de-barre", "main", null, null), DEPUIS));
        assertThrows(ScmException.class, () -> lecteur.lire(new RepositoryRef(null, "org/depot", "main", null, null), DEPUIS));

        StubServer.programmer(DEPOT_GH + "/issues", Stub.status(500));
        assertTrue(assertThrows(ScmException.class, () -> lecteur.lire(DEPOT_GITHUB, DEPUIS)).getMessage().startsWith("HTTP 500"));

        StubServer.programmer(DEPOT_GH + "/issues", Stub.succes("{illisible"));
        assertTrue(assertThrows(ScmException.class, () -> lecteur.lire(DEPOT_GITHUB, DEPUIS)).getMessage()
                .startsWith("réponse illisible"));

        StubServer.programmer(DEPOT_GH + "/issues", new Stub(429, "", Map.of("retry-after", "60"), false, null));
        assertThrows(QuotaEpuise.class, () -> lecteur.lire(DEPOT_GITHUB, DEPUIS));
    }

    // ------------------------------------------------------------------ activité GitLab

    @Test
    void activiteGitlabAvecCompteursDeJalonsEtDatesDeRepli() {
        StubServer.programmer(PROJET_GL + "/issues",
                Stub.succes("[{\"created_at\": \"2026-11-02T00:00:00Z\", \"closed_at\": \"illisible\"}]"));
        StubServer.programmer(PROJET_GL + "/merge_requests",
                Stub.succes("[{\"created_at\": \"2026-11-02T00:00:00Z\", \"merged_at\": null}]"));
        StubServer.programmer(PROJET_GL + "/pipelines", Stub.succes("""
                [{"created_at": "2026-11-03T00:00:00Z", "status": "success"}, {"created_at": "2026-11-03T00:00:00Z", "status": "failed"},
                 {"created_at": "2026-11-03T00:00:00Z", "status": "running"}, {"created_at": "2026-11-03T00:00:00Z", "status": null}]
                """));
        StubServer.programmer(PROJET_GL + "/releases", Stub.succes("""
                [{"released_at": null, "created_at": "2026-11-06T00:00:00Z"}, {"released_at": "2026-10-06T00:00:00Z"}]
                """));
        StubServer.programmer(PROJET_GL + "/milestones", Stub.succes("""
                [{"id": 11, "title": "Sprint 11", "state": "active", "due_date": "2026-12-15"},
                 {"id": 12, "title": null, "state": "active"},
                 {"id": 10, "title": "Sprint 10", "state": "closed", "updated_at": "2026-01-01T00:00:00Z"}]
                """));
        StubServer.programmer(PROJET_GL + "/issues_statistics", Stub.succes("{\"statistics\": {\"counts\": {\"all\": 4, \"closed\": 9}}}"));

        ActiviteDepot activite = activite().lire(DEPOT_GITLAB, DEPUIS);

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
            StubServer.programmer(PROJET_GL + chemin, Stub.succes("[]"));
        }
        StubServer.programmer(PROJET_GL + "/milestones", Stub.succes("[{\"id\": 11, \"title\": \"Sprint 11\", \"state\": \"active\"}]"));
        StubServer.programmer(PROJET_GL + "/issues_statistics", Stub.succes("{}"));

        ActiviteDepot activite = activite().lire(DEPOT_GITLAB, DEPUIS);

        assertEquals(List.of(new ActiviteDepot.Jalon("11", "Sprint 11", true, null, 0, 0)), activite.jalons());
        assertThrows(ScmException.class, () -> activite().lire(new RepositoryRef("GITLAB", " ", "main", null, null), DEPUIS));
    }

    private static RepositoryActivity activite() {
        return new RepositoryActivity(parametres(), PAGES_MAX, JALONS_MAX, TAILLE_MAX, Clock.systemUTC());
    }

    private static ParametresForges parametres() {
        return new ParametresForges(DELAI, StubServer.base() + "/forge-gl", StubServer.base() + "/forge-gh",
                Optional.of(JETON_GITLAB), Optional.of(JETON_GITHUB));
    }

    private static String pagePleine(String element) {
        return IntStream.range(0, RepositoryActivity.PAR_PAGE).mapToObj(rang -> element).collect(Collectors.joining(",", "[", "]"));
    }
}
