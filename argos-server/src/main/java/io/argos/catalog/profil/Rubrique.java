package io.argos.catalog.profil;

/**
 * Rubrique déclarée par un profil : classe un élément de veille pour ce profil
 * (ADR-0024 point 4, exigence PRF-02).
 *
 * @param identifiant identifiant stable, unique dans le profil
 * @param libelle     libellé affiché
 */
public record Rubrique(String identifiant, String libelle) {
}
