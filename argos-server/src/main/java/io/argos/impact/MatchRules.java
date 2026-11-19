package io.argos.impact;

import java.util.Optional;
import java.util.function.IntPredicate;

import io.argos.signal.AffectedPackage;

/**
 * Règles déterministes de rapprochement et de niveau (COR-01, COR-02, COR-06 ; décision du
 * 2026-11-16, Q-2 et Q-3) :
 *
 * <ul>
 *   <li>{@code EXACT} : PURL déclaré par l'avis et version comprise dans la plage affectée, sur un
 *       inventaire issu d'une SBOM non obsolète (moins de 30 jours après sa génération) ;</li>
 *   <li>{@code PROBABLE} : même correspondance, mais inventaire de repli (manifestes) ou SBOM obsolète ;</li>
 *   <li>{@code A_VERIFIER} : correspondance sans PURL déclaré (nom seul), version du composant
 *       inconnue ou plage non interprétable.</li>
 * </ul>
 * Une version hors de toutes les plages ne produit aucun impact (COR-01-S2). Pour un paquet Maven,
 * toute comparaison suit l'ordre de {@code ComparableVersion} (décision E3 du porteur du projet,
 * 2026-11-19, qui révise D5 du 2026-11-18, Q-COR-3) : {@code 32.0.0-jre} suit la borne
 * {@code 32.0.0-android}.
 */
public final class MatchRules {

    /** Niveau d'un impact (ADR-0013). */
    public enum Level { EXACT, PROBABLE, A_VERIFIER }

    /** Méthode d'analyse enregistrée avec chaque impact (COR-03). */
    public static final String METHOD = "PURL_RANGE_V1";

    /**
     * Identifiant de cette stratégie de rapprochement dans les manifestes de profils (PRF-03-S2) :
     * le profil {@code developpeur} la déclare ; elle est déterministe et n'appelle aucune IA.
     */
    public static final String STRATEGY = "purl-plage-versions";

    /** Stratégies de rapprochement des profils que le moteur d'impact sait exécuter. */
    public static final java.util.Set<String> IMPLEMENTED_STRATEGIES = java.util.Set.of(STRATEGY);

    /** Résultat de l'évaluation d'une version par rapport aux plages d'un paquet. */
    enum RangeResult { IN_RANGE, OUT_OF_RANGE, UNKNOWN }

    private MatchRules() {
    }

    /**
     * Niveau de l'impact d'un paquet affecté sur un composant de même clé, ou vide si la version
     * du composant est hors de la plage affectée.
     */
    public static Optional<Level> evaluate(AffectedPackage affected, String componentVersion, boolean inventoryFromFreshSbom) {
        RangeResult range = inRange(affected, componentVersion);
        if (range == RangeResult.OUT_OF_RANGE) {
            return Optional.empty();
        }
        if (range == RangeResult.UNKNOWN || !affected.declaredPurl()) {
            return Optional.of(Level.A_VERIFIER);
        }
        return Optional.of(inventoryFromFreshSbom ? Level.EXACT : Level.PROBABLE);
    }

    /**
     * Version affectée par le paquet : vrai si elle est dans une plage ou une version énumérée,
     * faux si elle est hors de toutes les plages, vide si les plages ne permettent pas de conclure
     * (version inconnue, plage GIT ou non interprétable). Même règle que la corrélation (COR-01).
     */
    public static Optional<Boolean> versionAffected(AffectedPackage affected, String version) {
        return switch (inRange(affected, version)) {
            case IN_RANGE -> Optional.of(true);
            case OUT_OF_RANGE -> Optional.of(false);
            case UNKNOWN -> Optional.empty();
        };
    }

    static RangeResult inRange(AffectedPackage affected, String version) {
        if (version == null || version.isBlank()) {
            return RangeResult.UNKNOWN;
        }
        if (enumerated(affected, version)) {
            return RangeResult.IN_RANGE;
        }
        boolean unknown = false;
        for (AffectedPackage.VersionRange range : affected.ranges()) {
            RangeResult result = "GIT".equalsIgnoreCase(range.type()) ? RangeResult.UNKNOWN : inInterval(affected, range, version);
            if (result == RangeResult.IN_RANGE) {
                return result;
            }
            unknown |= result == RangeResult.UNKNOWN;
        }
        return unknown || declaresNoVersion(affected) ? RangeResult.UNKNOWN : RangeResult.OUT_OF_RANGE;
    }

