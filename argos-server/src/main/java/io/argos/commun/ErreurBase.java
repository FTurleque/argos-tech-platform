package io.argos.commun;

/** Échec d'accès à la base de données, cause SQL conservée. */
public class ErreurBase extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ErreurBase(String message, Throwable cause) {
        super(message, cause);
    }
}
