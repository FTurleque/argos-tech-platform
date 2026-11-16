package io.argos.ingestion;

/**
 * Port d'enregistrement des avis normalisés, implémenté par le module {@code signal}.
 *
 * <p>L'appel a lieu dans la transaction qui avance le curseur de la source (COL-05, INV-D5).
 */
public interface AdvisorySink {

    /** Effet de l'enregistrement d'un avis. */
    enum Outcome { CREATED, UPDATED, UNCHANGED, IGNORED_OLDER }

    Outcome record(String sourceId, NormalizedAdvisory advisory);
}
