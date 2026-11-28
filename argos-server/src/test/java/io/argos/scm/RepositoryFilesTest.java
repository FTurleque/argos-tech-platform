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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Fixtures;
import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/**
 * Récupération des SBOM et des manifestes par {@link RepositoryFiles} (INV-01, INV-04, tâche 3.2) face aux forges
 * simulées par un serveur HTTP local, sans Quarkus : fichier absent ou dépôt introuvable, jeton refusé, quota épuisé,
 * erreur de forge, redirection non suivie, réponse trop volumineuse, chemins hostiles, forge non prise en charge, et
 * contrat GitLab / GitHub (points d'accès, jetons, en-têtes) avec les réponses de {@code fixtures/scm/}. Aucune forge
 * réelle n'est appelée.
 */
class RepositoryFilesTest {

    /** Délai de connexion et d'attente des en-têtes, large : aucun test n'attend son expiration. */
    private static final Duration DELAI = Duration.ofSeconds(5);
    private static final Instant MAINTENANT = Instant.parse("2026-11-20T10:00:00Z");
    private static final Clock HORLOGE = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    private static final long TAILLE_MAX = 1_000_000;
    private static final long TAILLE_REDUITE = 10;
    private static final long TAILLE_SBOM_MAX = 1_048_576;
    private static final long TAILLE_MINUSCULE = 64;
    /** Taille maximale d'un document de la configuration par défaut ({@code argos.inventory.max-document-bytes}). */
    private static final long TAILLE_DOCUMENT_MAX = 20_971_520;
    private static final long REPRISE_DIX_MINUTES = 600;
    private static final long REPRISE_QUINZE_MINUTES = 900;

    // Forges simulées sous des préfixes dédiés, avec leurs propres jetons.
    private static final String JETON_GITHUB = "jeton-gh-simule";
    private static final String JETON_GITLAB = "jeton-gl-simule";
    private static final String DEPOT_GH = "/forge-gh/repos/org/depot";
    private static final String PROJET_GL = "/forge-gl/api/v4/projects/42";
    private static final RepositoryRef DEPOT_GITHUB = new RepositoryRef("GITHUB", "org/depot", "main", null, "sbom/bom.json");
    private static final RepositoryRef DEPOT_GITLAB = new RepositoryRef("GITLAB", "42", "main", "sbom", "bom.json");

    // Contrat GitLab / GitHub, sous les préfixes de la configuration de test.
    private static final String JETON_GITLAB_CONTRAT = "jeton-fictif-gitlab-argos";
    private static final String JETON_GITHUB_CONTRAT = "jeton-fictif-github-argos";
    private static final RepositoryRef GITLAB = new RepositoryRef("GITLAB", "groupe/front", "main", "dependency_scanning",
            "gl-sbom-npm-npm.cdx.json");
    private static final RepositoryRef GITHUB = new RepositoryRef("GITHUB", "org/app", "main", null, "sbom/bom.json");
    private static final String GITLAB_SBOM =
            "/gitlab/api/v4/projects/groupe%2Ffront/jobs/artifacts/main/raw/gl-sbom-npm-npm.cdx.json?job=dependency_scanning";
    private static final String GITHUB_SBOM = "/github/repos/org/app/contents/sbom/bom.json?ref=main";
    private static final String SBOM_CYCLONEDX = "scm/gitlab/gl-sbom-npm-npm.cdx.json";

    private static ServeurHttpSimule serveur;

    private RepositoryFiles fichiers;

