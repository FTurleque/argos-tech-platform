package io.argos.signal.opportunite;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Source d'un profil de veille : jeu de données de data.culture.gouv.fr ou flux RSS public d'un
 * organisme (Q-1). Tant que ses conditions d'usage ne sont pas enregistrées, son état est
 * {@code CONDITIONS_A_VERIFIER} et elle n'est jamais collectée (ART-05).
 *
 * @param rubrique    rubrique du profil attribuée aux opportunités de la source
 * @param disciplines disciplines déclarées pour la source, appliquées quand un élément n'en précise pas
 * @param territoires territoires déclarés pour la source, appliqués quand un élément n'en précise pas
 */
public record SourceProfil(UUID id, String profil, String type, String adresse, String libelle, String rubrique,
        List<String> disciplines, List<String> territoires, String licence, String conditionsUsage,
        String conditionsUrl, UUID conditionsVerifieesPar, Instant conditionsVerifieesLe, String etat,
        String derniereErreur, Instant derniereCollecteLe, Instant creeLe) {

    public static final String DATA_CULTURE = "DATA_CULTURE";
    public static final String FLUX = "FLUX";

    public static final String CONDITIONS_A_VERIFIER = "CONDITIONS_A_VERIFIER";
    public static final String ACTIVE = "ACTIVE";
    public static final String EN_ERREUR = "EN_ERREUR";

    public SourceProfil {
        disciplines = List.copyOf(disciplines);
        territoires = List.copyOf(territoires);
    }

    /** Vrai si les conditions d'usage ont été vérifiées et enregistrées. */
    public boolean conditionsVerifiees() {
        return conditionsVerifieesLe != null;
    }
}
