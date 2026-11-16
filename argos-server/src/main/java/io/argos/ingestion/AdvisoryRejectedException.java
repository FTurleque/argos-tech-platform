package io.argos.ingestion;

/** Avis impossible à normaliser : il est mis à l'écart avec son motif (COL-02-S2, COL-08). */
public class AdvisoryRejectedException extends RuntimeException {

    public AdvisoryRejectedException(String reason) {
        super(reason);
    }

    public AdvisoryRejectedException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
