package io.argos.commun;

/**
 * Donnée d'entrée invalide (HTTP 400). Le code vaut {@code DONNEE_INVALIDE} sauf motif plus précis,
 * par exemple le motif de refus d'une adresse de veille web ; l'attribut en cause est facultatif.
 */
public class DonneeInvalide extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String attribut;

    public DonneeInvalide(String message) {
        this("DONNEE_INVALIDE", message, null);
    }

    public DonneeInvalide(String code, String message, String attribut) {
        super(message);
        this.code = code;
        this.attribut = attribut;
    }

    /** Code stable de l'erreur. */
    public String code() {
        return code;
    }

    /** Attribut de la demande en cause, ou {@code null}. */
    public String attribut() {
        return attribut;
    }
}
