package io.argos.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

    /** En-têtes de transport recalculés par le serveur simulé, jamais rejoués. */
    private static final Set<String> ENTETES_TRANSPORT =
            Set.of("content-length", "transfer-encoding", "connection", "keep-alive", "content-encoding");

    private static final Pattern MAINTENANT = Pattern.compile("\\$\\{maintenant\\+(\\d+)}");

    /**
     * Réponse HTTP enregistrée ({@code fixtures/<path>}, sortie de {@code curl -i}) : ligne de statut,
     * en-têtes, ligne vide, corps. Les en-têtes de transport sont retirés ; {@code ${maintenant+N}}
     * est remplacé par l'instant présent plus N secondes, en secondes depuis l'époque (réponses
     * composées de limite de débit uniquement).
     */
    public static StubServer.Stub reponseEnregistree(String path) {
        ReponseHttp r = lireReponse(path);
        return new StubServer.Stub(r.status(), r.corps(), r.entetes(), 0);
    }

    /** Corps d'une réponse HTTP enregistrée. */
    public static String corpsEnregistre(String path) {
        return lireReponse(path).corps();
    }

    /** Réponse HTTP enregistrée, décomposée. */
    public record ReponseHttp(int status, Map<String, String> entetes, String corps) {
    }

    /** Lit une réponse HTTP enregistrée (fins de ligne CRLF ou LF). */
    public static ReponseHttp lireReponse(String path) {
        String brut = resource(path);
        int crlf = brut.indexOf("\r\n\r\n");
        int lf = brut.indexOf("\n\n");
        int fin;
        int saut;
        if (crlf >= 0 && (lf < 0 || crlf < lf)) {
            fin = crlf;
            saut = 4;
        } else if (lf >= 0) {
            fin = lf;
            saut = 2;
        } else {
            throw new IllegalArgumentException("réponse enregistrée sans séparation en-têtes / corps : " + path);
        }
        String[] lignes = brut.substring(0, fin).split("\r?\n");
        String[] statut = lignes[0].split(" ", 3);
        if (statut.length < 2 || !statut[0].startsWith("HTTP/")) {
            throw new IllegalArgumentException("ligne de statut invalide : " + path);
        }
        Map<String, String> entetes = new LinkedHashMap<>();
        long maintenant = Instant.now().getEpochSecond();
        for (int i = 1; i < lignes.length; i++) {
            int deuxPoints = lignes[i].indexOf(':');
            if (deuxPoints <= 0) {
                continue;
            }
            String nom = lignes[i].substring(0, deuxPoints).strip();
            if (ENTETES_TRANSPORT.contains(nom.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String valeur = MAINTENANT.matcher(lignes[i].substring(deuxPoints + 1).strip())
                    .replaceAll(m -> Long.toString(maintenant + Long.parseLong(m.group(1))));
            entetes.put(nom, valeur);
        }
        return new ReponseHttp(Integer.parseInt(statut[1]), entetes, brut.substring(fin + saut));
    }

    /**
     * Avis au format OSV affectant un paquet sur l'intervalle [introduced, fixed[.
     * {@code purl} nul : l'avis ne déclare que l'écosystème et le nom.
     */
    public static String osv(String id, String modified, String ecosystem, String name, String purl, String introduced,
            String fixed, String... aliases) {
        String aliasJson = Arrays.stream(aliases).map(a -> "\"" + a + "\"").collect(Collectors.joining(","));
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
