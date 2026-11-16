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
}
