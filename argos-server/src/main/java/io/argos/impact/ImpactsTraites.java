package io.argos.impact;

import java.util.UUID;

/**
 * Des impacts d'un signal ont quitté les états éligibles aux alertes ({@code ECARTE}, {@code RESOLU}), par une
 * transition d'un utilisateur ou du système. Publié après la validation de la transaction : le module
 * {@code alerting} y retire les alertes immédiates encore en attente qui n'ont plus d'objet (ALR-07-S1).
 */
public record ImpactsTraites(UUID signalId) {
}
