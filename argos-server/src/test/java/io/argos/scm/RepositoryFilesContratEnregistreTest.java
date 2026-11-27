package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Fixtures;
import io.argos.testsupport.ServeurHttpSimule;

/**
 * Contrat enregistré de {@link RepositoryFiles} (tâche 3.2, INV-01, INV-04), sans Quarkus : chaque réponse servie par
 * le serveur HTTP local est une réponse de {@code api.github.com} ou de {@code gitlab.com} enregistrée le 2026-11-18
 * avec ses en-têtes ({@code fixtures/scm/enregistrees/}, voir {@code fixtures/NOTICE}), sauf les limites de débit et
 * l'artefact SBOM GitLab, composés d'après la documentation des forges faute de pouvoir les provoquer sans
 * authentification. Aucune forge réelle n'est appelée ; les jetons sont des valeurs fictives. Les limites de débit
 * enregistrées portent une date de reprise relative à l'instant présent : l'horloge est celle du système.
 */
class RepositoryFilesContratEnregistreTest {

    private static final String ENREGISTREMENTS_GITHUB = "scm/enregistrees/github/";
    private static final String ENREGISTREMENTS_GITLAB = "scm/enregistrees/gitlab/";
    private static final String JETON_GITLAB = "jeton-fictif-gitlab-argos";
    private static final String JETON_GITHUB = "jeton-fictif-github-argos";
    /** Délai de connexion et d'attente des en-têtes, large : aucun test n'attend son expiration. */
    private static final Duration DELAI = Duration.ofSeconds(5);
    private static final long TAILLE_DOCUMENT_MAX = 20_971_520;
    private static final long REPRISE_PRINCIPALE_SECONDES = 900;
    private static final long REPRISE_SECONDAIRE_SECONDES = 60;
    private static final long REPRISE_SANS_DELAI_SECONDES = 3600;
    private static final long REPRISE_GITLAB_SECONDES = 120;
    private static final long TOLERANCE_SECONDES = 2;

    /** Dépôt public dont les réponses GitHub ont été enregistrées. */
    private static final RepositoryRef GITHUB = new RepositoryRef("GITHUB", "CycloneDX/cyclonedx-node-npm", "main", null,
            "demo/dev-dependencies/example-results/flat/bom.1.6.json");
    private static final String GITHUB_DEPOT = "/github/repos/CycloneDX/cyclonedx-node-npm";
    private static final String GITHUB_SBOM = GITHUB_DEPOT
            + "/contents/demo/dev-dependencies/example-results/flat/bom.1.6.json?ref=main";

    /** Projet public dont les réponses GitLab ont été enregistrées. */
    private static final RepositoryRef GITLAB = new RepositoryRef("GITLAB", "gitlab-org/security-products/tests/js-npm",
            "master", "gemnasium-dependency_scanning", "gl-sbom-npm-npm.cdx.json");
    private static final String GITLAB_PROJET = "/gitlab/api/v4/projects/gitlab-org%2Fsecurity-products%2Ftests%2Fjs-npm";
    private static final String GITLAB_SBOM = GITLAB_PROJET
            + "/jobs/artifacts/master/raw/gl-sbom-npm-npm.cdx.json?job=gemnasium-dependency_scanning";
    private static final String GITLAB_PACKAGE = GITLAB_PROJET + "/repository/files/package.json/raw?ref=master";

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

    // --- GitHub -------------------------------------------------------------------------------

    @Test
    void githubSbomTrouveeEtTransmiseAvecJetonBearer() {
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "200-sbom-cyclonedx.http");

        SbomDocument sbom = forges().lecture(GITHUB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        assertEquals(Fixtures.corpsEnregistre(ENREGISTREMENTS_GITHUB + "200-sbom-cyclonedx.http"),
                new String(sbom.contenu(), StandardCharsets.UTF_8));
        List<ServeurHttpSimule.Received> requetes = serveur.received("/github/");
        assertEquals(1, requetes.size(), "SBOM trouvée : aucun appel supplémentaire");
        assertEquals("GET", requetes.get(0).method());
        assertEquals(List.of("Bearer " + JETON_GITHUB), entete(requetes.get(0), "Authorization"));
        assertEquals(List.of("application/vnd.github.raw+json"), entete(requetes.get(0), "Accept"));
    }

