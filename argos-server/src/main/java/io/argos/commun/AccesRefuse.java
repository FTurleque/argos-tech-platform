package io.argos.commun;

/** Refus d'accès à une ressource d'espace ; journalisé par la couche d'exposition (ACC-06). */
public class AccesRefuse extends RuntimeException {

    private final MotifRefus motif;

    public AccesRefuse(MotifRefus motif) {
        super(motif == MotifRefus.NON_MEMBRE ? "Ressource introuvable." : "Action interdite pour votre rôle.");
        this.motif = motif;
    }

    public MotifRefus motif() {
        return motif;
    }
}
