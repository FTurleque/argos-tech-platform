package io.argos.catalog.profil;

import java.util.List;

/** Manifeste de profil refusé au chargement du catalogue. */
public class ManifesteInvalideException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> erreurs;

    public ManifesteInvalideException(String source, List<String> erreurs) {
        super("Manifeste de profil invalide (" + source + ") : " + String.join(" ; ", erreurs));
        this.erreurs = List.copyOf(erreurs);
    }

    public List<String> erreurs() {
        return erreurs;
    }
}
