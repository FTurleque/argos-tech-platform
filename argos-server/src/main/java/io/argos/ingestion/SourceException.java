package io.argos.ingestion;

import java.time.Duration;
import java.util.Optional;

/**
 * Échec d'un appel à une source externe, classé transitoire ou permanent (COL-06, arc42 §8.5).
 *
 * <p>Le message ne contient jamais de jeton ni d'en-tête d'authentification (QS-09).
 */
public class SourceException extends RuntimeException {

    /** Nature de l'erreur : une erreur transitoire est retentée, une erreur permanente ne l'est pas. */
    public enum Kind { TRANSIENT, PERMANENT }

    private final Kind kind;
    private final Integer httpStatus;
    private final transient Duration retryAfter;

    public SourceException(Kind kind, Integer httpStatus, Duration retryAfter, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
    }

    public static SourceException transientError(String message, Throwable cause) {
        return new SourceException(Kind.TRANSIENT, null, null, message, cause);
    }

    public Kind kind() {
        return kind;
    }

    public Optional<Integer> httpStatus() {
        return Optional.ofNullable(httpStatus);
    }

    /** Délai imposé par l'en-tête {@code Retry-After}, prioritaire sur le délai calculé. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
