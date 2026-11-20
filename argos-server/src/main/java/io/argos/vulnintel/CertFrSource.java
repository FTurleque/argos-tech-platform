package io.argos.vulnintel;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Avis du CERT-FR. Le flux RSS des avis fournit les références récentes ({@code CERTFR-AAAA-AVI-NNNN}) ;
 * le contenu JSON de chaque avis, lu à l'adresse {@code <base-url>/<référence>/json/} construite à
 * partir de la seule référence (aucun lien du flux n'est suivi, R-12), liste les CVE traitées.
 * L'index CVE → avis est conservé pendant {@code argos.sources.certfr.cache-ttl}.
 */
@ApplicationScoped
public class CertFrSource implements EnrichmentSource {

    private static final Pattern REFERENCE = Pattern.compile("CERTFR-\\d{4}-AVI-\\d{3,5}");

    @Inject
    EnrichmentHttp http;

    @ConfigProperty(name = "argos.sources.certfr.feed-url")
    String feedUrl;

    @ConfigProperty(name = "argos.sources.certfr.base-url")
    String baseUrl;

    @ConfigProperty(name = "argos.sources.certfr.cache-ttl", defaultValue = "PT6H")
    Duration cacheTtl;

    private Map<String, List<Finding>> index;
    private Instant loadedAt;

    @Override
    public String id() {
        return "CERTFR";
    }

    @Override
    public List<Finding> lookup(String cve) {
        return index().getOrDefault(cve, List.of());
    }

    public synchronized void invalidate() {
        index = null;
    }

    private synchronized Map<String, List<Finding>> index() {
        if (index != null && loadedAt.plus(cacheTtl).isAfter(Instant.now())) {
            return index;
        }
        String feed = http.get(URI.create(feedUrl)).orElse("");
        Set<String> references = new LinkedHashSet<>();
        Matcher matcher = REFERENCE.matcher(feed);
        while (matcher.find()) {
            references.add(matcher.group());
        }
        Map<String, List<Finding>> built = new HashMap<>();
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        for (String reference : references) {
            http.getJson(URI.create(base + reference + "/json/")).ifPresent(avis -> {
                ObjectNode data = EnrichmentHttp.MAPPER.createObjectNode();
                data.put("reference", reference);
                data.put("title", avis.path("title").asText(null));
                data.put("url", base + reference + "/");
                for (JsonNode cve : avis.path("cves")) {
                    String name = cve.path("name").asText();
                    if (!name.isBlank()) {
                        built.computeIfAbsent(name, key -> new ArrayList<>()).add(new Finding(reference, data));
                    }
                }
            });
        }
        index = built;
        loadedAt = Instant.now();
        return index;
    }
}
