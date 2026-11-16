package io.argos.aigateway;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Demande adressée à la passerelle (contrat interne, DC-1) : tâche (identifiant d'un prompt du
 * registre), espace imputé ({@code null} : instance seule), entrées classées et sources sur lesquelles
 * le résultat s'appuie (IAG-05).
 */
public record DemandeIa(String tache, UUID espaceId, List<EntreeIa> entrees, List<String> sources) {

    public DemandeIa {
        Objects.requireNonNull(tache, "tache");
        entrees = List.copyOf(entrees);
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (entrees.isEmpty()) {
            throw new IllegalArgumentException("demande sans entrée");
        }
    }
}
