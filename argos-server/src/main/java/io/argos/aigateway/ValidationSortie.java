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
        } catch (Exception e) {
            return Optional.empty();
        }
        if (racine == null || !racine.isObject()) {
            return Optional.empty();
        }
        Map<String, String> champs = new LinkedHashMap<>();
        for (ChampSortie c : prompt.champs()) {
            JsonNode v = racine.get(c.nom());
            if (v == null || v.isNull()) {
                if (c.obligatoire()) {
                    return Optional.empty();
                }
                continue;
            }
            Optional<String> valeur = switch (c.type()) {
                case TEXTE -> texte(v, c.longueurMax());
                case DATE -> date(v);
                case ENTIERS -> entiers(v, c.longueurMax());
            };
            if (valeur.isEmpty()) {
                return Optional.empty();
            }
            champs.put(c.nom(), valeur.get());
        }
        return Optional.of(champs);
    }

    private static Optional<String> texte(JsonNode v, int longueurMax) {
        if (!v.isTextual()) {
            return Optional.empty();
        }
        String t = v.asText().replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").strip();
        return t.isEmpty() || t.length() > longueurMax ? Optional.empty() : Optional.of(t);
    }

    private static Optional<String> date(JsonNode v) {
        if (!v.isTextual()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(v.asText().strip()).toString());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> entiers(JsonNode v, int nombreMax) {
        if (!v.isArray() || v.size() > nombreMax) {
            return Optional.empty();
        }
        List<String> valeurs = new ArrayList<>();
        for (JsonNode e : v) {
            if (!e.isInt() || e.asInt() < 1 || e.asInt() > ENTIER_MAX) {
                return Optional.empty();
            }
            valeurs.add(Integer.toString(e.asInt()));
        }
        return Optional.of(String.join(",", valeurs));
    }
}