    @Test
    void githubDepotIntrouvable() {
        RepositoryRef absent = new RepositoryRef("GITHUB", "argos-poc-inexistant-7f3a/depot-inexistant", "main", null, "bom.json");
        String depot = "/github/repos/argos-poc-inexistant-7f3a/depot-inexistant";
        sert(depot + "/contents/bom.json?ref=main", ENREGISTREMENTS_GITHUB + "404-depot-contenu.http");
        sert(depot, ENREGISTREMENTS_GITHUB + "404-depot.http");

        DepotIntrouvable erreur = assertThrows(DepotIntrouvable.class, () -> forges().lecture(absent).latestSbom());

        assertTrue(erreur.getMessage().contains("argos-poc-inexistant-7f3a/depot-inexistant"));
        assertFalse(erreur.getMessage().contains(JETON_GITHUB), "le jeton n'apparaît dans aucun message");
    }

    @Test
    void githubJetonRefuse() {
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "401-jeton-refuse.http");

        AccesRefuse erreur = assertThrows(AccesRefuse.class, () -> forges().lecture(GITHUB).latestSbom());

        assertTrue(erreur.getMessage().contains("(HTTP 401)"), erreur.getMessage());
        assertTrue(erreur.getMessage().contains("jeton d'accès refusé"));
        assertFalse(erreur.getMessage().contains(JETON_GITHUB), "le jeton n'apparaît dans aucun message");
        assertEquals(1, serveur.received("/github/").size(), "aucune vérification du dépôt après un 401");
    }

    @Test
    void githubLimiteDeDebitPrincipaleEtSecondaire() {
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "403-limite-debit.http");
        Instant avant = Instant.now();
        QuotaEpuise principale = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITHUB).latestSbom());
        assertBorne(principale.reprise(), avant.plusSeconds(REPRISE_PRINCIPALE_SECONDES));

        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "403-limite-secondaire.http");
        avant = Instant.now();
        QuotaEpuise secondaire = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITHUB).latestSbom());
        assertBorne(secondaire.reprise(), avant.plusSeconds(REPRISE_SECONDAIRE_SECONDES));

        // Limite secondaire signalée par son seul message : ni Retry-After ni quota principal épuisé.
        sert(GITHUB_SBOM, ENREGISTREMENTS_GITHUB + "403-limite-secondaire-sans-delai.http");
        avant = Instant.now();
        QuotaEpuise sansDelai = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITHUB).latestSbom());
        assertBorne(sansDelai.reprise(), avant.plusSeconds(REPRISE_SANS_DELAI_SECONDES));
    }

    // --- GitLab -------------------------------------------------------------------------------

    @Test
    void gitlabSbomTrouveeAvecJetonPrivateToken() {
        // Artefact composé : aucun artefact de job n'est lisible anonymement sur gitlab.com (403 enregistré).
        serveur.programmer(GITLAB_SBOM, new ServeurHttpSimule.Stub(200, Fixtures.resource("scm/gitlab/gl-sbom-npm-npm.cdx.json"),
                Map.of("Content-Type", "application/json")));

        SbomDocument sbom = forges().lecture(GITLAB).latestSbom().orElseThrow();

        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, sbom.format());
        ServeurHttpSimule.Received requete = serveur.received("/gitlab/").get(0);
        assertEquals(List.of(JETON_GITLAB), entete(requete, "PRIVATE-TOKEN"));
        assertTrue(entete(requete, "Authorization").isEmpty());
    }

    @Test
    void gitlabProjetIntrouvable() {
        RepositoryRef absent = new RepositoryRef("GITLAB", "argos-poc-inexistant-7f3a/depot-inexistant", "main", null, null);
        sert("/gitlab/api/v4/projects/argos-poc-inexistant-7f3a%2Fdepot-inexistant/repository/files/package.json/raw?ref=main",
                ENREGISTREMENTS_GITLAB + "404-projet-fichier.http");

        DepotIntrouvable erreur = assertThrows(DepotIntrouvable.class, () -> forges().lecture(absent).file("package.json"));

        assertTrue(erreur.getMessage().contains("GITLAB"));
        assertFalse(erreur.getMessage().contains(JETON_GITLAB), "le jeton n'apparaît dans aucun message");
    }

    @Test
    void gitlabJetonRefuseEtArtefactInterdit() {
        sert(GITLAB_PACKAGE, ENREGISTREMENTS_GITLAB + "401-jeton-refuse.http");
        AccesRefuse refuse = assertThrows(AccesRefuse.class, () -> forges().lecture(GITLAB).file("package.json"));
        assertTrue(refuse.getMessage().contains("(HTTP 401)"), refuse.getMessage());
        assertFalse(refuse.getMessage().contains(JETON_GITLAB));

        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "403-artefact-interdit.http");
        AccesRefuse interdit = assertThrows(AccesRefuse.class, () -> forges().lecture(GITLAB).latestSbom());
        assertTrue(interdit.getMessage().contains("(HTTP 403)"), interdit.getMessage());
    }

    /** Limite de débit GitLab enregistrée (429) : quota épuisé, reprise annoncée par la forge. */
    @Test
    void gitlabLimiteDeDebit() {
        sert(GITLAB_SBOM, ENREGISTREMENTS_GITLAB + "429-limite-debit.http");
        Instant avant = Instant.now();
        QuotaEpuise quota = assertThrows(QuotaEpuise.class, () -> forges().lecture(GITLAB).latestSbom());
        assertBorne(quota.reprise(), avant.plusSeconds(REPRISE_GITLAB_SECONDES));
    }

    @Test
    void entetesDeTransportNonRejoues() {
        Fixtures.ReponseHttp reponse = Fixtures.lireReponse(ENREGISTREMENTS_GITHUB + "200-depot.http");
        assertEquals(200, reponse.status());
        assertFalse(reponse.entetes().keySet().stream().anyMatch(cle -> cle.equalsIgnoreCase("content-length")),
                "les en-têtes de transport ne sont pas rejoués");
    }

    /** Lecteur dont les jetons sont des valeurs fictives. */
    private static RepositoryFiles forges() {
        ParametresForges forges = new ParametresForges(DELAI, serveur.base() + "/gitlab", serveur.base() + "/github",
                Optional.of(JETON_GITLAB), Optional.of(JETON_GITHUB));
        return new RepositoryFiles(forges, TAILLE_DOCUMENT_MAX, Clock.systemUTC());
    }

    /** Sert, sur le chemin donné, une réponse enregistrée rejouée avec son statut, ses en-têtes et son corps. */
    private static void sert(String chemin, String enregistrement) {
        Fixtures.ReponseHttp reponse = Fixtures.lireReponse(enregistrement);
        serveur.programmer(chemin, new ServeurHttpSimule.Stub(reponse.status(), reponse.corps(), reponse.entetes()));
    }

    private static List<String> entete(ServeurHttpSimule.Received requete, String nom) {
        return requete.headers().entrySet().stream().filter(entree -> entree.getKey().equalsIgnoreCase(nom))
                .flatMap(entree -> entree.getValue().stream()).toList();
    }

    /** La reprise annoncée tombe à deux secondes près sur l'instant attendu. */
    private static void assertBorne(Instant reprise, Instant attendu) {
        assertTrue(Math.abs(Duration.between(attendu, reprise).toSeconds()) <= TOLERANCE_SECONDES,
                "reprise " + reprise + " au lieu de " + attendu);
    }
}
