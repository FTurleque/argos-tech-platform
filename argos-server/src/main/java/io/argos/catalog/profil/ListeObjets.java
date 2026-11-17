package io.argos.catalog.profil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Liste fermée des valeurs qu'un utilisateur peut suivre pour un type d'objet du profil
 * (ADR-0024 point 1, exigence ART-02) : par exemple les disciplines ou les territoires du profil
 * {@code artiste}.
 *
 * @param typeObjet type d'objet suivi, déclaré dans {@code typesObjetsSuivis} du manifeste
 * @param valeurs   valeurs admises, dans l'ordre d'affichage
 */
public record ListeObjets(String typeObjet, List<ValeurListe> valeurs) {

    public ListeObjets {
        valeurs = valeurs == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(valeurs));
    }

    /** Identifiants des valeurs, dans l'ordre d'affichage. */
    public List<String> identifiants() {
        return valeurs.stream().filter(v -> v != null).map(ValeurListe::identifiant).toList();
    }

    /** Valeur portant cet identifiant. */
    public Optional<ValeurListe> valeur(String identifiant) {
        return valeurs.stream().filter(v -> v != null && v.identifiant() != null && v.identifiant().equals(identifiant))
                .findFirst();
    }
}
