package io.argos.catalog.standard;

import java.util.UUID;

/**
 * Événement CDI publié après la validation d'un changement de statut ou de version recommandée d'une
 * technologie du référentiel (RAD-06). Le module {@code impact} le relaie aux projets concernés ; la
 * création d'une entrée n'est pas un changement et ne publie rien.
 *
 * @param ancienStatut    statut avant le changement
 * @param ancienneVersion version recommandée avant le changement, absente sinon
 * @param historiqueId    entrée de l'historique qui consigne ce changement : identifie l'annonce
 */
public record StandardModifie(UUID espaceId, String cle, String libelle, ReferentielStandards.Statut ancienStatut,
        String ancienneVersion, ReferentielStandards.Statut statut, String versionRecommandee, String justification,
        UUID modifiePar, UUID historiqueId) {
}
