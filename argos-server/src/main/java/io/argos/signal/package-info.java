/**
 * Module {@code signal} (arc42 §5).
 *
 * <p>Signaux normalisés, taxonomie, déduplication, recherche plein texte de la veille ({@code signal.recherche}).
 *
 * <p>Dépendances autorisées : {@code ingestion}, {@code catalog}, {@code aigateway} (résumé IA des actualités web) et
 * les modules techniques transverses ({@code commun} : contexte RLS, JDBC, erreurs communes).
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.signal;
