package io.argos.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.argos.ingestion.osv.OsvNormalizer;
import io.argos.testsupport.Fixtures;

/**
 * Empreinte du contenu normalisé (COL-10-S1, COL-10-S2, COL-10-S3 ; proposition P-2 du changement
 * {@code automatiser-chaine-veille}) sur la réponse OSV réelle enregistrée de Log4Shell et sur des avis de
 * test : canonicalisation (ordre des clés et des listes, espaces), exclusion de la date de modification,
 * sensibilité aux plages affectées et au retrait.
 */
class EmpreinteNormaliseeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static NormalizedAdvisory avis(String json) {
        return OsvNormalizer.normalize(new RawAdvisory("x", json));
    }

    /** Même contenu JSON, clés dans l'ordre inverse à chaque niveau et indentation différente. */
    @SuppressWarnings("unchecked")
    private static String reorganise(String json) throws Exception {
        Object arbre = JSON.readValue(json, Object.class);
        return JSON.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(inverse(arbre));
    }

    @SuppressWarnings("unchecked")
    private static Object inverse(Object noeud) {
        if (noeud instanceof Map<?, ?> map) {
            List<String> cles = new ArrayList<>((java.util.Set<String>) map.keySet());
            Collections.reverse(cles);
            Map<String, Object> resultat = new LinkedHashMap<>();
            cles.forEach(k -> resultat.put(k, inverse(map.get(k))));
            return resultat;
        }
        if (noeud instanceof List<?> liste) {
            return liste.stream().map(EmpreinteNormaliseeTest::inverse).toList();
        }
        return noeud;
    }

    @Test
    void col10S1ContenuBrutReorganiseMemeEmpreinteNormalisee() throws Exception {
        String brut = Fixtures.resource("osv/GHSA-jfh8-c2jp-5v3q.json");
        String reorganise = reorganise(brut);

        NormalizedAdvisory a = avis(brut);
        NormalizedAdvisory b = avis(reorganise);

        assertNotEquals(a.contentHash(), b.contentHash(), "contenus bruts différents");
        assertEquals(a.normalizedHash(), b.normalizedHash());
        assertEquals(64, a.normalizedHash().length());
        assertTrue(a.normalizedHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void col10S1OrdreDesAliasEtDesPaquetsSansEffet() {
        String deuxPaquets = """
                {"id":"GHSA-ORD","modified":"2026-11-01T00:00:00Z","aliases":["CVE-2026-1","CVE-2026-2"],
                 "affected":[{"package":{"ecosystem":"Maven","name":"a:a"},"versions":["1.0","1.1"]},
                             {"package":{"ecosystem":"Maven","name":"b:b"}}]}
                """;
        String inverse = """
                {"id":"GHSA-ORD","modified":"2026-11-01T00:00:00Z","aliases":["CVE-2026-2","CVE-2026-1"],
                 "affected":[{"package":{"ecosystem":"Maven","name":"b:b"}},
                             {"package":{"ecosystem":"Maven","name":"a:a"},"versions":["1.1","1.0"]}]}
                """;
        assertEquals(avis(deuxPaquets).normalizedHash(), avis(inverse).normalizedHash());
    }

    @Test
    void col10S2SeuleLaDateDeModificationChange() {
        String v1 = Fixtures.osv("GHSA-MOD", "2026-11-01T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0");
        String v2 = Fixtures.osv("GHSA-MOD", "2026-11-09T12:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0");

        assertNotEquals(avis(v1).contentHash(), avis(v2).contentHash());
        assertEquals(avis(v1).normalizedHash(), avis(v2).normalizedHash());
    }

    @Test
    void col10S3PlageAffecteeModifieeChangeLEmpreinte() {
        String v1 = Fixtures.osv("GHSA-PLG", "2026-11-01T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0");
        String v2 = Fixtures.osv("GHSA-PLG", "2026-11-05T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.4.0");

        assertNotEquals(avis(v1).normalizedHash(), avis(v2).normalizedHash());
    }

    @Test
    void champsNormalisesPrisEnCompte() {
        String base = Fixtures.osv("GHSA-CHP", "2026-11-01T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0");
        String empreinte = avis(base).normalizedHash();

        assertNotEquals(empreinte, avis(base.replace("\"severity\":\"HIGH\"", "\"severity\":\"CRITICAL\"")).normalizedHash(),
                "gravité");
        assertNotEquals(empreinte, avis(base.replace("Avis de test.", "Avis de test révisé.")).normalizedHash(), "résumé");
        assertNotEquals(empreinte, avis(base.replace("\"aliases\":[]", "\"aliases\":[\"CVE-2026-9\"]")).normalizedHash(),
                "alias");
        assertNotEquals(empreinte, avis(base.replaceFirst("\\{", "{\"withdrawn\":\"2026-11-12T00:00:00Z\",")).normalizedHash(),
                "retrait");
        assertNotEquals(empreinte, avis(base.replace("\"published\":\"2026-01-01T00:00:00Z\"",
                "\"published\":\"2026-01-02T00:00:00Z\"")).normalizedHash(), "date de publication");
    }

    @Test
    void jsonCanoniqueSansEspacesEtClesTriees() {
        String canonique = EmpreinteNormalisee.canonique(avis(Fixtures.osv("GHSA-CAN", "2026-11-01T00:00:00Z", "Maven",
                "org.exemple:lib", "pkg:maven/org.exemple/lib", "0", "1.3.0")));

        assertTrue(canonique.startsWith("{\"affected\":[{\"ecosystem\":\"Maven\""), canonique);
        assertTrue(!canonique.contains(": ") && !canonique.contains("\n"), canonique);
        assertTrue(!canonique.contains("modified") && !canonique.contains("2026-11-01"),
                "la date de modification n'entre pas dans le contenu normalisé : " + canonique);
    }
}
