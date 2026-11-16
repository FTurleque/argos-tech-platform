package io.argos.catalog.profil;

/**
 * Stratégie de rapprochement déclarée par un profil (ADR-0024 point 5).
 *
 * <p>Le manifeste ne fait que nommer la stratégie : son code vit dans
 * {@code impact-engine}. Une stratégie doit être déterministe (ADR-0013).
 *
 * @param identifiant  identifiant de la stratégie, par exemple {@code purl-plage-versions}
 * @param deterministe vrai si le rapprochement ne dépend d'aucun appel à l'IA
 */
public record StrategieDeclaree(String identifiant, boolean deterministe) {
}
