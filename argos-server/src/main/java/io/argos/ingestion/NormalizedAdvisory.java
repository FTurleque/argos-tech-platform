package io.argos.ingestion;

import java.time.Instant;
import java.util.List;

/**
 * Avis normalisé, indépendant du format de la source (COL-01, ADR-0003).
 *
 * @param externalId identifiant de l'avis chez la source, obligatoire (COL-02)
 * @param modifiedAt date de dernière modification déclarée par la source
 * @param withdrawnAt date de retrait déclarée par la source, absente si l'avis n'est pas retiré ; un avis
 *        retiré ne déclare plus aucun paquet affecté
 * @param contentHash empreinte SHA-256 du contenu brut, conservée pour l'audit ; la modification d'un avis
 *        se juge sur l'empreinte du contenu normalisé ({@link #normalizedHash()}, COL-10)
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
        Instant withdrawnAt,
        String rawContent,
        String contentHash) {

    /**
     * Empreinte SHA-256 du contenu normalisé (COL-10) : identique pour deux contenus bruts qui ne diffèrent
     * que par la présentation ou la date de modification déclarée par la source ({@link EmpreinteNormalisee}).
     */
    public String normalizedHash() {
        return EmpreinteNormalisee.calculer(this);
    }

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
