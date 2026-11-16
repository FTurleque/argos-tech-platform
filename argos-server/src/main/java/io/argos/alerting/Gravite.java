package io.argos.alerting;

import java.util.Locale;

/**
 * Rang d'une gravité déclarée par la source : {@code LOW} 1, {@code MODERATE} ou {@code MEDIUM} 2,
 * {@code HIGH} 3, {@code CRITICAL} 4. Une gravité absente ou non graduée (vecteur CVSS brut) vaut 0
 * et n'atteint aucun seuil.
 */
public final class Gravite {

    private Gravite() {
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
