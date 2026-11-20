package io.argos.impact;

import io.argos.signal.opportunite.Opportunite;

/**
 * Gabarit de résumé d'une opportunité (ART-06, garde-fou {@code AUCUN_AVIS_ELIGIBILITE}).
 *
 * <p>Le résumé est composé sans IA, à partir des champs collectés : il présente les critères tels
 * qu'énoncés par l'organisme, cités et présentés comme tels, la date limite et le lien vers le texte
 * officiel, suivis d'une mention fixe. Il ne contient aucune appréciation de l'éligibilité de
 * l'utilisateur : le gabarit n'a ni champ ni formule qui la porte, et aucun contenu externe n'est
 * envoyé à un modèle.
 */
public final class ResumeOpportunite {

    /** Mention ajoutée à chaque résumé et à chaque rappel. */
    public static final String MENTION = "Argos ne se prononce pas sur votre éligibilité : "
            + "reportez-vous au texte officiel de l'organisme.";

    /** Longueur maximale des critères cités dans le résumé. */
    static final int CRITERES_MAX = 2000;

    /**
     * Résumé affiché.
     *
     * @param titre          titre de l'opportunité
     * @param organisme      organisme ou source qui la publie
     * @param criteres       critères tels qu'énoncés, cités (texte brut, éventuellement tronqué)
     * @param criteresTronques vrai si les critères ont été tronqués : le texte officiel fait foi
     * @param dateLimite     date limite, ou « à confirmer »
     * @param texteOfficiel  lien vers le texte officiel, absent si la source n'en fournit pas
     * @param mention        mention « aucun avis d'éligibilité »
     */
    public record Resume(String titre, String organisme, String criteres, boolean criteresTronques,
            String dateLimite, String texteOfficiel, String mention) {
    }

    private ResumeOpportunite() {
    }

    public static Resume de(Opportunite opportunite) {
        String criteres = opportunite.criteres() == null ? "" : opportunite.criteres().strip();
        boolean tronques = criteres.length() > CRITERES_MAX;
        if (tronques) {
            criteres = criteres.substring(0, CRITERES_MAX) + " […]";
        }
        String organisme = opportunite.organisme() != null ? opportunite.organisme() : opportunite.sourceLibelle();
        return new Resume(opportunite.titre(), organisme,
                criteres.isEmpty() ? "Critères non fournis par la source : consultez le texte officiel." : criteres,
                tronques, opportunite.dateLimite() == null ? "à confirmer" : opportunite.dateLimite().toString(),
                opportunite.lien(), MENTION);
    }
}
