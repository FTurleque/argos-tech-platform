package io.argos.aigateway;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final int ENTIER_MAX = 1000;

    private ValidationSortie() {
    }

    /** Champs validés, ou vide si la sortie n'est pas conforme. */
    static Optional<Map<String, String>> valider(ObjectMapper json, PromptIa prompt, String texte) {
        JsonNode racine;
        try {
            racine = json.readTree(texte);
        } catch (Exception illisible) {
            return Optional.empty();
        }
        if (racine == null || !racine.isObject()) {
            return Optional.empty();
        }
        Map<String, String> champs = new LinkedHashMap<>();
        for (ChampSortie champ : prompt.champs()) {
            JsonNode noeud = racine.get(champ.nom());
            if (noeud == null || noeud.isNull()) {
                if (champ.obligatoire()) {
                    return Optional.empty();
                }
                continue;
            }
            Optional<String> valeur = switch (champ.type()) {
                case TEXTE -> texte(noeud, champ.longueurMax());
                case DATE -> date(noeud);
                case ENTIERS -> entiers(noeud, champ.longueurMax());
            };
            if (valeur.isEmpty()) {
                return Optional.empty();
            }
            champs.put(champ.nom(), valeur.get());
        }
        return Optional.of(champs);
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
