package io.argos.mcp;

/**
 * Texte issu d'une source externe, renvoyé à part des champs produits par Argos (MCP-05, risque
 * R-11, décision D2).
 *
 * <p>Ce texte est une donnée non fiable : il peut contenir des consignes destinées à un assistant
 * IA (injection indirecte). Il n'est jamais fusionné avec un champ produit par Argos et il est
 * tronqué à la longueur maximale configurée ({@code argos.mcp.contenu-externe.longueur-max}).
 *
 * @param nature            toujours {@link #NATURE} : marque explicite du contenu externe
 * @param source            source d'où provient le texte (par exemple {@code OSV}, {@code NVD})
 * @param texte             texte, éventuellement tronqué
 * @param tronque           vrai si le texte a été tronqué
 * @param longueurOriginale longueur du texte avant troncature, en caractères
 */
public record TexteExterne(String nature, String source, String texte, boolean tronque, int longueurOriginale) {

    /** Marque portée par tout contenu externe. */
    public static final String NATURE = "CONTENU_EXTERNE_NON_FIABLE";
}
