package io.argos.aigateway;

import java.util.Objects;

/**
 * Entrée d'une demande : nom (identifiant court), valeur et classe de confidentialité déclarée par le
 * module appelant. Toute valeur est traitée comme une donnée non fiable : la passerelle la place entre
 * balises de données, jamais parmi les instructions (R-11).
 */
public record EntreeIa(String nom, String valeur, ClasseDonnee classe) {

    public EntreeIa {
        Objects.requireNonNull(nom, "nom");
        Objects.requireNonNull(classe, "classe");
        if (!nom.matches("[a-z][a-zA-Z0-9_]{0,39}")) {
            throw new IllegalArgumentException("nom d'entrée invalide : " + nom);
        }
        valeur = valeur == null ? "" : valeur;
    }

    public static EntreeIa publique(String nom, String valeur) {
        return new EntreeIa(nom, valeur, ClasseDonnee.PUBLIC);
    }

    public static EntreeIa interne(String nom, String valeur) {
        return new EntreeIa(nom, valeur, ClasseDonnee.INTERNAL);
    }
}
