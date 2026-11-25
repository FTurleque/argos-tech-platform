package io.argos.api;

import java.math.BigInteger;
import java.util.regex.Pattern;

import io.argos.commun.DonneeInvalide;

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

    private ParametresPagination() {
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
