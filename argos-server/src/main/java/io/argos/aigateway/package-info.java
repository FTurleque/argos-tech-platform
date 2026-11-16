/**
 * Module {@code ai-gateway} (arc42 §5).
 *
 * <p>Seul point d'appel aux modèles d'IA : prompts versionnés, classes de données, plafond et quotas,
 * validation des sorties, traçabilité et mention IA (changement {@code encadrer-appels-ia}). Contrat
 * public : {@link io.argos.aigateway.PasserelleIa}, {@link io.argos.aigateway.DemandeIa},
 * {@link io.argos.aigateway.ReponseIa}. Le fournisseur local Ollama ({@code aigateway.ollama}) n'est
 * connu que de ce module (règle {@code seuleLaPasserelleConnaitLeFournisseur}).
 *
 * <p>Dépendances autorisées : {@code audit}.
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.aigateway;
