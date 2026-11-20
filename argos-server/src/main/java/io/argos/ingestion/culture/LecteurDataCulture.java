package io.argos.ingestion.culture;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;

/**
 * Lecture d'une réponse de l'API d'un portail Opendatasoft, celui de data.culture.gouv.fr (Licence
 * Ouverte Etalab, Q-1) : point d'accès {@code /api/explore/v2.1/catalog/datasets/<jeu>/records}, corps
 * {@code {"total_count": n, "results": [ {champ: valeur, ...}, ... ]}}.
 *
 * <p>Le nom des champs lus est fourni par la configuration ({@link Champs}) : chaque jeu de données a
 * ses propres noms. Chaque valeur est un contenu externe non fiable : elle est réduite à du texte brut
 * (balises, scripts et styles retirés) et tronquée. Une valeur de discipline ou de territoire peut être
 * une chaîne ou une liste de chaînes ; elle est rendue telle quelle, la correspondance avec les listes
 * du profil relevant de l'appelant. Aucune adresse lue dans le contenu n'est jamais récupérée.
 */
public final class LecteurDataCulture {

    /** Noms des champs d'un enregistrement. */
    public record Champs(String identifiant, String titre, String organisme, String criteres, String lien,
            String discipline, String territoire, String dateLimite) {
    }

    /** Enregistrement lu, en texte brut ; seuls l'identifiant et le titre sont toujours présents. */
    public record Enregistrement(String identifiant, String titre, String organisme, String criteres, String lien,
            List<String> disciplines, List<String> territoires, LocalDate dateLimite) {
    }

    /** Contenu qui n'est pas une réponse attendue de l'API. */
    public static class ReponseInvalide extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ReponseInvalide(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final int TITRE_MAX = 1000;
    static final int ORGANISME_MAX = 300;
    static final int CRITERES_MAX = 20_000;
    static final int VALEUR_MAX = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private LecteurDataCulture() {
    }

    /** Lit au plus {@code max} enregistrements ; ceux sans identifiant ni titre sont ignorés. */
    public static List<Enregistrement> lire(byte[] contenu, Champs champs, int max) {
        JsonNode racine;
        try {
            racine = MAPPER.readTree(contenu);
        } catch (IOException erreur) {
            throw new ReponseInvalide("La réponse n'est pas un document JSON valide.", erreur);
        }
        JsonNode resultats = racine == null ? null : racine.get("results");
        if (resultats == null || !resultats.isArray()) {
            throw new ReponseInvalide("La réponse ne contient pas de liste « results ».", null);
        }
        List<Enregistrement> lus = new ArrayList<>();
        for (JsonNode noeud : resultats) {
            if (lus.size() >= max) {
                break;
            }
            if (!noeud.isObject()) {
                continue;
            }
            String titre = couper(texte(noeud.get(champs.titre())), TITRE_MAX);
            String identifiant = couper(texte(noeud.get(champs.identifiant())), VALEUR_MAX);
            if (titre.isEmpty()) {
                continue;
            }
            if (identifiant.isEmpty()) {
                identifiant = titre;
            }
            lus.add(new Enregistrement(identifiant, titre,
                    vide(couper(texte(noeud.get(champs.organisme())), ORGANISME_MAX)),
                    couper(texte(noeud.get(champs.criteres())), CRITERES_MAX),
                    vide(couper(texte(noeud.get(champs.lien())), 2000)),
                    valeurs(noeud.get(champs.discipline())), valeurs(noeud.get(champs.territoire())),
                    date(texte(noeud.get(champs.dateLimite())))));
        }
        return lus;
    }

    /** Texte brut d'une valeur scalaire : balises retirées, espaces normalisés. */
    static String texte(JsonNode valeur) {
        if (valeur == null || valeur.isNull() || valeur.isContainerNode()) {
            return "";
        }
        return Jsoup.parse(valeur.asText()).text().strip();
    }

    private static List<String> valeurs(JsonNode valeur) {
        List<String> resultat = new ArrayList<>();
        if (valeur == null || valeur.isNull()) {
            return resultat;
        }
        if (valeur.isArray()) {
            for (JsonNode element : valeur) {
                String valeurCoupee = couper(texte(element), VALEUR_MAX);
                if (!valeurCoupee.isEmpty() && resultat.size() < 50) {
                    resultat.add(valeurCoupee);
                }
            }
            return resultat;
        }
        // Valeurs multiples séparées par « ; » ou « , » dans une même chaîne.
        for (String partie : texte(valeur).split("[;,]")) {
            String valeurCoupee = couper(partie.strip(), VALEUR_MAX);
            if (!valeurCoupee.isEmpty() && resultat.size() < 50) {
                resultat.add(valeurCoupee);
            }
        }
        return resultat;
    }

    /** Date limite : {@code AAAA-MM-JJ} ou horodatage ISO-8601 ; absente si illisible (« date à confirmer »). */
    static LocalDate date(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return valeur.length() <= 10 ? LocalDate.parse(valeur) : OffsetDateTime.parse(valeur).toLocalDate();
        } catch (DateTimeParseException horsFormatIso) {
            try {
                return LocalDate.parse(valeur.substring(0, 10));
            } catch (DateTimeParseException | StringIndexOutOfBoundsException illisible) {
                return null;
            }
        }
    }

    private static String couper(String valeur, int max) {
        return valeur.length() <= max ? valeur : valeur.substring(0, max);
    }

    private static String vide(String valeur) {
        return valeur.isEmpty() ? null : valeur;
    }
}
