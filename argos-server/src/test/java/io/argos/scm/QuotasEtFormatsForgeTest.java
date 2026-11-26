package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Règles locales du connecteur de forges, sans appel réseau : détection d'un quota épuisé (429, 403 signalé par
 * en-tête ou par message) et date de reprise bornée ; validation d'un dépôt GitHub ; reconnaissance des formats
 * de SBOM ; message d'erreur GitLab ; nettoyage des titres de jalons.
 */
class QuotasEtFormatsForgeTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T10:00:00Z");
    private static final byte[] VIDE = new byte[0];
    private static final int TITRE_MAX = 200;
    private static final int CORPS_MESSAGE_MAX = 65_536;

    // ------------------------------------------------------------------ quotas

    @Test
    void reponseOrdinaireOu403SansSignalNEstPasUnQuota() {
        assertEquals(Optional.empty(), ForgeHttp.quota(200, entetes(Map.of()), VIDE, MAINTENANT));
        assertEquals(Optional.empty(), ForgeHttp.quota(403, entetes(Map.of("x-ratelimit-remaining", "12")), VIDE, MAINTENANT));
        assertEquals(Optional.empty(), ForgeHttp.quota(404, entetes(Map.of("retry-after", "30")), VIDE, MAINTENANT));
    }

    @Test
    void reponse429SansDateRepriseApresLeReportParDefaut() {
        assertEquals(Optional.of(MAINTENANT.plus(ForgeHttp.REPORT_PAR_DEFAUT)),
                ForgeHttp.quota(429, entetes(Map.of()), VIDE, MAINTENANT));
    }

    @Test
    void retryAfterPrioritaireSurLesDatesDeReinitialisation() {
        HttpHeaders entetes = entetes(Map.of("retry-after", " 120 ", "x-ratelimit-reset",
                String.valueOf(MAINTENANT.plusSeconds(600).getEpochSecond())));
        assertEquals(Optional.of(MAINTENANT.plusSeconds(120)), ForgeHttp.quota(403, entetes, VIDE, MAINTENANT));
    }

    @Test
    void reinitialisationGithubPuisGitlabLuesEnSecondesDepuisLEpoque() {
        long dansDixMinutes = MAINTENANT.plusSeconds(600).getEpochSecond();
        assertEquals(Optional.of(Instant.ofEpochSecond(dansDixMinutes)), ForgeHttp.quota(403,
                entetes(Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(dansDixMinutes))), VIDE, MAINTENANT));
        assertEquals(Optional.of(Instant.ofEpochSecond(dansDixMinutes)), ForgeHttp.quota(429,
                entetes(Map.of("ratelimit-reset", String.valueOf(dansDixMinutes))), VIDE, MAINTENANT));
    }

    @Test
    void repriseIllisiblePasseeOuTropLointaineBornee() {
        assertEquals(Optional.of(MAINTENANT.plus(ForgeHttp.REPORT_PAR_DEFAUT)),
                ForgeHttp.quota(429, entetes(Map.of("retry-after", "demain")), VIDE, MAINTENANT), "valeur illisible ignorée");
        assertEquals(Optional.of(MAINTENANT), ForgeHttp.quota(429,
                entetes(Map.of("x-ratelimit-reset", String.valueOf(MAINTENANT.minusSeconds(60).getEpochSecond()))), VIDE, MAINTENANT));
        assertEquals(Optional.of(MAINTENANT.plus(ForgeHttp.REPORT_PAR_DEFAUT)), ForgeHttp.quota(429,
                entetes(Map.of("retry-after", String.valueOf(ForgeHttp.REPORT_MAX.toSeconds() + 1))), VIDE, MAINTENANT));
    }

    @Test
    void limiteSecondaireSignaleeParLeMessageDuCorps() {
        byte[] message = "{\"message\": \"You have exceeded a secondary RATE LIMIT\"}".getBytes(StandardCharsets.UTF_8);
        assertTrue(ForgeHttp.quota(403, entetes(Map.of()), message, MAINTENANT).isPresent());
        assertFalse(ForgeHttp.mentionneLimiteDeDebit(null));
        assertFalse(ForgeHttp.mentionneLimiteDeDebit(VIDE));
        byte[] tropLong = ("rate limit" + " ".repeat(CORPS_MESSAGE_MAX)).getBytes(StandardCharsets.UTF_8);
        assertFalse(ForgeHttp.mentionneLimiteDeDebit(tropLong), "corps trop long : non examiné");
    }

    // ------------------------------------------------------------------ dépôts, messages, titres

    @Test
    void depotGithubOwnerRepoSansSegmentDePoints() {
        assertTrue(ForgeHttp.depotGithubValide("argos-tech/argos.platform"));
        assertTrue(ForgeHttp.depotGithubValide("o/.config"));
        for (String depot : new String[] {null, "", "seul", "a/b/c", "../depot", "owner/..", "owner/.", "o r/x"}) {
            assertFalse(ForgeHttp.depotGithubValide(depot), String.valueOf(depot));
        }
    }

    @Test
    void messageDErreurGitlabLuSeulementDansUnObjetJson() {
        assertEquals(RepositoryFiles.PROJET_GITLAB_INTROUVABLE,
                RepositoryFiles.message("{\"message\": \"404 Project Not Found\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", RepositoryFiles.message(VIDE));
        assertEquals("", RepositoryFiles.message("{\"message\": {\"base\": \"x\"}}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", RepositoryFiles.message("<html>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void titreDeJalonNettoyeEtBorne() {
        assertEquals("(sans titre)", RepositoryActivity.titre(null));
        assertEquals("(sans titre)", RepositoryActivity.titre(" \u0000​ "));
        assertEquals("Version 2 finale", RepositoryActivity.titre("Version\t2\u0007  finale"));
        assertEquals(TITRE_MAX, RepositoryActivity.titre("t".repeat(TITRE_MAX + 1)).length());
    }

    // ------------------------------------------------------------------ formats de SBOM

    @Test
    void formatsDeSbomReconnusSurLEntete() {
        assertEquals(SbomDocument.Format.CYCLONEDX_JSON, format("﻿  {\"bomFormat\": \"CycloneDX\"}"));
        assertEquals(SbomDocument.Format.SPDX_JSON, format("{\"name\": \"x\", \"spdxVersion\": \"SPDX-2.3\"}"));
        assertEquals(SbomDocument.Format.CYCLONEDX_XML,
                format("<?xml version=\"1.0\"?><bom xmlns=\"http://cyclonedx.org/schema/bom/1.6\"/>"));
        assertEquals(SbomDocument.Format.SPDX_TAG_VALUE, format("\nSPDXVersion: SPDX-2.3\nDataLicense: CC0-1.0"));
    }

    @Test
    void documentsNonReconnus() {
        assertEquals(SbomDocument.Format.INCONNU, format(""));
        assertEquals(SbomDocument.Format.INCONNU, format("   "));
        assertEquals(SbomDocument.Format.INCONNU, format("<html></html>"));
        assertEquals(SbomDocument.Format.INCONNU, format("{\"bomFormat\": 1}"));
        assertEquals(SbomDocument.Format.INCONNU, format("{\"bomFormat\": \"SPDX\"}"));
        assertEquals(SbomDocument.Format.INCONNU, format("{\"sbom\": \"texte\"}"));
        assertEquals(SbomDocument.Format.INCONNU, format("{illisible"));
        assertEquals(SbomDocument.Format.INCONNU, format("name: x"));
    }

    @Test
    void egaliteDesDocumentsSurLeContenuEtLeFormat() {
        SbomDocument document = SbomDocument.depuis("{\"bomFormat\": \"CycloneDX\"}".getBytes(StandardCharsets.UTF_8));
        SbomDocument copie = SbomDocument.depuis("{\"bomFormat\": \"CycloneDX\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(document, copie);
        assertEquals(document.hashCode(), copie.hashCode());
        assertNotEquals(document, new SbomDocument(document.contenu(), SbomDocument.Format.INCONNU));
        assertNotEquals(document, "texte");
        assertEquals("SbomDocument[CycloneDX JSON, " + document.contenu().length + " octets]", document.toString());
        assertEquals("format inconnu", SbomDocument.Format.INCONNU.libelle());
    }

    private static SbomDocument.Format format(String contenu) {
        return SbomDocument.reconnaitre(contenu.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpHeaders entetes(Map<String, String> valeurs) {
        Map<String, List<String>> listes = new HashMap<>();
        valeurs.forEach((nom, valeur) -> listes.put(nom, List.of(valeur)));
        return HttpHeaders.of(listes, (nom, valeur) -> true);
    }
}
