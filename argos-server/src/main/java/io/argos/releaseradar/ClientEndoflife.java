package io.argos.releaseradar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.commun.http.ClientsHttp;
import io.argos.commun.http.LectureBornee;
import io.argos.commun.http.ReponseTropVolumineuse;

/**
 * Connecteur de l'API publique et gratuite endoflife.date (Q-2), version 1 :
 * {@code GET <base>/api/v1/products/<produit>}.
 *
 * <p>Protections : l'adresse de base vient de la configuration, jamais d'une saisie ; l'identifiant
 * de produit est contrôlé ({@link #PRODUIT}) puis encodé ; aucune redirection n'est suivie ; la durée
 * et la taille de la réponse sont bornées ; la réponse est traitée comme une donnée non fiable
 * (chaque cycle mal formé est ignoré, les textes sont tronqués).
 */
@ApplicationScoped
public class ClientEndoflife {

    /** Identifiant de produit accepté : celui de la colonne {@code produit_cycle_vie} (V12). */
    public static final Pattern PRODUIT = Pattern.compile("^[a-z0-9][a-z0-9._+-]{0,99}$");

    /** Cycle de vie d'une branche d'un produit, tel que publié par la source. */
    public record Cycle(String cycle, LocalDate sortie, LocalDate finSupport, boolean enFinSupport,
            String derniereVersion, LocalDate derniereVersionLe) {
    }

    /** Source indisponible ou réponse inexploitable. */
    public static class SourceIndisponible extends Exception {

        private static final long serialVersionUID = 1L;

        public SourceIndisponible(String message) {
            super(message);
        }

        public SourceIndisponible(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final int CYCLES_MAX = 500;
    private static final ObjectMapper JSON = new ObjectMapper();

    @ConfigProperty(name = "argos.radar.endoflife.url")
    String base;

    @ConfigProperty(name = "argos.radar.endoflife.delai")
    Duration delai;

    @ConfigProperty(name = "argos.radar.endoflife.taille-max-octets")
    int tailleMax;

    private HttpClient client;

    @PostConstruct
    void initialiser() {
        client = ClientsHttp.client(delai);
    }

    /** Cycles publiés pour le produit. */
    public List<Cycle> cycles(String produit) throws SourceIndisponible {
        if (produit == null || !PRODUIT.matcher(produit).matches()) {
            throw new SourceIndisponible("identifiant de produit invalide");
        }
        URI uri = URI.create(base.replaceAll("/+$", "") + "/api/v1/products/"
                + URLEncoder.encode(produit, StandardCharsets.UTF_8));
        HttpRequest requete = ClientsHttp.requete(uri, delai).header("Accept", "application/json").build();
        byte[] corps;
        try {
            HttpResponse<InputStream> reponse = client.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream flux = reponse.body()) {
                if (reponse.statusCode() == 404) {
                    throw new SourceIndisponible("produit inconnu d'endoflife.date");
                }
                if (reponse.statusCode() != 200) {
                    throw new SourceIndisponible("réponse HTTP " + reponse.statusCode());
                }
                corps = LectureBornee.octets(flux, tailleMax);
            }
        } catch (ReponseTropVolumineuse depassement) {
            throw new SourceIndisponible("réponse trop volumineuse", depassement);
        } catch (IOException injoignable) {
            throw new SourceIndisponible("source injoignable : " + injoignable.getClass().getSimpleName());
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new SourceIndisponible("synchronisation interrompue");
        }
        return lire(corps);
    }

    /** Lecture tolérante de la réponse : un cycle mal formé est ignoré, pas la réponse entière. */
    static List<Cycle> lire(byte[] corps) throws SourceIndisponible {
        JsonNode racine;
        try {
            racine = JSON.readTree(corps);
        } catch (IOException illisible) {
            throw new SourceIndisponible("réponse illisible");
        }
        JsonNode releases = racine == null ? null : racine.path("result").path("releases");
        if (releases == null || !releases.isArray()) {
            throw new SourceIndisponible("réponse sans liste de cycles");
        }
        List<Cycle> cycles = new ArrayList<>();
        for (JsonNode cycle : releases) {
            if (cycles.size() >= CYCLES_MAX) {
                break;
            }
            String nom = texte(cycle.path("name"), 100);
            if (nom == null) {
                continue;
            }
            JsonNode latest = cycle.path("latest");
            LocalDate fin = date(cycle.path("eolFrom"));
            boolean enFin = cycle.path("isEol").asBoolean(false);
            cycles.add(new Cycle(nom, date(cycle.path("releaseDate")), fin, enFin, texte(latest.path("name"), 100),
                    date(latest.path("date"))));
        }
        return cycles;
    }

    private static String texte(JsonNode noeud, int longueurMax) {
        if (noeud == null || !noeud.isTextual() || noeud.asText().isBlank()) {
            return null;
        }
        String nettoye = noeud.asText().strip().replaceAll("\\p{Cntrl}", "");
        return nettoye.length() > longueurMax ? null : nettoye;
    }

    private static LocalDate date(JsonNode noeud) {
        String texteDate = texte(noeud, 10);
        if (texteDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(texteDate);
        } catch (DateTimeParseException invalide) {
            return null;
        }
    }
}
