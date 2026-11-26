package io.argos.aigateway.ollama;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.commun.http.ClientsHttp;
import io.argos.commun.http.LectureBornee;

import io.argos.aigateway.FournisseurModele;

/**
 * Fournisseur local Ollama (licence MIT) : API HTTP {@code POST /api/chat}, sans clé ni coût (Q-1).
 *
 * <p>L'adresse vient de la configuration d'exploitation ({@code argos.ia.ollama.url}, variable
 * {@code ARGOS_OLLAMA_URL}, par défaut {@code http://localhost:11434}), jamais d'une entrée utilisateur ;
 * seuls les schémas {@code http} et {@code https} sont admis, sans identifiants dans l'adresse. Aucune
 * redirection n'est suivie et aucun mandataire n'est utilisé ; la réponse est bornée en taille et en
 * durée. La sortie est demandée au format JSON contraint par le schéma de la tâche, température nulle.
 */
@ApplicationScoped
public class FournisseurOllama implements FournisseurModele {

    private static final int TAILLE_MAX_REPONSE = 1024 * 1024;

    /** Délai de connexion maximal au service local, plus court que le délai d'une demande. */
    private static final Duration CONNEXION_MAX = Duration.ofSeconds(5);

    @ConfigProperty(name = "argos.ia.ollama.url")
    String url;

    @ConfigProperty(name = "argos.ia.delai")
    Duration delai;

    @Inject
    ObjectMapper json;

    private HttpClient client;
    private URI chat;

    @PostConstruct
    void initialiser() {
        URI base = URI.create(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalStateException("argos.ia.ollama.url invalide : adresse http(s) sans identifiants attendue");
        }
        chat = URI.create(base + "/api/chat");
        client = ClientsHttp.clientSansProxy(delai.compareTo(CONNEXION_MAX) < 0 ? delai : CONNEXION_MAX);
    }

    @Override
    public String nom() {
        return "OLLAMA";
    }

    @Override
    public Reponse executer(Requete demande) throws IOException, InterruptedException {
        Map<String, Object> corps = Map.of(
                "model", demande.modele(),
                "stream", false,
                "format", demande.schema(),
                "options", Map.of("temperature", 0),
                "messages", List.of(
                        Map.of("role", "system", "content", demande.consignes()),
                        Map.of("role", "user", "content", demande.message())));
        HttpRequest requete = ClientsHttp.requete(chat, delai)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corps), StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> reponse = client.send(requete, HttpResponse.BodyHandlers.ofInputStream());
        byte[] octets;
        try (InputStream flux = reponse.body()) {
            if (reponse.statusCode() != 200) {
                throw new IOException("Ollama a répondu " + reponse.statusCode());
            }
            octets = LectureBornee.octets(flux, TAILLE_MAX_REPONSE);
        }
        JsonNode racine = json.readTree(octets);
        JsonNode contenu = racine.path("message").path("content");
        if (!contenu.isTextual()) {
            throw new IOException("réponse d'Ollama sans message");
        }
        return new Reponse(contenu.asText(), racine.path("model").asText(demande.modele()), racine.path("prompt_eval_count").asInt(0),
                racine.path("eval_count").asInt(0), BigDecimal.ZERO);
    }
}
