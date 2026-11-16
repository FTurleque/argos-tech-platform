package io.argos.mcp;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Balise et tronque le contenu externe renvoyé aux assistants IA (MCP-05). Longueur maximale :
 * 2 000 caractères par défaut (décision du 2026-11-16, Q-2), paramétrable par
 * {@code argos.mcp.contenu-externe.longueur-max}.
 */
@ApplicationScoped
public class BalisageExterne {

    private final int longueurMax;

    public BalisageExterne(@ConfigProperty(name = "argos.mcp.contenu-externe.longueur-max", defaultValue = "2000") int longueurMax) {
        if (longueurMax < 1) {
            throw new IllegalArgumentException("argos.mcp.contenu-externe.longueur-max doit être positif");
        }
        this.longueurMax = longueurMax;
    }

    /** Texte externe balisé et tronqué ; {@code null} si la source n'a fourni aucun texte. */
    public TexteExterne baliser(String source, String texte) {
        if (texte == null || texte.isEmpty()) {
            return null;
        }
        int longueur = texte.codePointCount(0, texte.length());
        if (longueur <= longueurMax) {
            return new TexteExterne(TexteExterne.NATURE, source, texte, false, longueur);
        }
        // Troncature sur un nombre de points de code : aucun caractère n'est coupé en deux.
        String tronque = texte.substring(0, texte.offsetByCodePoints(0, longueurMax));
        return new TexteExterne(TexteExterne.NATURE, source, tronque, true, longueur);
    }

    public int longueurMax() {
        return longueurMax;
    }
}
