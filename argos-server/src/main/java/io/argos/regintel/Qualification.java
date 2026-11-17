package io.argos.regintel;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import io.argos.catalog.dictionnaire.DictionnaireMetier.DictionnaireEspace;
import io.argos.catalog.dictionnaire.DictionnaireMetier.Domaine;
import io.argos.catalog.dictionnaire.DictionnaireMetier.Regle;

/**
 * Qualification déterministe d'un texte officiel par le dictionnaire métier d'un espace (REG-02-S1,
 * ADR-0013) : nature du texte, ministère, préfixe NOR, code consolidé modifié, mot-clé du titre. Aucun
 * appel à l'IA : la proposition d'un domaine par l'IA n'intervient que si aucune règle ne s'applique.
 */
public final class Qualification {

    /** Domaine rattaché par une règle, avec le motif affiché à l'expert. */
    public record Rattachement(String domaine, String motif) {
    }

    private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");

    private Qualification() {
    }

    /** Domaines de l'espace rattachés au texte, une fois chacun, avec le motif de la première règle satisfaite. */
    public static List<Rattachement> qualifier(TexteOfficiel texte, DictionnaireEspace dictionnaire) {
        Map<String, Rattachement> rattachements = new LinkedHashMap<>();
        for (Domaine d : dictionnaire.domaines()) {
            for (Regle r : d.regles()) {
                String motif = motif(texte, r);
                if (motif != null) {
                    rattachements.putIfAbsent(d.code(), new Rattachement(d.code(), motif));
                    break;
                }
            }
        }
        return new ArrayList<>(rattachements.values());
    }

    /** Motif si la règle s'applique au texte, sinon {@code null}. */
    static String motif(TexteOfficiel t, Regle r) {
        String v = r.valeur();
        return switch (r.type()) {
            case NATURE -> t.nature() != null && normaliser(t.nature()).equals(normaliser(v)) ? "nature : " + t.nature() : null;
            case MINISTERE -> t.ministere() != null && normaliser(t.ministere()).contains(normaliser(v))
                    ? "ministère : " + t.ministere() : null;
            case NOR -> t.nor() != null && t.nor().toUpperCase(Locale.ROOT).startsWith(v.toUpperCase(Locale.ROOT))
                    ? "NOR : " + t.nor() : null;
            case CODE -> t.codes().stream()
                    .filter(c -> c.identifiant().equalsIgnoreCase(v) || normaliser(c.libelle()).equals(normaliser(v)))
                    .findFirst().map(c -> "code modifié : " + c.libelle()).orElse(null);
            case MOT_CLE -> contientMot(normaliser(t.titre()), normaliser(v)) ? "mot-clé du titre : " + v : null;
        };
    }

    /** Minuscules sans accents, espaces réduits. */
    static String normaliser(String s) {
        String sansAccents = DIACRITIQUES.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return sansAccents.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", " ").strip();
    }

    private static boolean contientMot(String texte, String mot) {
        if (mot.isEmpty()) {
            return false;
        }
        return (" " + texte + " ").contains(" " + mot + " ");
    }
}
