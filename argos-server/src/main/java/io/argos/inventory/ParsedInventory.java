package io.argos.inventory;

import java.time.Instant;
import java.util.List;

/**
 * Inventaire extrait d'un document avant enregistrement.
 *
 * @param specVersion version du schéma CycloneDX, absente pour un manifeste
 * @param sourceDate date de production de la SBOM ({@code metadata.timestamp}), absente si inconnue
 * @param ignoredComponents composants écartés faute de PURL exploitable
 */
public record ParsedInventory(String specVersion, Instant sourceDate, List<Item> components, int ignoredComponents) {

    /** Composant : clé PURL sans version, PURL complet, version, caractère transitif ({@code null} : inconnu). */
    public record Item(String packageKey, String purl, String version, Boolean transitive) {
    }
}
