package io.argos.architecture.exemples;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.argos.signal.SignalQueries;
import io.argos.signal.SignalQueries.SignalView;

/**
 * Exemple de lecture unitaire des signaux par référence de méthode, contre lequel
 * {@code LectureGroupeeSignauxTest} vérifie que sa règle voit aussi les références de méthode. Jamais exécuté.
 */
public final class LectureUnitaireParReference {

    private LectureUnitaireParReference() {
    }

    public static List<Optional<SignalView>> lire(SignalQueries signaux, List<UUID> ids) {
        return ids.stream().map(signaux::find).toList();
    }
}
