package io.argos.impact;

/** État de l'analyse complémentaire d'une corrélation (COR-04) ; elle ne crée jamais d'impact. */
public enum StatutAnalyse {

    /** Aucune analyse complémentaire n'est configurée. */
    NONE,

    /** Le budget de l'analyse est épuisé : elle n'est pas lancée. */
    BUDGET_EXHAUSTED,

    /** L'analyse a produit ses suggestions. */
    DONE,

    /** L'analyse a échoué : la corrélation déterministe reste valable. */
    UNAVAILABLE
}
