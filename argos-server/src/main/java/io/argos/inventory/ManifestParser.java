package io.argos.inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.PackageKey;

/**
 * Lecture des manifestes de dépendances pour l'inventaire de repli (INV-04). Manifestes lus :
 * {@code package.json} (section {@code dependencies}) et {@code requirements.txt}.
 *
 * <p>Seules les versions exactes sont retenues ; une contrainte de plage ({@code ^1.2}, {@code >=2})
 * donne une version inconnue. Les dépendances transitives ne sont pas connues d'un manifeste.
 */
public final class ManifestParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern EXACT = Pattern.compile("v?(\\d+(?:\\.[0-9A-Za-z-]+)*)");
    private static final Pattern REQUIREMENT = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)\\s*(?:\\[[^]]*])?\\s*(==\\s*([^;\\s#]+))?.*$");

    private ManifestParser() {
    }

    /** Vrai si le chemin désigne un manifeste lisible. */
    public static boolean supports(String path) {
        String name = fileName(path);
        return "package.json".equals(name) || "requirements.txt".equals(name);
    }

    public static List<ParsedInventory.Item> parse(String path, byte[] content) {
        return switch (fileName(path)) {
            case "package.json" -> packageJson(content);
            case "requirements.txt" -> requirements(content);
            default -> throw new InvalidDocumentException("manifeste non pris en charge : " + path);
        };
    }

    private static List<ParsedInventory.Item> packageJson(byte[] content) {
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (IOException e) {
            throw new InvalidDocumentException("package.json illisible", e);
        }
        List<ParsedInventory.Item> items = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> deps = root.path("dependencies").fields();
        while (deps.hasNext()) {
            Map.Entry<String, JsonNode> d = deps.next();
            String name = d.getKey();
            String version = exact(d.getValue().asText());
            int slash = name.lastIndexOf('/');
            String namespace = slash < 0 ? null : name.substring(0, slash);
            String key = PackageKey.key("npm", namespace, name.substring(slash + 1));
            items.add(new ParsedInventory.Item(key, purl(key, version), version, null));
        }
        return items;
    }

    private static List<ParsedInventory.Item> requirements(byte[] content) {
        List<ParsedInventory.Item> items = new ArrayList<>();
        for (String raw : new String(content, StandardCharsets.UTF_8).split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) {
                continue;
            }
            Matcher m = REQUIREMENT.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String version = m.group(3) == null ? null : exact(m.group(3));
            String key = PackageKey.key("pypi", null, m.group(1));
            items.add(new ParsedInventory.Item(key, purl(key, version), version, null));
        }
        return items;
    }

    private static String exact(String constraint) {
        Matcher m = EXACT.matcher(constraint.strip());
        return m.matches() ? m.group(1) : null;
    }

    private static String purl(String key, String version) {
        return version == null ? key : key + "@" + version;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
