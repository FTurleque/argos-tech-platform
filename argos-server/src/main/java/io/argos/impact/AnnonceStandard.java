package io.argos.impact;

import java.util.List;
import java.util.UUID;

/**
 * Événement CDI publié par {@link RadarProjets} quand un changement de standard touche au moins un
 * projet de l'espace (RAD-06) : le module {@code alerting} l'annonce aux utilisateurs des projets cités.
 *
 * @param ancienStatut    statut avant le changement ({@code ADOPT}, {@code TRIAL}, {@code ASSESS}, {@code HOLD})
 * @param ancienneVersion version recommandée avant le changement, absente sinon
 * @param projets         projets de l'espace qui utilisent la technologie, avec la version utilisée
 * @param historiqueId    entrée de l'historique du référentiel qui consigne le changement annoncé
 */
public record AnnonceStandard(UUID espaceId, String cle, String libelle, String ancienStatut, String ancienneVersion,
        String statut, String versionRecommandee, String justification, List<RadarProjets.ProjetConcerne> projets, UUID historiqueId) {

    public AnnonceStandard {
        projets = List.copyOf(projets);
    }
}
