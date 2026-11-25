package io.argos.impact;

import java.util.List;
import java.util.UUID;

/**
 * Impacts enregistrés ou mis à jour par une corrélation, publiés après la validation de leur
 * transaction et avant toute analyse complémentaire (DC-1 du changement
 * {@code diffuser-alertes-et-digests}). Le module {@code alerting} y évalue les règles d'alerte.
 *
 * <p>Les données du signal sont publiques ; chaque impact porte son espace pour que le destinataire
 * d'une alerte n'en reçoive que ceux de ses espaces (ACC-04).
 */
public record ImpactsRecorded(UUID signalId, String signalTitle, String signalSeverity, String signalSource,
        String signalExternalId, List<Item> impacts) {

    public ImpactsRecorded {
        impacts = List.copyOf(impacts);
    }

    /** Impact enregistré : projet, espace, niveau ({@code EXACT}, {@code PROBABLE}, {@code A_VERIFIER}) et paquet. */
    public record Item(UUID impactId, UUID projectId, UUID workspaceId, MatchRules.Level level, String packageKey) {
    }
}
