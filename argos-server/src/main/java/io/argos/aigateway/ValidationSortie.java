package io.argos.aigateway;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.aigateway.PromptIa.ChampSortie;

/**
 * Validation d'une sortie de modèle contre le schéma de sa tâche (IAG-04, QS-15). La sortie doit être
 * un objet JSON ; chaque champ obligatoire est présent, du bon type et dans ses bornes. Les champs non
 * déclarés sont ignorés et les caractères de contrôle retirés : seule une sortie conforme, réduite aux
 * champs du schéma, est rendue au module appelant.
 */
final class ValidationSortie {

    private static final Logger LOG = Logger.getLogger(ValidationSortie.class);
    private static final int ENTIER_MAX = 1000;

    private ValidationSortie() {
    }

    /** Champs validés, ou vide si la sortie n'est pas conforme. */
    static Optional<Map<String, String>> valider(ObjectMapper json, PromptIa prompt, String texte) {
        Optional<JsonNode> racine = lireObjet(json, texte);
        if (racine.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> champs = new LinkedHashMap<>();
        for (ChampSortie champ : prompt.champs()) {
            JsonNode noeud = racine.get().get(champ.nom());
            if (noeud == null || noeud.isNull()) {
                if (champ.obligatoire()) {
                    return Optional.empty();
                }
                continue;
            }
            Optional<String> valeur = valeurDuChamp(champ, noeud);
            if (valeur.isEmpty()) {
                return Optional.empty();
            }
            champs.put(champ.nom(), valeur.get());
        }
        return Optional.of(champs);
    }

    /**
     * Objet JSON racine de la sortie ; vide si elle n'est pas du JSON ou pas un objet. La cause est journalisée sans
     * le texte du modèle, qui peut reprendre des données des entrées.
     */
    private static Optional<JsonNode> lireObjet(ObjectMapper json, String texte) {
        JsonNode racine;
        try {
            racine = json.readTree(texte);
        } catch (JsonProcessingException illisible) {
            JsonLocation position = illisible.getLocation();
            LOG.debugf("sortie de modèle illisible en JSON (%s, ligne %d, colonne %d)", illisible.getClass().getSimpleName(),
                    position == null ? -1 : position.getLineNr(), position == null ? -1 : position.getColumnNr());
            return Optional.empty();
        } catch (IllegalArgumentException absente) {
            LOG.debugf("sortie de modèle absente : %s", absente.getMessage());
            return Optional.empty();
        }
        if (racine == null || !racine.isObject()) {
            LOG.debug("sortie de modèle qui n'est pas un objet JSON");
            return Optional.empty();
        }
        return Optional.of(racine);
    }

    /** Valeur du champ validée selon son type ; vide si elle n'est pas conforme. */
    private static Optional<String> valeurDuChamp(ChampSortie champ, JsonNode noeud) {
        return switch (champ.type()) {
            case TEXTE -> texte(noeud, champ.longueurMax());
            case DATE -> date(noeud);
            case ENTIERS -> entiers(noeud, champ.longueurMax());
        };
    }

    private static Optional<String> texte(JsonNode noeud, int longueurMax) {
        if (!noeud.isTextual()) {
            return Optional.empty();
        }
        String nettoye = noeud.asText().replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").strip();
        return nettoye.isEmpty() || nettoye.length() > longueurMax ? Optional.empty() : Optional.of(nettoye);
    }

    private static Optional<String> date(JsonNode noeud) {
        if (!noeud.isTextual()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(noeud.asText().strip()).toString());
        } catch (DateTimeParseException invalide) {
            LOG.debugf("date de sortie non conforme au format AAAA-MM-JJ (position %d)", invalide.getErrorIndex());
            return Optional.empty();
        }
    }

    private static Optional<String> entiers(JsonNode noeud, int nombreMax) {
        if (!noeud.isArray() || noeud.size() > nombreMax) {
            return Optional.empty();
        }
        List<String> valeurs = new ArrayList<>();
        for (JsonNode element : noeud) {
            if (!element.isInt() || element.asInt() < 1 || element.asInt() > ENTIER_MAX) {
                return Optional.empty();
            }
            valeurs.add(Integer.toString(element.asInt()));
        }
        return Optional.of(String.join(",", valeurs));
    }
}
