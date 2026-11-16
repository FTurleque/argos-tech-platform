package io.argos.alerting;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rang d'une gravité déclarée par la source : {@code LOW} 1, {@code MODERATE} ou {@code MEDIUM} 2,
 * {@code HIGH} 3, {@code CRITICAL} 4. Une gravité absente ou non graduée (vecteur CVSS brut) vaut 0
 * et n'atteint aucun seuil.
 */
public final class Gravite {

    private Gravite() {
    }

    /** Gravités graduées reconnues, de la plus faible à la plus forte. */
    public static final List<String> GRADUEES = List.of("LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL");

    /** Vrai si la valeur est une gravité graduée reconnue (casse indifférente). */
    public static boolean reconnue(String gravite) {
        return rang(gravite) > 0;
    }

    /** Gravités déclarées de rang au moins égal au seuil (filtre « gravité » de la recherche, RCH-02). */
    public static Set<String> auMoins(String seuil) {
        int minimum = rang(seuil);
        Set<String> admises = new LinkedHashSet<>();
        for (String g : GRADUEES) {
            if (minimum > 0 && rang(g) >= minimum) {
                admises.add(g);
            }
        }
        return admises;
    }

    public static int rang(String gravite) {
        if (gravite == null) {
            return 0;
        }
        return switch (gravite.strip().toUpperCase(Locale.ROOT)) {
            case "LOW" -> 1;
            case "MODERATE", "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }
}
