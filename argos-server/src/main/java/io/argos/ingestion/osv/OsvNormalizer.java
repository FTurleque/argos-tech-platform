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
        } catch (JsonProcessingException error) {
            throw new AdvisoryRejectedException("contenu JSON illisible", error);
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
        root.path("aliases").forEach(alias -> {
            if (alias.isTextual() && !alias.asText().isBlank()) {
                aliases.add(alias.asText());
            }
        });

        JsonNode affectedNode = root.path("affected");
        if (!affectedNode.isMissingNode() && !affectedNode.isArray()) {
            throw new AdvisoryRejectedException("champ affected non conforme");
        }
        List<Affected> affected = new ArrayList<>();
        for (JsonNode affectedEntry : affectedNode) {
            affected.add(affected(affectedEntry));
        }
        // Avis retiré (champ OSV « withdrawn ») : le signal et son historique sont conservés, mais il
        // ne déclare plus aucun paquet affecté ; la corrélation retire donc les impacts qu'il portait.
        Instant withdrawnAt = null;
        JsonNode withdrawn = root.path("withdrawn");
        if (!withdrawn.isMissingNode() && !withdrawn.isNull()) {
            affected.clear();
            withdrawnAt = dateDeRetrait(root);
        }

        String summary = text(root, "summary");
        String details = text(root, "details");
        return new NormalizedAdvisory(id, summary, details, severity(root), published, modified,
                List.copyOf(aliases), List.copyOf(affected), withdrawnAt, raw.content(), sha256(raw.content()));
    }

    private static Affected affected(JsonNode entry) {
        JsonNode pkg = entry.path("package");
        String ecosystem = text(pkg, "ecosystem");
        String name = text(pkg, "name");
        String purl = text(pkg, "purl");
        if ((name == null || name.isBlank()) && (purl == null || purl.isBlank())) {
            throw new AdvisoryRejectedException("paquet affecté sans nom ni PURL");
        }
        List<Range> ranges = new ArrayList<>();
        for (JsonNode range : entry.path("ranges")) {
            String type = text(range, "type");
            String introduced = null;
            boolean open = false;
            for (JsonNode event : range.path("events")) {
                if (event.has("introduced")) {
                    introduced = event.get("introduced").asText();
                    open = true;
                } else if (event.has("fixed") && open) {
                    ranges.add(new Range(type, introduced, event.get("fixed").asText(), null));
                    open = false;
                } else if (event.has("last_affected") && open) {
                    ranges.add(new Range(type, introduced, null, event.get("last_affected").asText()));
                    open = false;
                }
            }
            if (open) {
                ranges.add(new Range(type, introduced, null, null));
            }
        }
        List<String> versions = new ArrayList<>();
        entry.path("versions").forEach(version -> versions.add(version.asText()));
        return new Affected(ecosystem, name, purl, List.copyOf(ranges), List.copyOf(versions));
    }

    /**
     * Date de retrait, qui entre dans le contenu normalisé (COL-10, P-2). Une date illisible ne rejette pas
     * l'avis, accepté jusqu'ici : le retrait reste marqué par {@link Instant#EPOCH}.
     */
    private static Instant dateDeRetrait(JsonNode root) {
        try {
            Instant date = instant(root, "withdrawn", false);
            return date == null ? Instant.EPOCH : date;
        } catch (AdvisoryRejectedException illisible) {
            return Instant.EPOCH;
        }
    }

    private static String severity(JsonNode root) {
        String declared = text(root.path("database_specific"), "severity");
        if (declared != null) {
            return declared;
        }
        List<String> parts = new ArrayList<>();
        root.path("severity").forEach(entry -> parts.add(text(entry, "type") + ":" + text(entry, "score")));
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
        } catch (DateTimeParseException error) {
            throw new AdvisoryRejectedException("date " + field + " illisible", error);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponible", error);
        }
    }
}
