package io.argos.api;

import java.math.BigInteger;
import java.util.regex.Pattern;

import io.argos.workspace.DonneeInvalide;

/**
 * Lecture des paramètres de pagination {@code page} et {@code size} (INV-06). Une valeur absente prend la valeur par
 * défaut ; une valeur entière, même hors des bornes de {@code int}, est saturée puis bornée par la requête ; une
 * valeur non entière est refusée par une réponse 400 ({@code DONNEE_INVALIDE}, attribut en cause), et non par le 404
 * qu'une conversion JAX-RS en échec produirait.
 */
final class ParametresPagination {

    private static final Pattern ENTIER = Pattern.compile("[+-]?[0-9]+");
    private static final BigInteger MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private ParametresPagination() {
    }

    static int entier(String valeur, String attribut, int defaut) {
        if (valeur == null || valeur.isBlank()) {
            return defaut;
        }
        String v = valeur.strip();
        if (!ENTIER.matcher(v).matches()) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le paramètre « " + attribut + " » doit être un entier.", attribut);
        }
        return new BigInteger(v).max(MIN).min(MAX).intValue();
    }
}
