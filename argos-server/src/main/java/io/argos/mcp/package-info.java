/**
 * Module {@code mcp-server} (arc42 §5).
 *
 * <p>Outils MCP en lecture seule (ADR-0014). Ne modifie jamais le domaine au MVP.
 *
 * <p>Dépendances autorisées : {@code workspace}, {@code catalog}, {@code inventory}, {@code signal},
 * {@code vulnintel}, {@code releaseradar},
 * {@code regintel}, {@code impact}, {@code alerting}, {@code projectintel}, {@code audit}.
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.mcp;
