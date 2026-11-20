package io.argos.vulnintel;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Catalogue CISA KEV (vulnérabilités exploitées) : le fichier JSON public est téléchargé puis
 * conservé en mémoire pendant {@code argos.sources.kev.cache-ttl}. Une CVE présente indique
 * l'exploitation avérée (COL-04-S1).
 */
@ApplicationScoped
public class KevSource implements EnrichmentSource {

    @Inject
    EnrichmentHttp http;

    @ConfigProperty(name = "argos.sources.kev.url")
    String url;

    @ConfigProperty(name = "argos.sources.kev.cache-ttl", defaultValue = "PT6H")
    Duration cacheTtl;

    private Map<String, JsonNode> catalog;
    private Instant loadedAt;

    @Override
    public String id() {
        return "KEV";
    }

    @Override
    public List<Finding> lookup(String cve) {
        JsonNode entry = catalog().get(cve);
        if (entry == null) {
            return List.of();
        }
        ObjectNode data = EnrichmentHttp.MAPPER.createObjectNode();
        data.put("exploited", true);
        data.put("dateAdded", entry.path("dateAdded").asText(null));
        data.put("knownRansomwareCampaignUse", entry.path("knownRansomwareCampaignUse").asText(null));
        data.put("requiredAction", entry.path("requiredAction").asText(null));
        data.put("dueDate", entry.path("dueDate").asText(null));
        return List.of(new Finding(cve, data));
    }

    /** Vide le cache (tests, rechargement forcé). */
    public synchronized void invalidate() {
        catalog = null;
    }

    private synchronized Map<String, JsonNode> catalog() {
        if (catalog != null && loadedAt.plus(cacheTtl).isAfter(Instant.now())) {
            return catalog;
        }
        Optional<JsonNode> root = http.getJson(URI.create(url));
        Map<String, JsonNode> index = new HashMap<>();
        root.ifPresent(document -> document.path("vulnerabilities").forEach(entry -> index.put(entry.path("cveID").asText(), entry)));
        catalog = index;
        loadedAt = Instant.now();
        return catalog;
    }
}
