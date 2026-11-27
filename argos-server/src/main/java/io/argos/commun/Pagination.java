package io.argos.commun;

/**
 * Demande de page d'une liste (ADR-0040) : numéro de page à partir de 0 et taille de page bornée. Une page négative
 * vaut 0 ; une taille hors de [{@value #TAILLE_MIN}, {@value #TAILLE_MAX}] est ramenée à la borne la plus proche.
 * Une liste paginée trie ses éléments de façon stable, en terminant son tri par l'identifiant.
 *
 * @param page numéro de page, à partir de 0
 * @param taille nombre d'éléments par page
 */
public record Pagination(int page, int taille) {

    /** Taille de page appliquée quand la demande n'en donne pas. */
    public static final int TAILLE_DEFAUT = 50;

    /** Plus petite taille de page. */
    public static final int TAILLE_MIN = 1;

    /** Plus grande taille de page ; une taille supérieure est ramenée à cette borne. */
    public static final int TAILLE_MAX = 200;

    /** Première page, à la taille par défaut. */
    public static final Pagination PREMIERE = new Pagination(0, TAILLE_DEFAUT);

    public Pagination {
        page = Math.max(page, 0);
        taille = Math.min(Math.max(taille, TAILLE_MIN), TAILLE_MAX);
    }

    /** Nombre d'éléments qui précèdent la page ({@code OFFSET} SQL). */
    public long decalage() {
        return (long) page * taille;
    }
}
