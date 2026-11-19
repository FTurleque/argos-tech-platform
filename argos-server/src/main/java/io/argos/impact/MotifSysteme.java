package io.argos.impact;

/**
 * Motif normalisé d'une transition faite par le système (COR-11, DC-5) : résolution d'un impact {@code OUVERT} ou
 * {@code CONFIRME} qu'une corrélation ne vérifie plus, ou réouverture d'un impact {@code RESOLU} de nouveau vérifié
 * (P-6). Le système ne touche jamais un impact {@code ECARTE}.
 */
public enum MotifSysteme {
    /** Le composant a été retiré de l'inventaire du projet (COR-11-S1). */
    COMPOSANT_RETIRE,
    /** Le composant a été remplacé par une version hors de la plage affectée (COR-11-S2). */
    VERSION_HORS_PLAGE,
    /** L'avis modifié par sa source n'affecte plus le composant (COR-11-S3, P-3). */
    AVIS_MODIFIE,
    /** L'avis a été retiré par sa source : il ne déclare plus aucun paquet affecté (COR-11-S3, P-3). */
    AVIS_RETIRE,
    /** Un impact résolu est de nouveau vérifié par une corrélation (COR-11-S5, P-6). */
    DE_NOUVEAU_VERIFIE
}
