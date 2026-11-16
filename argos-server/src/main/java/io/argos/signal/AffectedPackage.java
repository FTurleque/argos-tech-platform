package io.argos.signal;

import java.util.List;

/**
 * Paquet affecté par un signal de sécurité, sous forme normalisée (COL-01-S1).
 *
 * @param packageKey clé PURL sans version (voir {@code io.argos.catalog.PackageKey})
 * @param declaredPurl vrai si la source a déclaré un PURL ; faux si la clé est déduite du nom
 *        et de l'écosystème, ce qui ne permet qu'une correspondance « à vérifier » (Q-2)
 * @param ranges intervalles de versions affectées
 * @param versions versions affectées énumérées par la source
 */
public record AffectedPackage(String packageKey, boolean declaredPurl, String ecosystem, String name,
        List<VersionRange> ranges, List<String> versions) {

    /**
     * Intervalle : {@code introduced} inclus (absent ou « 0 » : depuis l'origine),
     * {@code fixed} exclu, {@code lastAffected} inclus. {@code type} : SEMVER, ECOSYSTEM ou GIT.
     */
    public record VersionRange(String type, String introduced, String fixed, String lastAffected) {
    }
}
