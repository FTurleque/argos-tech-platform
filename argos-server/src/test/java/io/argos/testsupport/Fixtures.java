package io.argos.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Construction des documents de test : avis OSV, SBOM CycloneDX, réponses enregistrées. */
public final class Fixtures {

    private Fixtures() {
    }

    /** Fichier de {@code src/test/resources/fixtures}. */
    public static String resource(String path) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("fixture absente : " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Avis au format OSV affectant un paquet sur l'intervalle [introduced, fixed[.
     * {@code purl} nul : l'avis ne déclare que l'écosystème et le nom.
     */
    public static String osv(String id, String modified, String ecosystem, String name, String purl, String introduced,
            String fixed, String... aliases) {
        String aliasJson = java.util.Arrays.stream(aliases).map(a -> "\"" + a + "\"").collect(Collectors.joining(","));
        String events = "{\"introduced\":\"" + introduced + "\"}" + (fixed == null ? "" : ",{\"fixed\":\"" + fixed + "\"}");
        return """
                {"id":"%s","modified":"%s","published":"2026-01-01T00:00:00Z","aliases":[%s],
                 "summary":"Vulnérabilité de test %s","details":"Avis de test.",
                 "affected":[{"package":{"ecosystem":"%s","name":"%s"%s},
                              "ranges":[{"type":"ECOSYSTEM","events":[%s]}]}],
                 "database_specific":{"severity":"HIGH"}}
                """.formatted(id, modified, aliasJson, id, ecosystem, name,
                purl == null ? "" : ",\"purl\":\"" + purl + "\"", events);
    }

    /** Programme la source OSV simulée : index {@code modified_id.csv} et un avis par identifiant. */
    public static void stubOsv(Map<String, String> advisoriesById, Map<String, String> modifiedById) {
        List<Map.Entry<String, String>> sorted = new ArrayList<>(modifiedById.entrySet());
        sorted.sort(Map.Entry.<String, String>comparingByValue().reversed());
        String csv = sorted.stream().map(e -> e.getValue() + "," + e.getKey()).collect(Collectors.joining("\n"));
        StubServer.on("/osv-export/Maven/modified_id.csv", StubServer.Stub.ok(csv));
        advisoriesById.forEach((id, json) -> StubServer.on("/osv-api/v1/vulns/" + id, StubServer.Stub.ok(json)));
    }

    /** Raccourci : avis et dates déclarés ensemble (id → {modified, json}). */
    public static void stubOsv(String... idModifiedJson) {
        Map<String, String> json = new LinkedHashMap<>();
        Map<String, String> modified = new LinkedHashMap<>();
        for (int i = 0; i < idModifiedJson.length; i += 3) {
            modified.put(idModifiedJson[i], idModifiedJson[i + 1]);
            json.put(idModifiedJson[i], idModifiedJson[i + 2]);
        }
        stubOsv(json, modified);
    }

    /**
     * SBOM CycloneDX JSON. {@code components} : couples (bom-ref, purl) ; {@code direct} : bom-ref des
     * dépendances directes du composant racine ; {@code timestamp} nul : sans date de génération.
     */
    public static String cyclonedx(String specVersion, String timestamp, List<String[]> components, List<String> direct) {
        String comps = components.stream()
                .map(c -> "{\"type\":\"library\",\"bom-ref\":\"" + c[0] + "\",\"name\":\"" + c[0] + "\",\"purl\":\"" + c[1] + "\"}")
                .collect(Collectors.joining(","));
        String dependsOn = direct.stream().map(d -> "\"" + d + "\"").collect(Collectors.joining(","));
        String metadata = "{" + (timestamp == null ? "" : "\"timestamp\":\"" + timestamp + "\",")
                + "\"component\":{\"type\":\"application\",\"bom-ref\":\"root\",\"name\":\"app\"}}";
        return """
                {"bomFormat":"CycloneDX","specVersion":"%s","version":1,"metadata":%s,
                 "components":[%s],
                 "dependencies":[{"ref":"root","dependsOn":[%s]}]}
                """.formatted(specVersion, metadata, comps, dependsOn);
    }
}
