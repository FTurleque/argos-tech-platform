package io.argos.testsupport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.ingestion.Sleeper;
import io.quarkus.test.Mock;

/**
 * Attente simulée : enregistre chaque délai demandé et l'instant de la demande, sans attendre, puis exécute l'action
 * programmée pendant l'attente.
 */
@Mock
@ApplicationScoped
public class RecordingSleeper extends Sleeper {

    /** Attente demandée. */
    public record Wait(Duration duration, Instant instant) {
    }

    private static final Runnable RIEN = () -> { };

    private final List<Wait> waits = Collections.synchronizedList(new ArrayList<>());
    private volatile Runnable pendantAttente = RIEN;

    @Override
    public void sleep(Duration duration) {
        waits.add(new Wait(duration, Instant.now()));
        pendantAttente.run();
    }

    /** Action exécutée pendant chaque attente, sur le fil qui attend (concurrence pendant l'attente de reprise). */
    public void pendantAttente(Runnable action) {
        pendantAttente = action;
    }

    public List<Wait> waits() {
        synchronized (waits) {
            return List.copyOf(waits);
        }
    }

    public void reset() {
        waits.clear();
        pendantAttente = RIEN;
    }
}
