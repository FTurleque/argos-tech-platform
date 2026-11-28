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
        try (InputStream flux = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (flux == null) {
                throw new IllegalArgumentException("fixture absente : " + path);
            }
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
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
        ReponseHttp reponse = lireReponse(path);
        return new StubServer.Stub(reponse.status(), reponse.corps(), reponse.entetes());
    }

    /** Corps d'une réponse HTTP enregistrée. */
    public static String corpsEnregistre(String path) {
        return lireReponse(path).corps();
    }

    /** Réponse HTTP enregistrée, décomposée. */
    public record ReponseHttp(int status, Map<String, String> entetes, String corps) {
    }

    /** Lit une réponse HTTP enregistrée (fins de ligne CRLF ou LF), dates relatives calculées depuis l'instant présent. */
    public static ReponseHttp lireReponse(String path) {
        return lireReponse(path, Instant.now());
    }

    /**
     * Lit une réponse HTTP enregistrée (fins de ligne CRLF ou LF) ; {@code ${maintenant+N}} y vaut l'instant donné plus
     * N secondes, en secondes depuis l'époque Unix.
     */
    public static ReponseHttp lireReponse(String path, Instant instant) {
        String brut = resource(path);
        int separateurCrlf = brut.indexOf("\r\n\r\n");
        int separateurLf = brut.indexOf("\n\n");
        int fin;
        int saut;
        if (separateurCrlf >= 0 && (separateurLf < 0 || separateurCrlf < separateurLf)) {
            fin = separateurCrlf;
            saut = 4;
        } else if (separateurLf >= 0) {
            fin = separateurLf;
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
        long maintenant = instant.getEpochSecond();
        for (int numeroLigne = 1; numeroLigne < lignes.length; numeroLigne++) {
            int deuxPoints = lignes[numeroLigne].indexOf(':');
            if (deuxPoints <= 0) {
                continue;
            }
            String nom = lignes[numeroLigne].substring(0, deuxPoints).strip();
            if (ENTETES_TRANSPORT.contains(nom.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String valeur = MAINTENANT.matcher(lignes[numeroLigne].substring(deuxPoints + 1).strip())
                    .replaceAll(correspondance -> Long.toString(maintenant + Long.parseLong(correspondance.group(1))));
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
        String aliasJson = Arrays.stream(aliases).map(alias -> "\"" + alias + "\"").collect(Collectors.joining(","));
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
        String csv = sorted.stream().map(entree -> entree.getValue() + "," + entree.getKey()).collect(Collectors.joining("\n"));
        StubServer.programmer("/osv-export/Maven/modified_id.csv", StubServer.Stub.succes(csv));
        advisoriesById.forEach((id, json) -> StubServer.programmer("/osv-api/v1/vulns/" + id, StubServer.Stub.succes(json)));
    }

    /** Raccourci : avis et dates déclarés ensemble (id → {modified, json}). */
    public static void stubOsv(String... idModifiedJson) {
        Map<String, String> json = new LinkedHashMap<>();
        Map<String, String> modified = new LinkedHashMap<>();
        for (int indice = 0; indice < idModifiedJson.length; indice += 3) {
            modified.put(idModifiedJson[indice], idModifiedJson[indice + 1]);
            json.put(idModifiedJson[indice], idModifiedJson[indice + 2]);
        }
        stubOsv(json, modified);
    }

    /**
     * SBOM CycloneDX JSON. {@code components} : couples (bom-ref, purl) ; {@code direct} : bom-ref des
     * dépendances directes du composant racine ; {@code timestamp} nul : sans date de génération.
     */
    public static String cyclonedx(String specVersion, String timestamp, List<String[]> components, List<String> direct) {
        String comps = components.stream()
                .map(composant -> "{\"type\":\"library\",\"bom-ref\":\"" + composant[0] + "\",\"name\":\"" + composant[0]
                        + "\",\"purl\":\"" + composant[1] + "\"}")
                .collect(Collectors.joining(","));
        String dependsOn = direct.stream().map(dependance -> "\"" + dependance + "\"").collect(Collectors.joining(","));
        String metadata = "{" + (timestamp == null ? "" : "\"timestamp\":\"" + timestamp + "\",")
                + "\"component\":{\"type\":\"application\",\"bom-ref\":\"root\",\"name\":\"app\"}}";
        return """
                {"bomFormat":"CycloneDX","specVersion":"%s","version":1,"metadata":%s,
                 "components":[%s],
                 "dependencies":[{"ref":"root","dependsOn":[%s]}]}
                """.formatted(specVersion, metadata, comps, dependsOn);
    }
}
