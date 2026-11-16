package io.argos.ingestion.osv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.ingestion.AdvisoryRejectedException;
import io.argos.ingestion.NormalizedAdvisory;
import io.argos.ingestion.NormalizedAdvisory.Affected;
import io.argos.ingestion.NormalizedAdvisory.Range;
import io.argos.ingestion.RawAdvisory;

/**
 * Traduit un avis au format OSV (schéma OSV 1.x) en {@link NormalizedAdvisory}.
 * Le format OSV ne sort pas de ce paquet (COL-01, arc42 §5.5).
 */
public final class OsvNormalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OsvNormalizer() {
    }

    public static NormalizedAdvisory normalize(RawAdvisory raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw.content());
        } catch (JsonProcessingException e) {
            throw new AdvisoryRejectedException("contenu JSON illisible", e);
        }
        if (root == null || !root.isObject()) {
            throw new AdvisoryRejectedException("l'avis n'est pas un objet JSON");
        }
        String id = text(root, "id");
        if (id == null || id.isBlank()) {
            throw new AdvisoryRejectedException("identifiant externe absent");
        }
        Instant modified = instant(root, "modified", true);
        Instant published = instant(root, "published", false);

        List<String> aliases = new ArrayList<>();
        root.path("aliases").forEach(a -> {
            if (a.isTextual() && !a.asText().isBlank()) {
                aliases.add(a.asText());
            }
        });

        JsonNode affectedNode = root.path("affected");
        if (!affectedNode.isMissingNode() && !affectedNode.isArray()) {
            throw new AdvisoryRejectedException("champ affected non conforme");
        }
        List<Affected> affected = new ArrayList<>();
        for (JsonNode a : affectedNode) {
            affected.add(affected(a));
        }
        // Avis retiré (champ OSV « withdrawn ») : le signal et son historique sont conservés, mais il
        // ne déclare plus aucun paquet affecté ; la corrélation retire donc les impacts qu'il portait.
        JsonNode withdrawn = root.path("withdrawn");
        if (!withdrawn.isMissingNode() && !withdrawn.isNull()) {
            affected.clear();
        }

        String summary = text(root, "summary");
        String details = text(root, "details");
        return new NormalizedAdvisory(id, summary, details, severity(root), published, modified,
                List.copyOf(aliases), List.copyOf(affected), raw.content(), sha256(raw.content()));
    }

    private static Affected affected(JsonNode a) {
        JsonNode pkg = a.path("package");
        String ecosystem = text(pkg, "ecosystem");
        String name = text(pkg, "name");
        String purl = text(pkg, "purl");
        if ((name == null || name.isBlank()) && (purl == null || purl.isBlank())) {
            throw new AdvisoryRejectedException("paquet affecté sans nom ni PURL");
        }
        List<Range> ranges = new ArrayList<>();
        for (JsonNode r : a.path("ranges")) {
            String type = text(r, "type");
            String introduced = null;
            boolean open = false;
            for (JsonNode e : r.path("events")) {
                if (e.has("introduced")) {
                    introduced = e.get("introduced").asText();
                    open = true;
                } else if (e.has("fixed") && open) {
                    ranges.add(new Range(type, introduced, e.get("fixed").asText(), null));
                    open = false;
                } else if (e.has("last_affected") && open) {
                    ranges.add(new Range(type, introduced, null, e.get("last_affected").asText()));
                    open = false;
                }
            }
            if (open) {
                ranges.add(new Range(type, introduced, null, null));
            }
        }
        List<String> versions = new ArrayList<>();
        a.path("versions").forEach(v -> versions.add(v.asText()));
        return new Affected(ecosystem, name, purl, List.copyOf(ranges), List.copyOf(versions));
    }

    private static String severity(JsonNode root) {
        String declared = text(root.path("database_specific"), "severity");
        if (declared != null) {
            return declared;
        }
        List<String> parts = new ArrayList<>();
        root.path("severity").forEach(s -> parts.add(text(s, "type") + ":" + text(s, "score")));
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static Instant instant(JsonNode node, String field, boolean required) {
        String value = text(node, field);
        if (value == null) {
            if (required) {
                throw new AdvisoryRejectedException("date " + field + " absente");
            }
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new AdvisoryRejectedException("date " + field + " illisible", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
