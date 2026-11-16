package io.argos.ingestion;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;

/** Attente entre deux tentatives ; remplacée par une attente simulée dans les tests. */
@ApplicationScoped
public class Sleeper {

    public void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }
}
