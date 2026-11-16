package io.argos.vulnintel;

import java.net.URI;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** FIRST EPSS : {@code GET <url>?cve=<CVE>}, probabilité d'exploitation et percentile. */
@ApplicationScoped
public class EpssSource implements EnrichmentSource {

    @Inject
    EnrichmentHttp http;

    @ConfigProperty(name = "argos.sources.epss.url")
    String url;

    @Override
    public String id() {
        return "EPSS";
    }

    @Override
    public List<Finding> lookup(String cve) {
        return http.getJson(URI.create(url + "?cve=" + cve)).map(root -> {
            JsonNode items = root.path("data");
            if (!items.isArray() || items.isEmpty()) {
                return List.<Finding>of();
            }
            JsonNode item = items.get(0);
            ObjectNode data = EnrichmentHttp.MAPPER.createObjectNode();
            data.put("epss", item.path("epss").asDouble());
            data.put("percentile", item.path("percentile").asDouble());
            data.put("date", item.path("date").asText(null));
            return List.of(new Finding(cve, data));
        }).orElse(List.of());
    }
}
