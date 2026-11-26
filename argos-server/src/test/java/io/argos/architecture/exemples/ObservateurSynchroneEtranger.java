package io.argos.architecture.exemples;

import jakarta.enterprise.event.Observes;

import io.argos.signal.SignalChanged;

/**
 * Observateur synchrone d'un événement d'un autre module, absent de la liste fermée, contre lequel
 * {@code ObservateursEvenementsTest} vérifie que sa règle le refuse. Jamais exécuté.
 */
public final class ObservateurSynchroneEtranger {

    void surSignal(@Observes SignalChanged changement) {
        changement.signalId();
    }
}
