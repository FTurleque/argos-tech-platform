package io.argos.catalog.standard;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture du référentiel des standards technologiques des espaces (module {@code catalog},
 * arc42 §5 ; capacité {@code radar-standards}).
 *
 * <p>Point d'extension en lecture seule utilisé par le serveur MCP ({@code get_technology_standard},
 * {@code check_dependency}). Le référentiel lui-même (saisie, validation, historique) relève du
 * changement {@code construire-radar-et-standards} ; tant qu'il n'est pas livré, l'implémentation
 * par défaut {@link ReferentielStandardsNonRenseigne} ne connaît aucun standard.
 */
public interface ReferentielStandards {

    /** Statut d'une technologie au référentiel d'un espace. */
    enum Statut { ADOPTE, A_ESSAYER, A_EVALUER, A_EVITER }

    /**
     * Standard d'une technologie dans un espace.
     *
     * @param espaceId            espace propriétaire du standard
     * @param cle                 clé de paquet (PURL sans version, {@code io.argos.catalog.PackageKey})
     * @param statut              statut au référentiel
     * @param versionRecommandee  version recommandée, absente si l'espace n'en fixe pas
     * @param justification       justification saisie par l'espace, absente sinon
     */
    record Standard(UUID espaceId, String cle, Statut statut, String versionRecommandee, String justification) {
    }

    /**
     * Standards de la technologie {@code cle} dans les espaces donnés, et seulement dans ceux-là ;
     * liste vide si aucun de ces espaces ne la référence.
     */
    List<Standard> standards(String cle, Set<UUID> espaces);
}
