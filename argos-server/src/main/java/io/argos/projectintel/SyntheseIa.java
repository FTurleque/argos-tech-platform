package io.argos.projectintel;

/**
 * Point d'extension de la synthèse IA de l'activité d'un projet, fourni par {@code ai-gateway}
 * (ADR-0004, capacité {@code passerelle-ia}). Ce changement ne livre aucune implémentation : sans
 * implémentation, la synthèse a l'état {@code INDISPONIBLE} et les indicateurs restent affichés.
 *
 * <p>La demande est typée : elle ne contient que les indicateurs affichés à l'utilisateur
 * ({@link VueActivite}), jamais de donnée brute de la forge ni d'identité de personne. Les titres de
 * jalons sont des textes externes : l'implémentation les place dans la partie « données » de la
 * demande au modèle, séparée et balisée, jamais dans les instructions (injection d'instructions).
 * La synthèse ne calcule aucun indicateur (PIN-02) : {@link SyntheseActivite} refuse une réponse qui
 * contient un nombre absent des indicateurs.
 */
public interface SyntheseIa {

    /** Faux si le plafond de coût est atteint : aucun appel n'est alors effectué (état {@code PLAFOND_ATTEINT}). */
    boolean budgetDisponible();

    /**
     * Synthèse en texte brut des indicateurs. Un résultat vide vaut refus (état {@code REFUSEE}) ; une
     * exception vaut indisponibilité (état {@code INDISPONIBLE}).
     */
    String synthetiser(VueActivite indicateurs);
}
