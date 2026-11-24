package io.argos.ingestion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Client HTTP des sources d'avis : délai maximal, classification des erreurs et lecture de
 * {@code Retry-After} (COL-06, arc42 §8.5). Aucune URL issue d'un avis n'est suivie (R-12) :
 * seules les URL de base configurées sont appelées et les redirections sont refusées.
 */
@ApplicationScoped
public class HttpSourceClient {

    private static final Logger LOG = Logger.getLogger(HttpSourceClient.class);

    private final HttpClient client;
    private final Duration timeout;

    @Inject
    public HttpSourceClient(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Renvoie le corps d'une réponse 200 ; toute autre réponse lève une {@link SourceException}. */
    public String get(URI uri) {
        HttpResponse<String> response = send(uri);
        int status = response.statusCode();
        if (status == 200) {
            return response.body();
        }
        String message = "HTTP " + status + " pour " + hostOf(uri);
        if (status == 429 || status == 408 || status >= 500) {
            Duration retryAfter = response.headers().firstValue("Retry-After")
                    .flatMap(HttpSourceClient::parseRetryAfter).orElse(null);
            throw new SourceException(SourceException.Kind.TRANSIENT, status, retryAfter, message, null);
        }
        throw new SourceException(SourceException.Kind.PERMANENT, status, null, message, null);
    }

    private HttpResponse<String> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET()
                .header("Accept", "application/json, text/csv, */*").build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException error) {
            throw SourceException.transientError("délai dépassé pour " + hostOf(uri), error);
        } catch (IOException error) {
            throw SourceException.transientError("erreur réseau pour " + hostOf(uri), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw SourceException.transientError("appel interrompu pour " + hostOf(uri), error);
        }
    }

    /** {@code Retry-After} : nombre de secondes ou date HTTP (RFC 9110 §10.2.3). */
    static Optional<Duration> parseRetryAfter(String value) {
        String trimmed = value.trim();
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(trimmed))));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration delay = Duration.between(ZonedDateTime.now(date.getZone()), date);
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
