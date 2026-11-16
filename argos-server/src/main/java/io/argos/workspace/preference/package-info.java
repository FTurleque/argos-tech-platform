/**
 * Préférences d'affichage et de veille d'un utilisateur, dans le module {@code workspace}
 * (ADR-0023 point 3, ADR-0024 point 2, exigences AFF-03 et PRF-01).
 *
 * <p>Une préférence décrit ce que l'utilisateur veut voir et comment : sa mise en page et
 * ses profils de veille actifs. Elle ne figure jamais dans une décision d'autorisation
 * (INV-3, PRF-05) : seuls {@code api} et {@code mcp} lisent ce paquetage, et le reste de
 * {@code workspace} n'en dépend pas. Vérifié par {@code PreferencesIsolationTest}.
 *
 * <p>Ce paquetage ne connaît pas le catalogue : vérifier qu'un identifiant est publié
 * relève de l'appelant ({@code api}), seul autorisé à dépendre à la fois de
 * {@code workspace} et de {@code catalog} (arc42 §5).
 */
package io.argos.workspace.preference;
