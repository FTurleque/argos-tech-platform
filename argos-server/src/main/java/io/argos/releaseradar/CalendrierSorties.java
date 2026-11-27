package io.argos.releaseradar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture du calendrier des sorties, correctifs et fins de support (module {@code release-radar},
 * arc42 §5 ; capacité {@code radar-standards}).
 *
 * <p>Point d'extension en lecture seule utilisé par le radar, la dette de version et le serveur MCP
 * ({@code upcoming_releases}, {@code check_dependency}). Implémentation : {@link CalendrierEndoflife}.
 * Une technologie n'a d'échéances que si l'un des espaces demandés la relie à un produit du calendrier
 * dans son référentiel : la correspondance d'un autre espace n'est jamais utilisée.
 */
public interface CalendrierSorties {

    /** Nature d'une échéance. */
    enum Nature { SORTIE, CORRECTIF, FIN_DE_SUPPORT }

    /**
     * Échéance d'une technologie.
     *
     * @param cle      clé de paquet (PURL sans version)
     * @param version  version ou branche concernée
     * @param nature   sortie, correctif ou fin de support
     * @param date     date de l'échéance
     * @param source   source du calendrier (identifiant court, par exemple {@code endoflife.date})
     * @param note     texte libre fourni par la source, absent sinon (contenu externe)
     */
    record Echeance(String cle, String version, Nature nature, LocalDate date, String source, String note) {
    }

    /**
     * Échéances des clés données comprises entre {@code depuis} et {@code jusqua} inclus, par date
     * croissante, selon les correspondances du référentiel des espaces donnés.
     */
    List<Echeance> echeances(Collection<String> cles, Set<UUID> espaces, LocalDate depuis, LocalDate jusqua);

    /** Fin de support connue de la version {@code version} de la clé {@code cle}, selon les espaces donnés. */
    Optional<Echeance> finDeSupport(String cle, String version, Set<UUID> espaces);

    /**
     * Fins de support des clés données selon les espaces donnés, lues une fois (RAD-10) : la fin de support de chaque
     * version est ensuite évaluée en mémoire, comme {@link #finDeSupport}.
     */
    FinsDeSupport finsDeSupport(Collection<String> cles, Set<UUID> espaces);

    /** Fins de support chargées pour un ensemble de clés : évaluation d'une version, sans lecture. */
    @FunctionalInterface
    interface FinsDeSupport {

        /** Fin de support connue de la version de la clé ; vide pour une clé non chargée. */
        Optional<Echeance> pour(String cle, String version);
    }
}
