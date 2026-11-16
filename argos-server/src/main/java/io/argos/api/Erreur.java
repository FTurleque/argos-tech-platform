package io.argos.api;

/** Corps des réponses d'erreur de l'API : un code stable et un message lisible. */
public record Erreur(String code, String message) {
}
