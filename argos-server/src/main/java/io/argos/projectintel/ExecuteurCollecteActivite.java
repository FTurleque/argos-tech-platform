package io.argos.projectintel;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.execution.ExecuteurBorne;

/**
 * Exécuteur de la collecte d'activité (AUD-PER-13, design D9 du changement {@code rendre-ingestion-asynchrone}) : une
 * tâche par forge, les projets d'une forge en séquence ; deux fils et une file de huit tâches par défaut
 * ({@code argos.activite.forges.fils}, {@code .fils-max}, {@code .file}).
 */
@ApplicationScoped
public class ExecuteurCollecteActivite extends ExecuteurBorne {

    public ExecuteurCollecteActivite() {
        super("argos-collecte-activite", "argos.activite.forges");
    }
}