    /** Paquet sans plage ni version énumérée : rien ne permet de conclure. */
    private static boolean declaresNoVersion(AffectedPackage affected) {
        return affected.ranges().isEmpty() && (affected.versions() == null || affected.versions().isEmpty());
    }

    /**
     * Version énumérée par le paquet. Pour Maven, l'égalité suit aussi {@code ComparableVersion} (décision E3) :
     * {@code 1.0} énumérée correspond à {@code 1.0.0}. Une chaîne identique correspond dans tous les cas.
     */
    private static boolean enumerated(AffectedPackage affected, String version) {
        if (affected.versions() == null || affected.versions().isEmpty()) {
            return false;
        }
        if (affected.versions().contains(version)) {
            return true;
        }
        String ecosystem = ecosystem(affected);
        if (!VersionComparator.maven(ecosystem)) {
            return false;
        }
        for (String listed : affected.versions()) {
            if (VersionComparator.compare(ecosystem, version, listed).filter(order -> order == 0).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Version dans un intervalle {@code [introduced, fixed)} ou {@code [introduced, lastAffected]} ;
     * UNKNOWN si la version ou une borne n'est pas interprétable dans l'écosystème du paquet.
     */
    private static RangeResult inInterval(AffectedPackage affected, AffectedPackage.VersionRange range, String version) {
        String ecosystem = ecosystem(affected);
        if (!interpretable(ecosystem, version)) {
            return RangeResult.UNKNOWN;
        }
        RangeResult lowerBound = fromIntroduced(ecosystem, version, range.introduced());
        return lowerBound == RangeResult.IN_RANGE ? beforeUpperBound(ecosystem, version, range) : lowerBound;
    }

    /** Version interprétable : au sens de {@code ComparableVersion} pour Maven, de l'ordre générique sinon. */
    private static boolean interpretable(String ecosystem, String version) {
        return VersionComparator.maven(ecosystem)
                ? VersionComparator.mavenInterpretable(version)
                : VersionComparator.interpretable(version);
    }

    /** Borne basse incluse ; une introduction absente ou {@code 0} admet toute version. */
    private static RangeResult fromIntroduced(String ecosystem, String version, String introduced) {
        if (introduced == null || introduced.isBlank() || "0".equals(introduced)) {
            return RangeResult.IN_RANGE;
        }
        return position(ecosystem, version, introduced, order -> order >= 0);
    }

    /** Borne haute : version corrigée exclue, sinon dernière version affectée incluse, sinon plage ouverte. */
    private static RangeResult beforeUpperBound(String ecosystem, String version, AffectedPackage.VersionRange range) {
        if (range.fixed() != null) {
            return position(ecosystem, version, range.fixed(), order -> order < 0);
        }
        if (range.lastAffected() != null) {
            return position(ecosystem, version, range.lastAffected(), order -> order <= 0);
        }
        return RangeResult.IN_RANGE;
    }

    /** Position de la version par rapport à une borne : IN_RANGE si l'ordre satisfait la condition, UNKNOWN si incomparable. */
    private static RangeResult position(String ecosystem, String version, String bound, IntPredicate inside) {
        return VersionComparator.compare(ecosystem, version, bound)
                .map(order -> inside.test(order) ? RangeResult.IN_RANGE : RangeResult.OUT_OF_RANGE)
                .orElse(RangeResult.UNKNOWN);
    }

    /** Écosystème du paquet : nom déclaré par la source, sinon type de la clé PURL ({@code pkg:maven/…}). */
    private static String ecosystem(AffectedPackage affected) {
        if (affected.ecosystem() != null && !affected.ecosystem().isBlank()) {
            return affected.ecosystem();
        }
        String key = affected.packageKey();
        if (key != null && key.startsWith("pkg:")) {
            int slash = key.indexOf('/');
            return slash > 4 ? key.substring(4, slash) : null;
        }
        return null;
    }
}
