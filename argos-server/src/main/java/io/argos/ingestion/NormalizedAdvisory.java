package io.argos.ingestion;

import java.time.Instant;
import java.util.List;

/**
 * Avis normalisé, indépendant du format de la source (COL-01, ADR-0003).
 *
 * @param externalId identifiant de l'avis chez la source, obligatoire (COL-02)
 * @param modifiedAt date de dernière modification déclarée par la source
 * @param contentHash empreinte SHA-256 du contenu brut, qui détecte une modification (COL-03)
 */
public record NormalizedAdvisory(
        String externalId,
        String title,
        String summary,
        String severity,
        Instant publishedAt,
        Instant modifiedAt,
        List<String> aliases,
        List<Affected> affected,
        String rawContent,
        String contentHash) {

    /**
     * Paquet affecté. {@code purl} est le PURL déclaré par la source, absent si elle ne
     * fournit que l'écosystème et le nom.
     */
    public record Affected(String ecosystem, String name, String purl, List<Range> ranges, List<String> versions) {
    }

    /**
     * Intervalle de versions affectées : {@code introduced} inclus, {@code fixed} exclu,
     * {@code lastAffected} inclus. {@code type} vaut SEMVER, ECOSYSTEM ou GIT.
     */
    public record Range(String type, String introduced, String fixed, String lastAffected) {
    }
}
