package io.argos.scm;

/** Échec d'appel à la forge ; le message ne contient jamais le jeton d'accès (QS-09). */
public class ScmException extends RuntimeException {

    public ScmException(String message, Throwable cause) {
        super(message, cause);
    }
}
