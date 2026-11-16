package io.argos.impact;

import java.util.Optional;

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
 * Une version hors de toutes les plages ne produit aucun impact (COR-01-S2).
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
        if (affected.versions() != null && affected.versions().contains(version)) {
            return RangeResult.IN_RANGE;
        }
        boolean unknown = false;
        for (AffectedPackage.VersionRange r : affected.ranges()) {
            if ("GIT".equalsIgnoreCase(r.type())) {
                unknown = true;
                continue;
            }
            RangeResult result = inInterval(r, version);
            if (result == RangeResult.IN_RANGE) {
                return result;
            }
            unknown |= result == RangeResult.UNKNOWN;
        }
        if (affected.ranges().isEmpty() && (affected.versions() == null || affected.versions().isEmpty())) {
            return RangeResult.UNKNOWN;
        }
        return unknown ? RangeResult.UNKNOWN : RangeResult.OUT_OF_RANGE;
    }

    private static RangeResult inInterval(AffectedPackage.VersionRange r, String version) {
        if (!VersionComparator.interpretable(version)) {
            return RangeResult.UNKNOWN;
        }
        String introduced = r.introduced();
        if (introduced != null && !introduced.isBlank() && !"0".equals(introduced)) {
            Optional<Integer> c = VersionComparator.compare(version, introduced);
            if (c.isEmpty()) {
                return RangeResult.UNKNOWN;
            }
            if (c.get() < 0) {
                return RangeResult.OUT_OF_RANGE;
            }
        }
        if (r.fixed() != null) {
            Optional<Integer> c = VersionComparator.compare(version, r.fixed());
            if (c.isEmpty()) {
                return RangeResult.UNKNOWN;
            }
            return c.get() < 0 ? RangeResult.IN_RANGE : RangeResult.OUT_OF_RANGE;
        }
        if (r.lastAffected() != null) {
            Optional<Integer> c = VersionComparator.compare(version, r.lastAffected());
            if (c.isEmpty()) {
                return RangeResult.UNKNOWN;
            }
            return c.get() <= 0 ? RangeResult.IN_RANGE : RangeResult.OUT_OF_RANGE;
        }
        return RangeResult.IN_RANGE;
    }
}
