package io.argos.commun.http;

import java.io.IOException;

/** Réponse HTTP dont le corps dépasse la taille maximale admise : la lecture s'arrête au dépassement. */
public class ReponseTropVolumineuse extends IOException {

    private static final long serialVersionUID = 1L;

    private final long tailleMax;

    public ReponseTropVolumineuse(long tailleMax) {
        super("réponse au-delà de " + tailleMax + " octets");
        this.tailleMax = tailleMax;
    }

    /** Taille maximale dépassée, en octets. */
    public long tailleMax() {
        return tailleMax;
    }
}
