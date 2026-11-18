package io.argos.inventory;

import java.time.Instant;
import java.util.List;

/**
 * Inventaire extrait d'un document avant enregistrement.
 *
 * @param specVersion version du schéma CycloneDX, absente pour un manifeste
 * @param sourceDate date de production de la SBOM ({@code metadata.timestamp}), absente si inconnue
 * @param ignoredComponents nombre de composants écartés faute de PURL exploitable
 * @param ignored composants écartés, signalés à l'import (INV-01, décision D6 du 2026-11-18), au plus
 *        {@link #MAX_SIGNALED} ; {@code ignoredComponents} donne leur nombre total
 */
public record ParsedInventory(String specVersion, Instant sourceDate, List<Item> components, int ignoredComponents,
        List<IgnoredComponent> ignored) {

    /** Nombre maximal de composants ignorés décrits par un import ; au-delà, seul le nombre total est conservé. */
    public static final int MAX_SIGNALED = 100;

    /** Composant : clé PURL sans version, PURL complet, version, caractère transitif ({@code null} : inconnu). */
    public record Item(String packageKey, String purl, String version, Boolean transitive) {
    }

    /**
     * Composant écarté de l'inventaire, tel que décrit par la SBOM.
     *
     * @param name nom ({@code name}), absent s'il n'est pas déclaré
     * @param group groupe ({@code group}), absent s'il n'est pas déclaré
     * @param version version ({@code version}), absente si elle n'est pas déclarée
     * @param reason {@link #SANS_PURL} (aucun PURL déclaré) ou {@link #PURL_INVALIDE} (PURL illisible)
     */
    public record IgnoredComponent(String name, String group, String version, String reason) {

        public static final String SANS_PURL = "SANS_PURL";
        public static final String PURL_INVALIDE = "PURL_INVALIDE";
    }

    /** Inventaire sans composant ignoré (manifestes). */
    public ParsedInventory(String specVersion, Instant sourceDate, List<Item> components) {
        this(specVersion, sourceDate, components, 0, List.of());
    }
}
