package io.argos.scm;

/**
 * Dépôt introuvable sur la forge, ou invisible avec le jeton configuré : GitHub répond 404 dans les
 * deux cas. Distinct d'un fichier absent d'un dépôt existant, qui n'est pas une erreur (INV-04).
 */
public class DepotIntrouvable extends ScmException {

    private static final long serialVersionUID = 1L;

    public DepotIntrouvable(String forge, String depot) {
        super("dépôt " + forge + " introuvable ou inaccessible avec le jeton configuré : " + depot, null);
    }
}
