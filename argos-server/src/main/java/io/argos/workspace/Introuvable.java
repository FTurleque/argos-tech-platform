package io.argos.workspace;

/**
 * Ressource absente du point de vue du compte courant, sans refus d'accès à un espace : réponse
 * « introuvable », non journalisée (par exemple une invitation qui ne lui est pas adressée).
 */
public class Introuvable extends RuntimeException {

    public Introuvable(String message) {
        super(message);
    }
}
