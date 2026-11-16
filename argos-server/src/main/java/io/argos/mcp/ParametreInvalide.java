package io.argos.mcp;

/** Paramètre d'outil invalide : l'appel échoue avant tout appel métier. */
class ParametreInvalide extends RuntimeException {

    ParametreInvalide(String message) {
        super(message);
    }
}
