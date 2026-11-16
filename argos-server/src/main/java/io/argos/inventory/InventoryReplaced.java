package io.argos.inventory;

import java.util.UUID;

/** Événement CDI : l'inventaire de référence d'un projet a été remplacé (D2). */
public record InventoryReplaced(UUID projectId, String origin) {
}
