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
        for (Domaine domaine : dictionnaire.domaines()) {
            for (Regle regle : domaine.regles()) {
                String motif = motif(texte, regle);
                if (motif != null) {
                    rattachements.putIfAbsent(domaine.code(), new Rattachement(domaine.code(), motif));
                    break;
                }
            }
        }
        return new ArrayList<>(rattachements.values());
    }

    /** Motif si la règle s'applique au texte, sinon {@code null}. */
    static String motif(TexteOfficiel texte, Regle regle) {
        String valeur = regle.valeur();
        return switch (regle.type()) {
            case NATURE -> texte.nature() != null && normaliser(texte.nature()).equals(normaliser(valeur))
                    ? "nature : " + texte.nature() : null;
            case MINISTERE -> texte.ministere() != null && normaliser(texte.ministere()).contains(normaliser(valeur))
                    ? "ministère : " + texte.ministere() : null;
            case NOR -> texte.nor() != null && texte.nor().toUpperCase(Locale.ROOT).startsWith(valeur.toUpperCase(Locale.ROOT))
                    ? "NOR : " + texte.nor() : null;
            case CODE -> texte.codes().stream()
                    .filter(codeModifie -> codeModifie.identifiant().equalsIgnoreCase(valeur)
                            || normaliser(codeModifie.libelle()).equals(normaliser(valeur)))
                    .findFirst().map(codeModifie -> "code modifié : " + codeModifie.libelle()).orElse(null);
            case MOT_CLE -> contientMot(normaliser(texte.titre()), normaliser(valeur)) ? "mot-clé du titre : " + valeur : null;
        };
    }

    /** Minuscules sans accents, espaces réduits. */
    static String normaliser(String texte) {
        String sansAccents = DIACRITIQUES.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("");
        return sansAccents.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", " ").strip();
    }

    private static boolean contientMot(String texte, String mot) {
        if (mot.isEmpty()) {
            return false;
        }
        return (" " + texte + " ").contains(" " + mot + " ");
    }
}
