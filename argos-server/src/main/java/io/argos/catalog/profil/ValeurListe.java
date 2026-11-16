package io.argos.catalog.profil;

/**
 * Valeur d'une liste du pack, par exemple une discipline ou un territoire (exigence ART-02).
 *
 * @param identifiant identifiant stable, unique dans la liste
 * @param libelle     libellé affiché
 */
public record ValeurListe(String identifiant, String libelle) {
}
