package io.argos.inventory;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.PackageKey;

/**
 * Valide et lit une SBOM CycloneDX au format JSON, versions de schéma 1.5 et 1.6 (décision du
 * 2026-11-16, Q-7). Les dépendances directes sont celles du composant racine
 * ({@code metadata.component}) dans le graphe {@code dependencies} ; les autres composants
 * sont transitifs (INV-02). Sans graphe, le caractère transitif est inconnu. Un composant sans PURL
 * exploitable est écarté de l'inventaire (INV-01) et signalé : nom, groupe, version et motif, pour
 * les {@value ParsedInventory#MAX_SIGNALED} premiers (décision D6 du 2026-11-18).
 */
public final class CycloneDxParser {

    static final Set<String> ACCEPTED_SPEC_VERSIONS = Set.of("1.5", "1.6");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Longueur maximale conservée du nom, du groupe et de la version d'un composant ignoré. */
    static final int MAX_FIELD = 200;

    private CycloneDxParser() {
    }

    public static ParsedInventory parse(byte[] document, long maxBytes) {
        JsonNode root = racine(document, maxBytes);
        String specVersion = specVersion(root);
        JsonNode components = composants(root);
        Optional<Instant> sourceDate = dateSource(root);
        Lecture lecture = new Lecture(directRefs(root));
        lecture.lire(components);
        return new ParsedInventory(specVersion, sourceDate.orElse(null), List.copyOf(lecture.items.values()), lecture.ignored,
                List.copyOf(lecture.signaled));
    }

    /** Racine du document : taille bornée, JSON lisible et objet. */
    private static JsonNode racine(byte[] document, long maxBytes) {
        if (document.length > maxBytes) {
            throw new InvalidDocumentException("document de " + document.length + " octets au-delà de la taille maximale");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(document);
        } catch (IOException e) {
            throw new InvalidDocumentException("JSON illisible", e);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidDocumentException("le document n'est pas un objet JSON");
        }
        return root;
    }

    /** Version de schéma d'un document CycloneDX, parmi {@link #ACCEPTED_SPEC_VERSIONS}. */
    private static String specVersion(JsonNode root) {
        if (!"CycloneDX".equals(root.path("bomFormat").asText())) {
            throw new InvalidDocumentException("bomFormat absent ou différent de CycloneDX");
        }
        String specVersion = root.path("specVersion").asText();
        if (!ACCEPTED_SPEC_VERSIONS.contains(specVersion)) {
            throw new InvalidDocumentException("version de schéma CycloneDX non acceptée : '" + specVersion
                    + "' (acceptées : 1.5, 1.6)");
        }
        return specVersion;
    }

    /** Composants de premier niveau : tableau, ou nœud absent si le document n'en déclare pas. */
    private static JsonNode composants(JsonNode root) {
        JsonNode components = root.path("components");
        if (!components.isMissingNode() && !components.isArray()) {
            throw new InvalidDocumentException("champ components non conforme");
        }
        return components;
    }

    /** Date de la SBOM ({@code metadata.timestamp}), absente si le document n'en déclare pas. */
    private static Optional<Instant> dateSource(JsonNode root) {
        String timestamp = root.path("metadata").path("timestamp").asText(null);
        if (timestamp == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(timestamp));
        } catch (DateTimeParseException e) {
            throw new InvalidDocumentException("metadata.timestamp illisible", e);
        }
    }

    /**
     * Lecture des composants, imbriqués compris, en largeur : un composant à PURL exploitable entre dans
     * l'inventaire (un doublon direct l'emporte sur les autres), les autres sont comptés et signalés.
     */
    private static final class Lecture {

        private final Optional<Set<String>> direct;
        private final Map<String, ParsedInventory.Item> items = new LinkedHashMap<>();
        private final List<ParsedInventory.IgnoredComponent> signaled = new ArrayList<>();
        private int ignored;

        private Lecture(Optional<Set<String>> direct) {
            this.direct = direct;
        }

        void lire(JsonNode components) {
            Deque<JsonNode> pending = new ArrayDeque<>();
            components.forEach(pending::add);
            while (!pending.isEmpty()) {
                JsonNode c = pending.poll();
                c.path("components").forEach(pending::add);
                String declaredPurl = c.path("purl").asText(null);
                PackageKey.parse(declaredPurl).ifPresentOrElse(purl -> retenir(c, purl), () -> ecarter(c, declaredPurl));
            }
        }

        private void retenir(JsonNode c, PackageKey.Purl purl) {
            String version = purl.version() != null ? purl.version() : c.path("version").asText(null);
            String bomRef = c.path("bom-ref").asText(null);
            Boolean transitive = direct.filter(d -> bomRef != null).map(d -> !d.contains(bomRef)).orElse(null);
            String identity = purl.key() + "@" + (version == null ? "" : version);
            items.merge(identity, new ParsedInventory.Item(purl.key(), c.path("purl").asText(), version, transitive),
                    (a, b) -> Boolean.FALSE.equals(a.transitive()) ? a : b);
        }

        private void ecarter(JsonNode c, String declaredPurl) {
            ignored++;
            signal(signaled, c, declaredPurl);
        }
    }
    /** Décrit un composant écarté, tant que la liste n'atteint pas {@link ParsedInventory#MAX_SIGNALED} entrées. */
    private static void signal(List<ParsedInventory.IgnoredComponent> signaled, JsonNode component, String declaredPurl) {
        if (signaled.size() >= ParsedInventory.MAX_SIGNALED) {
            return;
        }
        String reason = declaredPurl == null || declaredPurl.isBlank() ? ParsedInventory.IgnoredComponent.SANS_PURL
                : ParsedInventory.IgnoredComponent.PURL_INVALIDE;
        signaled.add(new ParsedInventory.IgnoredComponent(text(component, "name").orElse(null),
                text(component, "group").orElse(null), text(component, "version").orElse(null), reason));
    }

    /** Champ texte d'un composant, borné à {@value #MAX_FIELD} caractères ; absent s'il est vide. */
    private static Optional<String> text(JsonNode component, String field) {
        JsonNode value = component.path(field);
        if (!value.isValueNode()) {
            return Optional.empty();
        }
        String s = value.asText().strip();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(s.length() > MAX_FIELD ? s.substring(0, MAX_FIELD) : s);
    }

    /** Références des dépendances directes du composant racine, si le graphe est exploitable. */
    private static Optional<Set<String>> directRefs(JsonNode root) {
        String rootRef = root.path("metadata").path("component").path("bom-ref").asText(null);
        JsonNode dependencies = root.path("dependencies");
        if (rootRef == null || !dependencies.isArray()) {
            return Optional.empty();
        }
        for (JsonNode d : dependencies) {
            if (rootRef.equals(d.path("ref").asText())) {
                Set<String> refs = new HashSet<>();
                d.path("dependsOn").forEach(r -> refs.add(r.asText()));
                return Optional.of(refs);
            }
        }
        return Optional.empty();
    }
}
