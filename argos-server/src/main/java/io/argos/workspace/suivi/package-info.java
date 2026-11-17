/**
 * Objets suivis par un utilisateur pour un profil de veille, dans le module {@code workspace}
 * (ADR-0024 points 1 et 2, exigence ART-02) : par exemple les disciplines et les territoires du profil
 * {@code artiste}, et ses jours de rappel avant une date limite (ART-04).
 *
 * <p>Un suivi décrit ce que l'utilisateur veut voir ; il ne figure dans aucune décision d'autorisation
 * (PRF-05). Ce paquetage ne lit ni rôles ni appartenances : il ne dépend que du contexte RLS
 * ({@code workspace.ContexteBase}). Vérifié par {@code SuiviIsolationTest}.
 *
 * <p>Ce paquetage ne connaît pas le catalogue : confronter une valeur aux listes du manifeste relève
 * de l'appelant ({@code api}).
 */
package io.argos.workspace.suivi;
