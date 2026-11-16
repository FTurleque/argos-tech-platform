package io.argos.releaseradar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lecture du calendrier des sorties et des fins de support (module {@code release-radar}, arc42 §5 ;
 * capacité {@code radar-standards}).
 *
 * <p>Point d'extension en lecture seule utilisé par le serveur MCP ({@code upcoming_releases},
 * {@code check_dependency}). Le connecteur de calendriers (endoflife.date) relève du changement
 * {@code construire-radar-et-standards} ; tant qu'il n'est pas livré, l'implémentation par défaut
 * {@link CalendrierSortiesNonRenseigne} ne connaît aucune échéance.
 */
public interface CalendrierSorties {

    /** Nature d'une échéance. */
    enum Nature { SORTIE, FIN_DE_SUPPORT }

    /**
     * Échéance d'une technologie.
     *
     * @param cle      clé de paquet (PURL sans version)
     * @param version  version ou branche concernée
     * @param nature   sortie ou fin de support
     * @param date     date de l'échéance
     * @param source   source du calendrier (identifiant court, par exemple {@code endoflife.date})
     * @param note     texte libre fourni par la source, absent sinon (contenu externe)
     */
    record Echeance(String cle, String version, Nature nature, LocalDate date, String source, String note) {
    }

    /** Échéances des clés données comprises entre {@code depuis} et {@code jusqua} inclus, par date croissante. */
    List<Echeance> echeances(Collection<String> cles, LocalDate depuis, LocalDate jusqua);

    /** Fin de support connue de la version {@code version} de la clé {@code cle}. */
    Optional<Echeance> finDeSupport(String cle, String version);
}
