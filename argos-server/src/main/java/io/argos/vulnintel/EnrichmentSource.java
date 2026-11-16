package io.argos.vulnintel;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Port d'une source d'enrichissement secondaire (ADR-0010) : NVD, CISA KEV, FIRST EPSS, CERT-FR. */
public interface EnrichmentSource {

    /** Identifiant de la source : NVD, KEV, EPSS ou CERTFR (contrainte de la table {@code enrichment}). */
    String id();

    /**
     * Données publiées pour une CVE ; liste vide si la source ne la connaît pas.
     *
     * @throws EnrichmentUnavailableException si la source ne répond pas correctement
     */
    List<Finding> lookup(String cve);

    /** Donnée d'enrichissement : référence chez la source (CVE ou avis) et contenu retenu. */
    record Finding(String reference, JsonNode data) {
    }
}
