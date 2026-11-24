package io.argos.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * EXP-10-S2 et EXP-10-S3 : une ressource de l'IHM est compressée en gzip si le client l'annonce, et servie telle
 * quelle sinon. Ressource factice du classpath de test, servie comme le bundle de l'IHM ({@code /assets/}).
 */
@QuarkusTest
class CompressionReponsesTest {

    private static final String RESSOURCE = "META-INF/resources/assets/essai-compression.js";
    private static final String GZIP = "gzip";
    private static final int STATUT_OK = 200;

    @TestHTTPResource("/assets/essai-compression.js")
    URI bundle;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<byte[]> demander(Optional<String> encodagesAcceptes) throws IOException, InterruptedException {
        HttpRequest.Builder requete = HttpRequest.newBuilder(bundle).GET();
        encodagesAcceptes.ifPresent(encodages -> requete.header("Accept-Encoding", encodages));
        return client.send(requete.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static byte[] original() {
        try (InputStream flux = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESSOURCE)) {
            return flux.readAllBytes();
        } catch (IOException erreur) {
            throw new UncheckedIOException("ressource de test illisible : " + RESSOURCE, erreur);
        }
    }

    @Test
    void ressourceCompresseeSiLeClientAccepteGzip() throws IOException, InterruptedException {
        HttpResponse<byte[]> reponse = demander(Optional.of(GZIP));
        assertEquals(STATUT_OK, reponse.statusCode());
        assertEquals(Optional.of(GZIP), reponse.headers().firstValue("Content-Encoding"));
        assertTrue(reponse.body().length < original().length, "contenu transféré plus petit que l'original");
        try (InputStream decompresse = new GZIPInputStream(new ByteArrayInputStream(reponse.body()))) {
            assertArrayEquals(original(), decompresse.readAllBytes(), "contenu décompressé identique au fichier servi");
        }
    }

    @Test
    void ressourceNonCompresseeSansAcceptEncoding() throws IOException, InterruptedException {
        HttpResponse<byte[]> reponse = demander(Optional.empty());
        assertEquals(STATUT_OK, reponse.statusCode());
        assertTrue(reponse.headers().firstValue("Content-Encoding").isEmpty(), "aucun encodage de contenu");
        assertArrayEquals(original(), reponse.body());
    }
}
