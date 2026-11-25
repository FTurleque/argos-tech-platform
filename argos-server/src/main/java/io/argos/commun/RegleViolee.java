package io.argos.commun;

/** Demande valide en forme mais contraire à une règle du domaine (par exemple INV-1). */
public class RegleViolee extends RuntimeException {

    private final String code;

    public RegleViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
