package io.argos.execution;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Exécuteur des observateurs asynchrones ({@link PublicationAsynchrone}, ADR-0039) : deux fils, quatre au plus et
 * une file de 256 notifications par défaut ({@code argos.evenements.fils}, {@code .fils-max}, {@code .file}). File
 * pleine, la notification s'exécute sur le fil émetteur : aucun événement n'est perdu.
 */
@ApplicationScoped
public class ExecuteurEvenements extends ExecuteurBorne {

    public ExecuteurEvenements() {
        super("argos-evenements", "argos.evenements");
    }
}
