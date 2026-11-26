package io.argos.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.osv.OsvAdvisoryFeed;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Lecture OSV par archive d'écosystème (AUD-PER-01, design D8 du changement {@code rendre-ingestion-asynchrone}) et
 * lecture bornée de l'index (AUD-PER-11). Le connecteur est construit avec un seuil d'une seule entrée pour que
 * l'archive serve dès deux avis.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class OsvArchiveTest {

    private static final String INDEX = "/osv-export/Maven/modified_id.csv";
    private static final String ARCHIVE = "/osv-export/Maven/all.zip";
    private static final String API = "/osv-api/v1/vulns/";
    private static final long ARCHIVE_MAX = 1_000_000;
    private static final Duration DELAI = Duration.ofSeconds(1);
    private static final long TAILLE_MINUSCULE = 8;

    @Inject
    HttpSourceClient http;

    @ConfigProperty(name = "argos.sources.osv.export-url")
    String exportUrl;

    @ConfigProperty(name = "argos.sources.osv.api-url")
    String apiUrl;

    @BeforeEach
    void reset() {
        StubServer.reset();
        StubServer.programmer(INDEX, Stub.succes("""
                2026-11-20T00:00:00Z,GHSA-A1
                2026-11-21T00:00:00Z,GHSA-A2
                2026-11-22T00:00:00Z,GHSA-A3
                """));
    }

    private OsvAdvisoryFeed feed(int seuil) {
        return new OsvAdvisoryFeed(http, exportUrl, apiUrl, List.of("Maven"), seuil, ARCHIVE_MAX);
    }

    @Test
    void auDelaDuSeuilLesAvisSontLusDansLArchiveEtLApiSertDeRepli() throws IOException {
        StubServer.programmer(ARCHIVE, Stub.octets(zip(Map.of("GHSA-A1.json", "{\"id\":\"GHSA-A1\"}", "GHSA-A2.json",
                "{\"id\":\"GHSA-A2\"}"))));
        StubServer.programmer(API + "GHSA-A3", Stub.succes("{\"id\":\"GHSA-A3\"}"));

        try (AdvisoryFeed.Lecture lecture = feed(1).lire(null)) {
            List<FeedEntry> entrees = lecture.entrees();
            assertEquals(List.of("GHSA-A1", "GHSA-A2", "GHSA-A3"), entrees.stream().map(FeedEntry::externalId).toList());
            assertEquals(List.of("{\"id\":\"GHSA-A1\"}", "{\"id\":\"GHSA-A2\"}", "{\"id\":\"GHSA-A3\"}"),
                    entrees.stream().map(entree -> lecture.avis(entree).content()).toList());
        }

        assertEquals(List.of(API + "GHSA-A3"), StubServer.received(API).stream().map(StubServer.Received::pathAndQuery).toList(),
                "seul l'avis absent de l'archive est lu par l'API");
        assertEquals(1, StubServer.received(ARCHIVE).size());
    }

    @Test
    void sousLeSeuilLArchiveNEstPasTelechargee() {
        StubServer.programmer(API + "GHSA-A3", Stub.succes("{\"id\":\"GHSA-A3\"}"));

        try (AdvisoryFeed.Lecture lecture = feed(1000).lire("2026-11-21T00:00:00Z|GHSA-A2")) {
            assertEquals("{\"id\":\"GHSA-A3\"}", lecture.avis(lecture.entrees().get(0)).content());
        }

        assertTrue(StubServer.received(ARCHIVE).isEmpty());
    }

    @Test
    void uneArchiveIndisponibleLaisseLApiLireLesAvis() {
        StubServer.programmer(API + "GHSA-A1", Stub.succes("{\"id\":\"GHSA-A1\"}"));

        try (AdvisoryFeed.Lecture lecture = feed(1).lire(null)) {
            assertEquals("{\"id\":\"GHSA-A1\"}", lecture.avis(lecture.entrees().get(0)).content());
        }
    }

    @Test
    void uneReponseAuDelaDeLaTailleMaximaleEstRefuseeCommeErreurPermanente() {
        HttpSourceClient borne = new HttpSourceClient(DELAI, TAILLE_MINUSCULE);
        SourceException refus = assertThrows(SourceException.class,
                () -> borne.telecharger(URI.create(StubServer.base() + INDEX)));
        assertEquals(SourceException.Kind.PERMANENT, refus.kind());
    }

    private static byte[] zip(Map<String, String> fichiers) throws IOException {
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(octets)) {
            for (Map.Entry<String, String> fichier : fichiers.entrySet()) {
                archive.putNextEntry(new ZipEntry(fichier.getKey()));
                archive.write(fichier.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return octets.toByteArray();
    }
}
