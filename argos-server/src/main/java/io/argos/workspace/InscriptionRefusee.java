package io.argos.workspace;

/** Première connexion d'une personne sans invitation alors que l'inscription se fait sur invitation (Q-2). */
public class InscriptionRefusee extends RuntimeException {

    public InscriptionRefusee() {
        super("L'inscription à Argos se fait sur invitation : aucune invitation en attente pour votre adresse e-mail vérifiée.");
    }
}
