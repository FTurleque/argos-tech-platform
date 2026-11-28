package io.argos.api;

import java.math.BigInteger;
import java.util.regex.Pattern;

import io.argos.commun.DonneeInvalide;
import io.argos.commun.Pagination;

/**
 * Lecture des paramètres de pagination des listes de l'API REST ({@code page}, {@code size}, {@code taille},
 * {@code limite} ; INV-06, décision G4 du porteur du projet, 2026-11-19). Une valeur absente ou vide prend la valeur
 * par défaut ; une valeur entière, même hors des bornes de {@code int}, est saturée puis soumise aux bornes propres à
 * chaque liste ; une valeur non entière est refusée par une réponse 400 ({@code DONNEE_INVALIDE}, attribut en cause),
 * et non par le 404 qu'une conversion JAX-RS en échec produirait.
 */
public final class ParametresPagination {

    private static final Pattern ENTIER = Pattern.compile("[+-]?[0-9]+");
    private static final BigInteger MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    /** Nom du paramètre de requête du numéro de page (ADR-0040). */
    public static final String PAGE = "page";

    /** Nom du paramètre de requête de la taille de page (ADR-0040). */
    public static final String TAILLE = "size";

    /** Description OpenAPI du paramètre {@value #PAGE}. */
    public static final String DESCRIPTION_PAGE =
            "Numéro de page, à partir de 0 ; une valeur négative vaut 0 ; une valeur non entière donne 400";

    /** Description OpenAPI du paramètre {@value #TAILLE}. */
    public static final String DESCRIPTION_TAILLE = "Taille de page, de " + Pagination.TAILLE_MIN + " à "
            + Pagination.TAILLE_MAX + " ; une valeur hors bornes est ramenée à la borne la plus proche ;"
            + " une valeur non entière donne 400";

    private ParametresPagination() {
    }

    /**
     * Demande de page lue dans les paramètres {@value #PAGE} et {@value #TAILLE} (ADR-0040) : absents, la première
     * page à la taille par défaut.
     *
     * @throws DonneeInvalide si l'un des deux n'est pas un entier décimal
     */
    public static Pagination pagination(String page, String taille) {
        return new Pagination(entier(page, PAGE, 0), entier(taille, TAILLE, Pagination.TAILLE_DEFAUT));
    }

    /**
     * Valeur entière du paramètre {@code attribut}, ou {@code defaut} s'il est absent ou vide.
     *
     * @throws DonneeInvalide si la valeur n'est pas un entier décimal (code {@code DONNEE_INVALIDE})
     */
    public static int entier(String valeur, String attribut, int defaut) {
        if (valeur == null || valeur.isBlank()) {
            return defaut;
        }
        String saisie = valeur.strip();
        if (!ENTIER.matcher(saisie).matches()) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le paramètre « " + attribut + " » doit être un entier.", attribut);
        }
        return new BigInteger(saisie).max(MIN).min(MAX).intValue();
    }
}
