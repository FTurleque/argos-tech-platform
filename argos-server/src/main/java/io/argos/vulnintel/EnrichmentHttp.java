package io.argos.vulnintel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Appels HTTP des sources d'enrichissement : seules les URL de base configurées sont appelées,
 * sans suivre de redirection (R-12). Toute réponse autre que 200 ou 404 rend la source
 * indisponible pour ce cycle.
 */
@ApplicationScoped
public class EnrichmentHttp {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final Duration timeout;

    @Inject
    public EnrichmentHttp(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Corps de la réponse 200, ou vide pour 404. */
    public Optional<String> get(URI uri) {
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new EnrichmentUnavailableException("HTTP " + response.statusCode() + " pour " + uri.getHost(), null);
            }
            return Optional.of(response.body());
        } catch (IOException error) {
            throw new EnrichmentUnavailableException("erreur réseau pour " + uri.getHost(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new EnrichmentUnavailableException("appel interrompu pour " + uri.getHost(), error);
        }
    }

    public Optional<JsonNode> getJson(URI uri) {
        return get(uri).map(body -> {
            try {
                return MAPPER.readTree(body);
            } catch (JsonProcessingException error) {
                throw new EnrichmentUnavailableException("réponse illisible de " + uri.getHost(), error);
            }
        });
    }
}
