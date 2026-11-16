package io.argos.ingestion;

/** Contenu brut d'un avis tel que reçu de la source, conservé pour la provenance (COL-02). */
public record RawAdvisory(String externalId, String content) {
}
