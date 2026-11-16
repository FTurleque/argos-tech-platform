package io.argos.aigateway;

import java.util.List;
import java.util.Map;

/**
 * Réponse de la passerelle : sortie validée, marquée IA et accompagnée de ses sources ({@link
 * Issue#PRODUIT}), ou motif de refus. Un refus laisse toujours le traitement appelant se poursuivre
 * sans IA (IAG-03, décision D1).
 *
 * @param champs champs de la sortie validée, vides sauf pour {@link Issue#PRODUIT}
 * @param mention mention à afficher avec tout contenu produit ({@value #MENTION}), nulle sinon
 * @param sources sources transmises avec la demande, à afficher avec le contenu produit
 */
public record ReponseIa(Issue issue, Map<String, String> champs, String mention, List<String> sources,
        String modele, int versionPrompt) {

    /** Mention obligatoire de tout contenu produit par un modèle (IAG-05, AFF-02). */
    public static final String MENTION = "produit par l'IA";

    /** Issue d'une demande. */
    public enum Issue {
        /** Sortie conforme au schéma de la tâche. */
        PRODUIT,
        /** Une entrée est classée CONFIDENTIAL ou RESTRICTED (IAG-02). */
        REFUS_CLASSE,
        /** Une entrée contient un secret ou un jeton (Q-4). */
        REFUS_DONNEE_SENSIBLE,
        /** Plafond journalier de l'instance atteint (IAG-03). */
        PLAFOND_INSTANCE,
        /** Quota journalier de l'espace atteint (IAG-03). */
        QUOTA_ESPACE,
        /** Sortie non conforme au schéma (IAG-04). */
        SORTIE_INVALIDE,
        /** Fournisseur injoignable, en erreur ou trop lent. */
        INDISPONIBLE,
        /** IA désactivée par la configuration ({@code argos.ia.active=false}). */
        DESACTIVEE
    }

    public ReponseIa {
        champs = champs == null ? Map.of() : Map.copyOf(champs);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    static ReponseIa refus(Issue issue, String modele, int versionPrompt) {
        return new ReponseIa(issue, Map.of(), null, List.of(), modele, versionPrompt);
    }

    public boolean produit() {
        return issue == Issue.PRODUIT;
    }

    /** Vrai si le refus tient au plafond de l'instance ou au quota de l'espace. */
    public boolean plafondAtteint() {
        return issue == Issue.PLAFOND_INSTANCE || issue == Issue.QUOTA_ESPACE;
    }

    public String champ(String nom) {
        return champs.get(nom);
    }
}
