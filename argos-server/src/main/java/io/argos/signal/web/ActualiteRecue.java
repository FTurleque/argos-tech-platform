package io.argos.signal.web;

import java.util.UUID;

/**
 * Une source a cité une actualité pour la première fois. Émis après validation de la transaction ;
 * {@code espaceId} est renseigné pour une source d'espace, vide pour une source personnelle. Le
 * moteur d'impact s'en sert pour le rapprochement par nom (WEB-06).
 */
public record ActualiteRecue(UUID actualiteId, UUID sourceId, UUID espaceId) {
}
