package io.argos.catalog.standard;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture du référentiel des standards technologiques des espaces (module {@code catalog},
 * arc42 §5 ; capacité {@code radar-standards}).
 *
 * <p>Point d'extension en lecture seule utilisé par le serveur MCP ({@code get_technology_standard},
 * {@code check_dependency}). Implémentation : {@link ReferentielStandardsEnBase}, qui lit le
 * référentiel tenu par chaque espace ({@link ReferentielEspace}).
 */
public interface ReferentielStandards {

    /**
     * Statut d'une technologie au référentiel (RAD-01), selon le principe d'un radar technologique :
     * {@code ASSESS} à étudier, {@code TRIAL} projets pilotes, {@code ADOPT} standard recommandé,
     * {@code HOLD} ne plus introduire.
     */
    enum Statut { ADOPT, TRIAL, ASSESS, HOLD }

    /**
     * Standard d'une technologie dans un espace.
     *
     * @param espaceId            espace propriétaire du standard
     * @param cle                 clé de paquet (PURL sans version, {@code io.argos.catalog.PackageKey})
     * @param statut              statut au référentiel
     * @param versionRecommandee  version recommandée, absente si l'espace n'en fixe pas
     * @param justification       justification saisie par l'espace
     */
    record Standard(UUID espaceId, String cle, Statut statut, String versionRecommandee, String justification) {
    }

    /**
     * Standards de la technologie {@code cle} dans les espaces donnés, et seulement dans ceux-là ;
     * liste vide si aucun de ces espaces ne la référence.
     */
    List<Standard> standards(String cle, Set<UUID> espaces);
}
