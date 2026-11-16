package io.argos.ingestion;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Politique de nouvelles tentatives (COL-06, arc42 §8.5) : délai exponentiel avec gigue, borné,
 * et {@code Retry-After} prioritaire. Valeurs par défaut décidées le 2026-11-16 (Q-4) :
 * 5 tentatives par synchronisation, état {@code DEGRADED} après 3 synchronisations
 * consécutives en échec.
 */
@ApplicationScoped
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final int degradedThreshold;

    @Inject
    public RetryPolicy(
            @ConfigProperty(name = "argos.ingestion.max-attempts", defaultValue = "5") int maxAttempts,
            @ConfigProperty(name = "argos.ingestion.backoff-initial", defaultValue = "PT2S") Duration initialDelay,
            @ConfigProperty(name = "argos.ingestion.backoff-max", defaultValue = "PT5M") Duration maxDelay,
            @ConfigProperty(name = "argos.ingestion.degraded-threshold", defaultValue = "3") int degradedThreshold) {
        if (maxAttempts < 1 || degradedThreshold < 1) {
            throw new IllegalArgumentException("argos.ingestion.max-attempts et degraded-threshold doivent être ≥ 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.degradedThreshold = degradedThreshold;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int degradedThreshold() {
        return degradedThreshold;
    }

    /**
     * Délai avant la tentative qui suit la tentative {@code attempt} (à partir de 1) :
     * le {@code Retry-After} de la source s'il existe, sinon initial × 2^(attempt-1),
     * augmenté d'une gigue de 0 à 20 % et borné par le délai maximal.
     */
    public Duration delayAfter(int attempt, Optional<Duration> retryAfter) {
        if (retryAfter.isPresent()) {
            return retryAfter.get();
        }
        long base = initialDelay.toMillis() << Math.min(attempt - 1, 20);
        long bounded = Math.min(base, maxDelay.toMillis());
        long jitter = bounded <= 0 ? 0 : ThreadLocalRandom.current().nextLong(bounded / 5 + 1);
        return Duration.ofMillis(Math.min(bounded + jitter, maxDelay.toMillis()));
    }
}