    @BeforeAll
    static void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
    }

    @AfterAll
    static void arreter() {
        serveur.close();
    }

    @BeforeEach
    void preparer() {
        serveur.reset();
        fichiers = new RepositoryFiles(parametres(), TAILLE_MAX, HORLOGE);
    }

    // ------------------------------------------------------------------ fichiers et SBOM

    @Test
    void fichierGithubLuAvecLeJetonEtCheminNettoye() {
        serveur.programmer(DEPOT_GH + "/contents/docs/sbom.json", Stub.succes("{\"bomFormat\": \"CycloneDX\"}"));

        Optional<byte[]> contenu = fichiers.lecture(DEPOT_GITHUB).file("../docs/./sbom.json");

        assertArrayEquals("{\"bomFormat\": \"CycloneDX\"}".getBytes(StandardCharsets.UTF_8), contenu.orElseThrow());
        ServeurHttpSimule.Received requete = serveur.received(DEPOT_GH + "/contents").get(0);
        assertEquals(List.of("Bearer " + JETON_GITHUB), requete.headers().get("Authorization"));
        assertTrue(requete.pathAndQuery().endsWith("?ref=main"), requete.pathAndQuery());
    }

    @Test
    void fichierGithubAbsentDUnDepotExistantVerifieLeDepotUneSeuleFois() {
        serveur.programmer(DEPOT_GH, Stub.succes("{\"full_name\": \"org/depot\"}"));
        RepositoryFiles.Lecture lecture = fichiers.lecture(DEPOT_GITHUB);

        assertEquals(Optional.empty(), lecture.file("package.json"));
        assertEquals(Optional.empty(), lecture.file("requirements.txt"));
        assertEquals(Optional.empty(), lecture.latestSbom());

        assertEquals(1, serveur.received(DEPOT_GH).stream().filter(requete -> requete.pathAndQuery().equals(DEPOT_GH)).count());
    }

    @Test
    void depotGithubIntrouvableOuRefuseApresUn404() {
        assertThrows(DepotIntrouvable.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json"));

        serveur.programmer(DEPOT_GH, Stub.status(403));
        assertTrue(assertThrows(AccesRefuse.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .contains("(HTTP 403)"));
    }

    @Test
    void projetGitlabIntrouvableDistingueDUnFichierAbsent() {
        serveur.programmer(PROJET_GL + "/repository/files/package.json/raw",
                new Stub(404, "{\"message\": \"404 File Not Found\"}", Map.of()));
        assertEquals(Optional.empty(), fichiers.lecture(DEPOT_GITLAB).file("package.json"));
        assertEquals(List.of(JETON_GITLAB), serveur.received(PROJET_GL).get(0).headers().get("Private-token"));

        serveur.programmer(PROJET_GL + "/repository/files/package.json/raw", new Stub(404,
                "{\"message\": \"" + RepositoryFiles.PROJET_GITLAB_INTROUVABLE + "\"}", Map.of()));
        assertThrows(DepotIntrouvable.class, () -> fichiers.lecture(DEPOT_GITLAB).file("package.json"));
    }

    @Test
    void sbomGitlabLueDansLArtefactDuJob() {
        serveur.programmer(PROJET_GL + "/jobs/artifacts/main/raw/bom.json", Stub.succes("SPDXVersion: SPDX-2.3"));

        SbomDocument document = fichiers.lecture(DEPOT_GITLAB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.SPDX_TAG_VALUE, document.format());
        assertTrue(serveur.received(PROJET_GL).get(0).pathAndQuery().endsWith("?job=sbom"));
    }

    @Test
    void sbomSansCheminOuSansJobGitlabAbsenteSansAppel() {
        assertEquals(Optional.empty(), fichiers.lecture(new RepositoryRef("GITHUB", "org/depot", "main", null, " ")).latestSbom());
        assertEquals(Optional.empty(), fichiers.lecture(new RepositoryRef("GITLAB", "42", "main", null, "bom.json")).latestSbom());
        assertEquals(List.of(), serveur.received("/forge-"));
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
        serveur.programmer(fichier, Stub.status(401));
        assertTrue(assertThrows(AccesRefuse.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .contains("(HTTP 401)"));

        serveur.programmer(fichier, Stub.status(500));
        ScmException erreur = assertThrows(ScmException.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json"));
        assertTrue(erreur.getMessage().startsWith("HTTP 500"), erreur.getMessage());
        assertFalse(erreur.getMessage().contains(JETON_GITHUB), "aucun jeton dans le message");

        serveur.programmer(fichier, new Stub(302, "", Map.of("Location", "https://ailleurs.invalid/")));
        assertTrue(assertThrows(ScmException.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .startsWith("HTTP 302"));
    }

    @Test
    void quotaEpuiseSignaleAvecSaDateDeReprise() {
        Instant reinitialisation = MAINTENANT.plusSeconds(REPRISE_DIX_MINUTES);
        serveur.programmer(DEPOT_GH + "/contents/package.json", new Stub(403, "", Map.of("x-ratelimit-remaining", "0",
                "x-ratelimit-reset", String.valueOf(reinitialisation.getEpochSecond()))));

        QuotaEpuise quota = assertThrows(QuotaEpuise.class, () -> fichiers.lecture(DEPOT_GITHUB).file("package.json"));

        assertEquals(reinitialisation, quota.reprise());
    }

    @Test
    void reponseAuDelaDeLaTailleMaximaleRefusee() {
        RepositoryFiles bornes = new RepositoryFiles(parametres(), TAILLE_REDUITE, HORLOGE);
        serveur.programmer(DEPOT_GH + "/contents/package.json", Stub.succes("{\"dependencies\": {}}"));

        assertTrue(assertThrows(ScmException.class, () -> bornes.lecture(DEPOT_GITHUB).file("package.json")).getMessage()
                .contains("taille maximale"));
    }

    @Test
    void forgeInjoignableSignaleeSansJeton() {
        RepositoryFiles injoignable = new RepositoryFiles(new ParametresForges(DELAI, "http://localhost:1/gl", "http://localhost:1/gh",
                Optional.of(JETON_GITLAB), Optional.of(JETON_GITHUB)), TAILLE_MAX, HORLOGE);

        ScmException erreur = assertThrows(ScmException.class, () -> injoignable.lecture(DEPOT_GITHUB).file("package.json"));

        assertTrue(erreur.getMessage().startsWith("erreur réseau"), erreur.getMessage());
        assertFalse(erreur.getMessage().contains(JETON_GITHUB), "aucun jeton dans le message");
    }

    // ------------------------------------------------------------------ contrat GitLab / GitHub

    @Test
    void gitlabArtefactCycloneDxDuJobEtJetonPrivateToken() {
        serveur.programmer(GITLAB_SBOM, fixture(200, SBOM_CYCLONEDX, Map.of("Content-Type", "application/json")));

        SbomDocument sbom = avecJetons(TAILLE_SBOM_MAX).lecture(GITLAB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        assertEquals(Fixtures.resource(SBOM_CYCLONEDX), texte(sbom.contenu()));
        ServeurHttpSimule.Received requete = serveur.received("/gitlab/").get(0);
        assertEquals("GET", requete.method());
        assertEquals(List.of(JETON_GITLAB_CONTRAT), entete(requete, "PRIVATE-TOKEN"));
        assertTrue(entete(requete, "Authorization").isEmpty(), "GitLab : jeton dans PRIVATE-TOKEN seulement");
    }

    @Test
    void gitlabFichierBrutEt404Absent() {
        serveur.programmer("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/package.json/raw?ref=main",
                fixture(200, "scm/gitlab/manifeste-package.json", Map.of("Content-Type", "text/plain; charset=utf-8")));
        serveur.programmer("/gitlab/api/v4/projects/groupe%2Ffront/repository/files/requirements.txt/raw?ref=main",
                fixture(404, "scm/gitlab/404-file.json", Map.of("Content-Type", "application/json")));

        assertEquals(Fixtures.resource("scm/gitlab/manifeste-package.json"),
                texte(sansJeton().lecture(GITLAB).file("package.json").orElseThrow()));
        assertTrue(sansJeton().lecture(GITLAB).file("requirements.txt").isEmpty(), "404 GitLab : fichier absent");
    }

    @Test
    void gitlabSansJobDeSbomAucunAppel() {
        RepositoryRef sansJob = new RepositoryRef("GITLAB", "groupe/front", "main", null, "bom.json");
        assertTrue(sansJeton().lecture(sansJob).latestSbom().isEmpty());
        assertTrue(serveur.received("/gitlab/").isEmpty());
    }

    @Test
    void githubContenuBrutAvecJetonBearer() {
        serveur.programmer(GITHUB_SBOM, fixture(200, SBOM_CYCLONEDX, Map.of()));

        SbomDocument sbom = avecJetons(TAILLE_SBOM_MAX).lecture(GITHUB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        ServeurHttpSimule.Received requete = serveur.received("/github/").get(0);
        assertEquals(List.of("application/vnd.github.raw+json"), entete(requete, "Accept"));
        assertEquals(List.of("Bearer " + JETON_GITHUB_CONTRAT), entete(requete, "Authorization"));
        assertTrue(entete(requete, "PRIVATE-TOKEN").isEmpty());
    }

    @Test
    void github404AbsentEt403QuotaEpuise() {
        serveur.programmer(GITHUB_SBOM, fixture(404, "scm/github/404.json", Map.of("Content-Type", "application/json")));
        serveur.programmer("/github/repos/org/app", Stub.succes("{\"full_name\":\"org/app\"}"));
        assertTrue(sansJeton().lecture(GITHUB).latestSbom().isEmpty(), "404 GitHub sur un dépôt existant : SBOM absente");

        long reinitialisation = MAINTENANT.plusSeconds(REPRISE_QUINZE_MINUTES).getEpochSecond();
        serveur.programmer(GITHUB_SBOM, fixture(403, "scm/github/403-rate-limit.json",
                Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", Long.toString(reinitialisation))));
        QuotaEpuise quota = assertThrows(QuotaEpuise.class, () -> sansJeton().lecture(GITHUB).latestSbom());
        assertEquals(reinitialisation, quota.reprise().getEpochSecond());
    }

    @Test
    void redirectionNonSuivieEtJetonAbsentDuMessage() {
        serveur.programmer(GITHUB_SBOM, new Stub(302, "", Map.of("Location", serveur.base() + "/ailleurs/bom.json")));

        ScmException erreur = assertThrows(ScmException.class, () -> avecJetons(TAILLE_SBOM_MAX).lecture(GITHUB).latestSbom());

        assertTrue(erreur.getMessage().contains("302"));
        assertFalse(erreur.getMessage().contains(JETON_GITHUB_CONTRAT), "le jeton n'apparaît dans aucun message");
        assertTrue(serveur.received("/ailleurs").isEmpty(), "la redirection n'est pas suivie");
    }

    @Test
    void sbomAuDelaDeLaTailleMaximaleRefusee() {
        serveur.programmer(GITHUB_SBOM, fixture(200, SBOM_CYCLONEDX, Map.of()));
        ScmException erreur = assertThrows(ScmException.class, () -> avecJetons(TAILLE_MINUSCULE).lecture(GITHUB).latestSbom());
        assertTrue(erreur.getMessage().contains("taille maximale"));
    }

    @Test
    void cheminsHostilesNeSortentPasDuDepot() {
        RepositoryRef hostile = new RepositoryRef("GITHUB", "org/app", "main", null, "../../../orgs/x/../bom.json");
        serveur.programmer("/github/repos/org/app/contents/orgs/x/bom.json?ref=main", Stub.succes("{}"));
        assertTrue(sansJeton().lecture(hostile).latestSbom().isPresent());

        assertThrows(ScmException.class,
                () -> sansJeton().lecture(new RepositoryRef("GITHUB", "../..", "main", null, null)).file("package.json"));
        assertThrows(ScmException.class,
                () -> sansJeton().lecture(new RepositoryRef("BITBUCKET", "org/app", "main", null, null)).file("package.json"));
        assertEquals(1, serveur.received("/github/").size(), "aucun appel pour un dépôt invalide ou une forge inconnue");
    }

    @Test
    void contenuServiTelQuel() {
        byte[] octets = Fixtures.resource("scm/github/bom.cdx.xml").getBytes(StandardCharsets.UTF_8);
        serveur.programmer(GITHUB_SBOM, Stub.octets(octets));
        assertArrayEquals(octets, sansJeton().lecture(GITHUB).latestSbom().orElseThrow().contenu());
    }

    /** Contrat GitLab puis GitHub sans jeton configuré : artefact, fichier absent, média brut, aucun jeton envoyé. */
    @Test
    void contratGitLabEtGitHub() {
        RepositoryRef gitlab = new RepositoryRef("GITLAB", "groupe/api", "main", "cyclonedx", "reports/bom.json");
        serveur.programmer("/gitlab/api/v4/projects/groupe%2Fapi/jobs/artifacts/main/raw/reports/bom.json?job=cyclonedx",
                Stub.succes("{\"bomFormat\":\"CycloneDX\"}"));
        assertEquals("{\"bomFormat\":\"CycloneDX\"}", texte(sansJeton().lecture(gitlab).latestSbom().orElseThrow().contenu()));
        assertTrue(sansJeton().lecture(gitlab).file("package.json").isEmpty(), "404 : fichier absent");

        RepositoryRef github = new RepositoryRef("GITHUB", "org/app", "main", null, "sbom/bom.json");
        serveur.programmer(GITHUB_SBOM, Stub.succes("{\"bomFormat\":\"CycloneDX\"}"));
        assertTrue(sansJeton().lecture(github).latestSbom().isPresent());
        ServeurHttpSimule.Received requete = serveur.received("/github/repos/org/app/contents/sbom/bom.json").get(0);
        assertEquals(List.of("application/vnd.github.raw+json"), requete.headers().get("Accept"));
        assertTrue(entete(serveur.received("/gitlab/").get(0), "PRIVATE-TOKEN").isEmpty(), "aucun jeton configuré, aucun envoyé");
    }

    private static ParametresForges parametres() {
        return new ParametresForges(DELAI, serveur.base() + "/forge-gl", serveur.base() + "/forge-gh",
                Optional.of(JETON_GITLAB), Optional.of(JETON_GITHUB));
    }

    /** Lecteur des préfixes de la configuration de test, avec des jetons fictifs, pour vérifier leur transmission. */
    private static RepositoryFiles avecJetons(long tailleMax) {
        ParametresForges forges = new ParametresForges(DELAI, serveur.base() + "/gitlab", serveur.base() + "/github",
                Optional.of(JETON_GITLAB_CONTRAT), Optional.of(JETON_GITHUB_CONTRAT));
        return new RepositoryFiles(forges, tailleMax, HORLOGE);
    }

    /** Lecteur des préfixes de la configuration de test, sans jeton configuré. */
    private static RepositoryFiles sansJeton() {
        ParametresForges forges = new ParametresForges(DELAI, serveur.base() + "/gitlab", serveur.base() + "/github",
                Optional.empty(), Optional.empty());
        return new RepositoryFiles(forges, TAILLE_DOCUMENT_MAX, HORLOGE);
    }

    private static Stub fixture(int status, String chemin, Map<String, String> entetes) {
        return new Stub(status, Fixtures.resource(chemin), entetes);
    }

    private static String texte(byte[] octets) {
        return new String(octets, StandardCharsets.UTF_8);
    }

    private static List<String> entete(ServeurHttpSimule.Received requete, String nom) {
        return requete.headers().entrySet().stream().filter(entree -> entree.getKey().equalsIgnoreCase(nom))
                .flatMap(entree -> entree.getValue().stream()).toList();
    }
}
