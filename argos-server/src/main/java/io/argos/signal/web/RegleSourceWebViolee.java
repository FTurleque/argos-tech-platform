package io.argos.signal.web;

/**
 * Demande de source contraire à une règle : nombre maximal de sources personnelles atteint
 * ({@code LIMITE_SOURCES}, Q-3) ou adresse déjà suivie par le même propriétaire
 * ({@code SOURCE_EXISTANTE}).
 */
public class RegleSourceWebViolee extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public RegleSourceWebViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
