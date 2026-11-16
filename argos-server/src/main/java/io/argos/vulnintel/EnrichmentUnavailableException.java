package io.argos.vulnintel;

/** Source d'enrichissement indisponible : le signal reste enregistré sans cet enrichissement (COL-04-S3). */
public class EnrichmentUnavailableException extends RuntimeException {

    public EnrichmentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
