/**
 * Module {@code catalog} (arc42 §5).
 *
 * <p>Technologies, versions, standards internes (radar), domaines métier et modules produits ;
 * catalogue des profils de veille ({@code profil}) et des mises en page ({@code miseenpage}).
 *
 * <p>Dépendances autorisées : {@code audit} ; {@code workspace} pour le seul référentiel des standards
 * ({@code standard} : contexte RLS et erreurs communes, règle {@code catalogNEmprunteQueRlsEtErreurs}).
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.catalog;
