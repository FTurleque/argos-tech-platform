/**
 * Module {@code reg-intel} (arc42 §5).
 *
 * <p>Textes du Journal officiel collectés, qualification par le dictionnaire métier de chaque espace,
 * dates de publication et d'entrée en vigueur, fiches préparées puis validées par un expert métier,
 * cycle de vie jusqu'à la livraison et demande d'évolution associée (capacité {@code veille-reglementaire}).
 *
 * <p>Dépendances autorisées : {@code signal}, {@code catalog}, {@code aigateway}, {@code ingestion}
 * (connecteur JORF), {@code scm} (demandes d'évolution GitHub et GitLab) et, dans {@code workspace},
 * le seul contexte RLS et les erreurs communes. Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.regintel;
