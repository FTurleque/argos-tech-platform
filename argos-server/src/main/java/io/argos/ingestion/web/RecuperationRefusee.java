package io.argos.ingestion.web;

/**
 * Adresse ou récupération refusée par le collecteur web (WEB-01, WEB-03).
 *
 * <p>Le refus porte deux textes. {@link #message()} est destiné à l'utilisateur : il est propre au
 * motif et ne contient ni hôte, ni adresse IP, ni statut, ni destination de redirection, pour que
 * les réponses ne servent pas à sonder un réseau. {@link #getMessage()} est le détail technique,
 * écrit seulement dans le journal applicatif.
 */
public class RecuperationRefusee extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Motif stable, exploitable par l'API et l'IHM, avec son message générique. */
    public enum Motif {
        /** Adresse mal formée, schéma autre que http(s), identifiants dans l'adresse, adresse trop longue. */
        ADRESSE_INVALIDE("Adresse invalide : une adresse http ou https complète, sans identifiants, est attendue."),
        /**
         * Hôte introuvable, hôte résolu vers un réseau privé, local, de lien local ou réservé, ou port
         * non autorisé (R-12). Un hôte introuvable reçoit le même motif qu'un hôte interdit.
         */
        ADRESSE_INTERDITE("Adresse refusée : elle doit désigner un serveur public, sur un port autorisé."),
        /** Redirection vers une adresse invalide ou interdite (R-12). */
        REDIRECTION_INTERDITE("Redirection refusée : la destination n'est pas une adresse publique autorisée."),
        TROP_DE_REDIRECTIONS("Trop de redirections."),
        DELAI_DEPASSE("Le serveur n'a pas répondu dans le délai imparti."),
        CONTENU_TROP_VOLUMINEUX("Le contenu dépasse la taille maximale acceptée."),
        REPONSE_HTTP("Le serveur a répondu par une erreur."),
        ERREUR_RESEAU("Le serveur n'a pas pu être joint."),
        /** Contenu reçu qui n'est pas un flux RSS ou Atom (WEB-01-S2). */
        FLUX_INVALIDE("L'adresse ne renvoie pas un flux RSS ou Atom valide."),
        /** Contenu reçu qui n'est pas une page HTML. */
        PAGE_INVALIDE("L'adresse ne renvoie pas une page HTML.");

        private final String message;

        Motif(String message) {
            this.message = message;
        }

        /** Message générique destiné à l'utilisateur. */
        public String message() {
            return message;
        }
    }

    private final Motif motif;

    /**
     * @param motif  motif du refus
     * @param detail détail technique pour le journal applicatif (hôte, cause) ; jamais renvoyé à
     *               l'utilisateur
     */
    public RecuperationRefusee(Motif motif, String detail) {
        super(detail);
        this.motif = motif;
    }

    public RecuperationRefusee(Motif motif, String detail, Throwable cause) {
        super(detail, cause);
        this.motif = motif;
    }

    public Motif motif() {
        return motif;
    }

    /** Message générique destiné à l'utilisateur. */
    public String message() {
        return motif.message();
    }

    /** Motif et message générique, tels qu'enregistrés dans l'état d'une source en erreur. */
    public String motifPublic() {
        return motif.name() + " : " + motif.message();
    }
}
