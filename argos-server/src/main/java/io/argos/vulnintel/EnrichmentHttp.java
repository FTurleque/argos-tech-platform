package io.argos.vulnintel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.commun.http.ClientsHttp;
import io.argos.commun.http.LectureBornee;
import io.argos.commun.http.ReponseTropVolumineuse;

/**
 * Appels HTTP des sources d'enrichissement : seules les URL de base configurées sont appelées,
 * sans suivre de redirection (R-12), corps lu sous {@code argos.sources.taille-max-octets} (AUD-PER-11). Toute
 * réponse autre que 200 ou 404, ou trop volumineuse, rend la source indisponible pour ce cycle.
 */
@ApplicationScoped
public class EnrichmentHttp {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final Duration timeout;

    private final long tailleMax;

    @Inject
    public EnrichmentHttp(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout,
            @ConfigProperty(name = "argos.sources.taille-max-octets") long tailleMax) {
        this.timeout = timeout;
        this.tailleMax = tailleMax;
        this.client = ClientsHttp.client(timeout);
    }

    /** Corps de la réponse 200, au plus la taille maximale, ou vide pour 404. */
    public Optional<String> get(URI uri) {
        try {
            HttpResponse<InputStream> response = client.send(ClientsHttp.requete(uri, timeout).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream corps = response.body()) {
                if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                if (response.statusCode() != 200) {
                    throw new EnrichmentUnavailableException("HTTP " + response.statusCode() + " pour " + uri.getHost(), null);
                }
                return Optional.of(new String(LectureBornee.octets(corps, tailleMax), StandardCharsets.UTF_8));
            }
        } catch (ReponseTropVolumineuse depassement) {
            throw new EnrichmentUnavailableException("réponse de " + uri.getHost() + " au-delà de " + depassement.tailleMax()
                    + " octets", depassement);
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
