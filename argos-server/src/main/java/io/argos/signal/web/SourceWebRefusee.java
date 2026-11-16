package io.argos.signal.web;

/**
 * Demande de source refusée pour une donnée invalide : adresse interdite ou mal formée, contenu
 * qui n'est pas un flux ou une page, portée ou type inconnus (WEB-01-S2, WEB-03-S1). Aucune
 * source n'est créée.
 */
public class SourceWebRefusee extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String attribut;

    public SourceWebRefusee(String code, String message, String attribut) {
        super(message);
        this.code = code;
        this.attribut = attribut;
    }

    /** Code stable : motif de la récupération refusée ou {@code DONNEE_INVALIDE}. */
    public String code() {
        return code;
    }

    /** Attribut de la demande en cause. */
    public String attribut() {
        return attribut;
    }
}
