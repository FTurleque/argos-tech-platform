package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.argos.ingestion.AdvisoryRejectedException;
import io.argos.ingestion.NormalizedAdvisory;
import io.argos.ingestion.RawAdvisory;
import io.argos.ingestion.osv.OsvNormalizer;
import io.argos.inventory.CycloneDxParser;
import io.argos.inventory.InvalidDocumentException;
import io.argos.inventory.ManifestParser;
import io.argos.inventory.ParsedInventory;
import io.argos.testsupport.Fixtures;

/**
 * Contrat des documents externes : avis OSV réel enregistré (GHSA-jfh8-c2jp-5v3q), SBOM CycloneDX,
 * manifestes (tâches 2.2, 3.3).
 */
class DocumentParsingTest {

    @Test
    void contratOsvSurReponseEnregistree() {
        String raw = Fixtures.resource("osv/GHSA-jfh8-c2jp-5v3q.json");
        NormalizedAdvisory a = OsvNormalizer.normalize(new RawAdvisory("GHSA-jfh8-c2jp-5v3q", raw));
        assertEquals("GHSA-jfh8-c2jp-5v3q", a.externalId());
        assertEquals(List.of("CVE-2021-44228"), a.aliases());
        assertEquals("CRITICAL", a.severity());
        assertEquals(Instant.parse("2025-10-22T19:37:02.616807Z"), a.modifiedAt());
        NormalizedAdvisory.Affected core = a.affected().get(0);
        assertEquals("pkg:maven/org.apache.logging.log4j/log4j-core", core.purl());
        assertEquals(new NormalizedAdvisory.Range("ECOSYSTEM", "2.13.0", "2.15.0", null), core.ranges().get(0));
        assertTrue(a.affected().stream().anyMatch(x -> x.ranges().stream().anyMatch(r -> "1.2.1.2-jre17".equals(r.lastAffected()))));
        assertEquals(64, a.contentHash().length());
        assertEquals(raw, a.rawContent());
    }

    @Test
    void col02S2AvisSansIdentifiantRejete() {
        AdvisoryRejectedException e = assertThrows(AdvisoryRejectedException.class,
                () -> OsvNormalizer.normalize(new RawAdvisory("X", "{\"modified\":\"2026-01-01T00:00:00Z\"}")));
        assertEquals("identifiant externe absent", e.getMessage());
        assertThrows(AdvisoryRejectedException.class, () -> OsvNormalizer.normalize(new RawAdvisory("X", "{pas du json")));
    }

    @Test
    void sbomValideAvecTransitivite() {
        String sbom = Fixtures.cyclonedx("1.6", "2026-11-10T08:00:00Z", List.of(
                new String[] {"a", "pkg:maven/org.exemple/a@1.0.0"},
                new String[] {"b", "pkg:maven/org.exemple/b@2.1.0"},
                new String[] {"nopurl", "pas-un-purl"}), List.of("a"));
        ParsedInventory inv = CycloneDxParser.parse(sbom.getBytes(StandardCharsets.UTF_8), 1_000_000);
        assertEquals("1.6", inv.specVersion());
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), inv.sourceDate());
        assertEquals(2, inv.components().size());
        assertEquals(1, inv.ignoredComponents());
        assertEquals(List.of(new ParsedInventory.IgnoredComponent("nopurl", null, null, ParsedInventory.IgnoredComponent.PURL_INVALIDE)),
                inv.ignored());
        ParsedInventory.Item a = inv.components().get(0);
        assertEquals("pkg:maven/org.exemple/a", a.packageKey());
        assertEquals("1.0.0", a.version());
        assertFalse(a.transitive());
        assertTrue(inv.components().get(1).transitive());
    }

    /** INV-01, décision D6 (2026-11-18) : composants sans PURL écartés et signalés, liste bornée. */
    @Test
    void composantsSansPurlSignalesEtListeBornee() {
        StringBuilder comps = new StringBuilder(
                "{\"type\":\"library\",\"group\":\"org.apache.logging.log4j\",\"name\":\"log4j-core\",\"version\":\"2.14.1\","
                        + "\"cpe\":\"cpe:2.3:a:apache:log4j:2.14.1:*:*:*:*:*:*:*\"},"
                        + "{\"type\":\"library\",\"name\":\"" + "n".repeat(300) + "\",\"purl\":\"  \"}");
        for (int i = 0; i < 150; i++) {
            comps.append(",{\"type\":\"library\",\"name\":\"lib").append(i).append("\"}");
        }
        comps.append(",{\"type\":\"library\",\"name\":\"ok\",\"purl\":\"pkg:maven/org.exemple/ok@1.0\"}");
        String sbom = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"components\":[" + comps + "]}";
        ParsedInventory inv = CycloneDxParser.parse(sbom.getBytes(StandardCharsets.UTF_8), 1_000_000);
        assertEquals(1, inv.components().size());
        assertEquals(152, inv.ignoredComponents(), "le nombre total compte tous les composants écartés");
        assertEquals(ParsedInventory.MAX_SIGNALED, inv.ignored().size(), "la description est bornée");
        assertEquals(new ParsedInventory.IgnoredComponent("log4j-core", "org.apache.logging.log4j", "2.14.1",
                ParsedInventory.IgnoredComponent.SANS_PURL), inv.ignored().get(0));
        assertEquals(200, inv.ignored().get(1).name().length(), "nom borné à 200 caractères");
        assertEquals(ParsedInventory.IgnoredComponent.SANS_PURL, inv.ignored().get(1).reason(), "PURL vide : sans PURL");
        assertEquals("lib97", inv.ignored().get(99).name());
    }

    @Test
    void sbomNonConformeRefuseeAvecMotif() {
        String v12 = Fixtures.cyclonedx("1.2", null, List.of(), List.of());
        InvalidDocumentException e = assertThrows(InvalidDocumentException.class,
                () -> CycloneDxParser.parse(v12.getBytes(StandardCharsets.UTF_8), 1_000_000));
        assertTrue(e.getMessage().contains("1.2"));
        assertThrows(InvalidDocumentException.class,
                () -> CycloneDxParser.parse("{\"bomFormat\":\"SPDX\"}".getBytes(StandardCharsets.UTF_8), 1_000_000));
        assertThrows(InvalidDocumentException.class, () -> CycloneDxParser.parse("<xml/>".getBytes(StandardCharsets.UTF_8), 1_000_000));
        assertThrows(InvalidDocumentException.class,
                () -> CycloneDxParser.parse(Fixtures.cyclonedx("1.5", null, List.of(), List.of()).getBytes(StandardCharsets.UTF_8), 10));
    }

    @Test
    void manifestes() {
        List<ParsedInventory.Item> npm = ManifestParser.parse("front/package.json",
                "{\"dependencies\":{\"lodash\":\"4.17.20\",\"@angular/core\":\"^17.0.0\"}}".getBytes(StandardCharsets.UTF_8));
        assertEquals("pkg:npm/lodash", npm.get(0).packageKey());
        assertEquals("4.17.20", npm.get(0).version());
        assertEquals("pkg:npm/@angular/core", npm.get(1).packageKey());
        assertNull(npm.get(1).version());
        List<ParsedInventory.Item> py = ManifestParser.parse("requirements.txt",
                "# commentaire\nDjango==4.2.1\nrequests>=2.0\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("pkg:pypi/django", py.get(0).packageKey());
        assertEquals("4.2.1", py.get(0).version());
        assertNull(py.get(1).version());
    }
}
