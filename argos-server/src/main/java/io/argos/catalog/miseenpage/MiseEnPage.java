package io.argos.catalog.miseenpage;

/**
 * Mise en page déclarée au catalogue (ADR-0023, exigence AFF-01).
 *
 * <p>Le serveur ne connaît que l'identifiant et les libellés : la présentation
 * elle-même (composants, polices, couleurs) appartient à la couche mise en page
 * d'{@code argos-ui}.
 *
 * @param identifiant identifiant technique stable, par exemple {@code gazette}
 * @param libelle     libellé affiché
 * @param description phrase décrivant l'esprit de la mise en page
 */
public record MiseEnPage(String identifiant, String libelle, String description) {
}
