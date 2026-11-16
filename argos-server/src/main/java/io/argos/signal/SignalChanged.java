package io.argos.signal;

import java.util.UUID;

/**
 * Événement CDI émis quand un signal est créé ou que son contenu change. Les observateurs
 * (enrichissement, corrélation) le reçoivent après validation de la transaction.
 */
public record SignalChanged(UUID signalId, boolean created) {
}
