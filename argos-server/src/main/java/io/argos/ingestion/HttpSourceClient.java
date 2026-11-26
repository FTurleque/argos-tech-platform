package io.argos.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.commun.http.ClientsHttp;
import io.argos.commun.http.LectureBornee;
import io.argos.commun.http.ReponseTropVolumineuse;

/**
 * Client HTTP des sources d'avis : délai maximal, classification des erreurs, lecture de {@code Retry-After}
 * (COL-06, arc42 §8.5) et lecture bornée du corps ({@code argos.sources.taille-max-octets}, AUD-PER-11). Aucune URL
 * issue d'un avis n'est suivie (R-12) : seules les URL de base configurées sont appelées et les redirections sont
 * refusées ({@link ClientsHttp}).
 */
@ApplicationScoped
public class HttpSourceClient {

    private static final Logger LOG = Logger.getLogger(HttpSourceClient.class);

    /** Lecture d'un corps de réponse 200. */
    @FunctionalInterface
    private interface LectureCorps<T> {
        T lire(InputStream corps) throws IOException;
    }

    private final HttpClient client;
    private final Duration timeout;
    private final long tailleMax;

    @Inject
    Clock horloge;

    @Inject
    public HttpSourceClient(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout,
            @ConfigProperty(name = "argos.sources.taille-max-octets") long tailleMax) {
        this.timeout = timeout;
        this.tailleMax = tailleMax;
        this.client = ClientsHttp.client(timeout);
    }

    /** Renvoie le corps d'une réponse 200, au plus la taille maximale ; toute autre réponse lève une {@link SourceException}. */
    public String telecharger(URI uri) {
        return recevoir(uri, corps -> new String(LectureBornee.octets(corps, tailleMax), StandardCharsets.UTF_8));
    }

    /**
     * Lit le corps d'une réponse 200 ligne à ligne, sans le garder en mémoire, au plus la taille maximale ; renvoie le
     * nombre de lignes lues.
     */
    public long lignes(URI uri, Consumer<String> consommateur) {
        return recevoir(uri, corps -> LectureBornee.lignes(corps, tailleMax, consommateur));
    }

    /** Copie le corps d'une réponse 200 dans {@code cible}, au plus {@code tailleMaxFichier} octets ; renvoie la taille copiée. */
    public long telechargerVers(URI uri, Path cible, long tailleMaxFichier) {
        return recevoir(uri, corps -> LectureBornee.copier(corps, cible, tailleMaxFichier));
    }

    private <T> T recevoir(URI uri, LectureCorps<T> lecture) {
        HttpRequest request = ClientsHttp.requete(uri, timeout).header("Accept", "application/json, text/csv, */*").build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream corps = response.body()) {
                verifierStatut(uri, response);
                return lecture.lire(corps);
            }
        } catch (ReponseTropVolumineuse depassement) {
            throw new SourceException(SourceException.Kind.PERMANENT, null, null,
                    "réponse de " + hostOf(uri) + " au-delà de " + depassement.tailleMax() + " octets", depassement);
        } catch (HttpTimeoutException error) {
            throw SourceException.transientError("délai dépassé pour " + hostOf(uri), error);
        } catch (IOException error) {
            throw SourceException.transientError("erreur réseau pour " + hostOf(uri), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw SourceException.transientError("appel interrompu pour " + hostOf(uri), error);
        }
    }

    /** Toute réponse autre que 200 lève une {@link SourceException} transitoire (429, 408, 5xx) ou permanente. */
    private void verifierStatut(URI uri, HttpResponse<?> response) {
        int status = response.statusCode();
        if (status == 200) {
            return;
        }
        String message = "HTTP " + status + " pour " + hostOf(uri);
        if (status == 429 || status == 408 || status >= 500) {
            Duration retryAfter = response.headers().firstValue("Retry-After")
                    .flatMap(valeur -> parseRetryAfter(valeur, horloge.instant())).orElse(null);
            throw new SourceException(SourceException.Kind.TRANSIENT, status, retryAfter, message, null);
        }
        throw new SourceException(SourceException.Kind.PERMANENT, status, null, message, null);
    }

    /** {@code Retry-After} : nombre de secondes ou date HTTP (RFC 9110 §10.2.3), lue à l'instant {@code maintenant}. */
    static Optional<Duration> parseRetryAfter(String value, Instant maintenant) {
        String trimmed = value.trim();
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(trimmed))));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration delay = Duration.between(maintenant, date.toInstant());
                return Optional.of(delay.isNegative() ? Duration.ZERO : delay);
            } catch (DateTimeParseException notDate) {
                LOG.debugf(notDate, "en-tête Retry-After ignorée : ni nombre de secondes ni date HTTP (%s)", notDate.getMessage());
                return Optional.empty();
            }
        }
    }

    private static String hostOf(URI uri) {
        return uri.getHost() == null ? "source" : uri.getHost();
    }
}
