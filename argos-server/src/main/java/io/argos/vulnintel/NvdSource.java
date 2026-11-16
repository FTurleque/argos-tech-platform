package io.argos.vulnintel;

import java.net.URI;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** NVD, API CVE 2.0 : {@code GET <url>?cveId=<CVE>}. Retient description, score et sévérité CVSS. */
@ApplicationScoped
public class NvdSource implements EnrichmentSource {

    @Inject
    EnrichmentHttp http;

    @ConfigProperty(name = "argos.sources.nvd.url")
    String url;

    @Override
    public String id() {
        return "NVD";
    }

    @Override
    public List<Finding> lookup(String cve) {
        return http.getJson(URI.create(url + "?cveId=" + cve)).map(root -> {
            JsonNode vulns = root.path("vulnerabilities");
            if (!vulns.isArray() || vulns.isEmpty()) {
                return List.<Finding>of();
            }
            JsonNode item = vulns.get(0).path("cve");
            ObjectNode data = EnrichmentHttp.MAPPER.createObjectNode();
            data.put("published", item.path("published").asText(null));
            data.put("lastModified", item.path("lastModified").asText(null));
            for (JsonNode d : item.path("descriptions")) {
                if ("en".equals(d.path("lang").asText())) {
                    data.put("description", d.path("value").asText());
                }
            }
            JsonNode metrics = item.path("metrics");
            for (String key : List.of("cvssMetricV40", "cvssMetricV31", "cvssMetricV30", "cvssMetricV2")) {
                JsonNode m = metrics.path(key);
                if (m.isArray() && !m.isEmpty()) {
                    JsonNode cvss = m.get(0).path("cvssData");
                    data.put("cvssVersion", cvss.path("version").asText(null));
                    data.put("baseScore", cvss.path("baseScore").asDouble());
                    data.put("baseSeverity", cvss.path("baseSeverity").asText(m.get(0).path("baseSeverity").asText(null)));
                    break;
                }
            }
            return List.of(new Finding(cve, data));
        }).orElse(List.of());
    }
}
