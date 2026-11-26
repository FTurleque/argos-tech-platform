package io.argos.ingestion;

import java.util.List;

/**
 * Port d'une source d'avis de vulnérabilité (adapter externe, arc42 §5.5).
 *
 * <p>Les méthodes d'accès à la source lèvent {@link SourceException} pour une erreur de la
 * source ; toute autre exception est un défaut du programme et interrompt la synchronisation
 * sans la clore (elle est reprise après expiration, arc42 §6.4).
 */
public interface AdvisoryFeed {

    /** Identifiant de la source, clé de la table {@code source}. */
    String sourceId();

    /** Avis publiés ou modifiés après la position donnée, dans l'ordre {@link FeedEntry#ORDER}. */
    List<FeedEntry> changesSince(String position);

    /** Contenu brut d'un avis annoncé. */
    RawAdvisory fetch(FeedEntry entry);

    /** Normalise le contenu brut ; lève {@link AdvisoryRejectedException} s'il n'est pas exploitable. */
    NormalizedAdvisory normalize(RawAdvisory raw);

    /**
     * Lecture des avis postérieurs à la position, fermée à la fin de la synchronisation : une source peut y tenir des
     * ressources (archive téléchargée). Par défaut : {@link #changesSince(String)} puis {@link #fetch(FeedEntry)}.
     */
    default Lecture lire(String position) {
        List<FeedEntry> entrees = changesSince(position);
        return new Lecture() {
            @Override
            public List<FeedEntry> entrees() {
                return entrees;
            }

            @Override
            public RawAdvisory avis(FeedEntry entree) {
                return fetch(entree);
            }

            @Override
            public void close() {
                // Aucune ressource tenue par la lecture par défaut.
            }
        };
    }

    /** Lecture en cours : avis annoncés, contenu de chacun, ressources libérées à la fermeture. */
    interface Lecture extends AutoCloseable {

        /** Avis annoncés, dans l'ordre {@link FeedEntry#ORDER}. */
        List<FeedEntry> entrees();

        /** Contenu brut d'un avis annoncé ; lève {@link SourceException} pour une erreur de la source. */
        RawAdvisory avis(FeedEntry entree);

        /** Libère les ressources de la lecture ; sans exception vérifiée. */
        @Override
        void close();
    }
}
