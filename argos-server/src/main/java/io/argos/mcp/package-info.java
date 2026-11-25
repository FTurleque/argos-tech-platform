/**
 * Module {@code mcp-server} (arc42 §5).
 *
 * <p>Serveur MCP intégré, en lecture seule, transport Streamable HTTP sur {@code POST /mcp} (ADR-0014,
 * changement {@code exposer-serveur-mcp}) : sans session en protocole 2026-07-28 ; une version
 * antérieure négociée par {@code initialize} ouvre une session, dont les négociations sont limitées par
 * {@link io.argos.mcp.ControleInitialisation}. Huit outils de consultation ; chaque appel
 * passe par {@link io.argos.mcp.GardeAppels} : appelant et espaces établis comme pour le portail,
 * limitation de débit, journal des appels, refus journalisés. Le contenu externe est renvoyé dans
 * des champs {@link io.argos.mcp.TexteExterne} balisés et tronqués. Ne modifie jamais le domaine.
 *
 * <p>Dépendances autorisées : {@code workspace}, {@code catalog}, {@code inventory}, {@code signal},
 * {@code vulnintel}, {@code releaseradar}, {@code regintel}, {@code impact}, {@code alerting},
 * {@code projectintel} et {@code audit}.
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.mcp;
