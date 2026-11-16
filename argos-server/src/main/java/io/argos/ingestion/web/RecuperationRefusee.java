package io.argos.ingestion.web;

/**
 * Adresse ou récupération refusée par le collecteur web (WEB-01, WEB-03). Le message est destiné à
 * l'utilisateur : il nomme le motif et l'hôte en cause, jamais le contenu reçu.
 */
public class RecuperationRefusee extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Motif stable, exploitable par l'API et l'IHM. */
    public enum Motif {
        /** Adresse mal formée, schéma autre que http(s), identifiants dans l'adresse. */
        ADRESSE_INVALIDE,
        /** Adresse résolue vers un réseau privé, local, de lien local ou réservé (R-12). */
        ADRESSE_INTERDITE,
        /** Redirection vers une adresse interdite ou invalide (R-12). */
        REDIRECTION_INTERDITE,
        TROP_DE_REDIRECTIONS,
        DELAI_DEPASSE,
        CONTENU_TROP_VOLUMINEUX,
        REPONSE_HTTP,
        ERREUR_RESEAU,
        /** Contenu reçu qui n'est pas un flux RSS ou Atom (WEB-01-S2). */
        FLUX_INVALIDE,
        /** Contenu reçu qui n'est pas une page HTML. */
        PAGE_INVALIDE
    }

    private final Motif motif;

    public RecuperationRefusee(Motif motif, String message) {
        super(message);
        this.motif = motif;
    }

    public RecuperationRefusee(Motif motif, String message, Throwable cause) {
        super(message, cause);
        this.motif = motif;
    }

    public Motif motif() {
        return motif;
    }
}
