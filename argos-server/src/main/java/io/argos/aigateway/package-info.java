/**
 * Module {@code ai-gateway} (arc42 §5).
 *
 * <p>Seul point d'appel aux modèles d'IA : prompts versionnés, quotas, coûts, validation des sorties (ADR-0004).
 *
 * <p>Dépendances autorisées : {@code audit}.
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.aigateway;
