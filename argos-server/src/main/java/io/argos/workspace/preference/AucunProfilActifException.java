package io.argos.workspace.preference;

/** Demande qui laisserait un utilisateur sans profil actif (INV-1, PRF-01-S2). */
public class AucunProfilActifException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AucunProfilActifException() {
        super("Au moins un profil de veille doit rester actif.");
    }
}
