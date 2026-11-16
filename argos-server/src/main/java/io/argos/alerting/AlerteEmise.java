package io.argos.alerting;

import java.util.UUID;

/**
 * Événement CDI publié après l'enregistrement d'une alerte ou d'un résumé, avant sa diffusion.
 * Le changement {@code notifier-sur-le-poste} peut l'observer pour pousser la notification au
 * client desktop.
 */
public record AlerteEmise(UUID alerteId, UUID compteId, String canal, String nature) {
}
