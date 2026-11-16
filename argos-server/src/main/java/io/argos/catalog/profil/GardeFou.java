package io.argos.catalog.profil;

/**
 * Garde-fou déclaré par un profil (ADR-0024 point 7).
 *
 * @param type        type de garde-fou appliqué par le serveur
 * @param description explication lisible par l'utilisateur
 */
public record GardeFou(TypeGardeFou type, String description) {
}
