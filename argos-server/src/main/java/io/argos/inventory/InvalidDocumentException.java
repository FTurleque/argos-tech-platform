package io.argos.inventory;

/** SBOM ou manifeste inexploitable ; le motif est enregistré et consultable (INV-05). */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String reason) {
        super(reason);
    }

    public InvalidDocumentException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
