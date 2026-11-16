package io.argos.testsupport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.ingestion.Sleeper;
import io.quarkus.test.Mock;

/** Attente simulée : enregistre chaque délai demandé et l'instant de la demande, sans attendre. */
@Mock
@ApplicationScoped
public class RecordingSleeper extends Sleeper {

    /** Attente demandée. */
    public record Wait(Duration duration, Instant at) {
    }

    private final List<Wait> waits = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sleep(Duration duration) {
        waits.add(new Wait(duration, Instant.now()));
    }

    public List<Wait> waits() {
        synchronized (waits) {
            return List.copyOf(waits);
        }
    }

    public void reset() {
        waits.clear();
    }
}
