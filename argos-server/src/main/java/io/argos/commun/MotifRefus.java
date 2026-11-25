package io.argos.commun;

/** Motif d'un refus d'accès à une ressource d'espace. */
public enum MotifRefus {
    /** Le compte n'est pas membre de l'espace, ou la ressource n'existe pas : réponse « introuvable » (Q-4). */
    NON_MEMBRE,
    /** Le compte est membre mais son rôle ne suffit pas : réponse « interdit » (ACC-03-S2). */
    ROLE_INSUFFISANT
}
