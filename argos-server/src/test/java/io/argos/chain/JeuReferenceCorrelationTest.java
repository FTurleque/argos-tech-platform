package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.argos.catalog.PackageKey;
import io.argos.impact.CorrelationEngine;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Mesure de la précision et du rappel de la corrélation sur le jeu de référence annoté (tâche 4.7,
 * COR-01, COR-02, baseline v2.3 §6 critère 3).
 *
 * <p>Le jeu ({@code fixtures/jeu-reference-correlation/jeu.json}) réunit des avis OSV réels, des SBOM
 * CycloneDX et un manifeste de test, et la vérité annotée à la main. Le test exécute la chaîne réelle
 * d'ARGOS sans accès à Internet : import des inventaires, collecte OSV sur le serveur simulé,
 * corrélation déclenchée par la collecte, puis lecture des impacts enregistrés. Il compare chaque
 * impact à la vérité, vérifie les composants signalés à l'import faute de PURL (INV-01-S3, décision D6),
 * calcule précision et rappel par niveau et échoue si un écart n'est pas consigné
 * dans le jeu ou si les métriques diffèrent de celles du rapport versionné
 * ({@code docs/architecture/quality/mesure-correlation-jeu-reference.md}). Le rapport calculé est écrit
 * dans {@code target/jeu-reference-correlation/mesure.md}.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class JeuReferenceCorrelationTest {

    private static final String JEU = "jeu-reference-correlation/";
    private static final String AUCUN = "AUCUN";
    private static final List<String> NIVEAUX = List.of("EXACT", "PROBABLE", "A_VERIFIER");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    TestDb db;

    @Inject
    DataSource dataSource;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    CorrelationEngine engine;

    /** Impact observé : niveau et caractère transitif enregistrés. */
    record Observe(String niveau, Boolean transitif) {
    }

    /** Vrais positifs, faux positifs, faux négatifs d'une catégorie. */
    record Compte(int vp, int fp, int fn) {

        String precision() {
            return taux(vp, vp + fp);
        }

        String rappel() {
            return taux(vp, vp + fn);
        }

        String ligne() {
            return vp + "/" + fp + "/" + fn + " précision " + precision() + " rappel " + rappel();
        }
    }

    /** Avis du jeu : contenu JSON, date de modification et alias CVE, par identifiant. */
    record AvisDuJeu(Map<String, String> json, Map<String, String> modifie, Map<String, List<String>> aliasCve) {
    }

    /** Comptes accumulés en comparant chaque paire (projet, avis, composant) à la vérité annotée. */
    private static final class Mesure {
        private int vp;
        private int fp;
        private int fn;
        private int vpConfirmes;
        private int fpConfirmes;
        private int fnConfirmes;
        private int transitifsCorrects;
        private int transitifsAnnotes;
        private final Map<String, Compte> parNiveau = new LinkedHashMap<>();
        private final Set<String> ecarts = new TreeSet<>();

        Mesure() {
            for (String niveau : NIVEAUX) {
                parNiveau.put(niveau, new Compte(0, 0, 0));
            }
        }

        void comparer(String cle, JsonNode a, Observe o) {
            String attendu = a == null ? AUCUN : a.path("niveau").asText();
            String observe = o == null ? AUCUN : o.niveau();
            compterTousNiveaux(attendu, observe, a, o);
            compterConfirmes(attendu, observe);
            for (String niveau : NIVEAUX) {
                compterNiveau(niveau, attendu, observe);
            }
            if (!attendu.equals(observe)) {
                ecarts.add(cle + "|" + nature(attendu, observe) + "|" + observe);
            }
        }

        private void compterTousNiveaux(String attendu, String observe, JsonNode a, Observe o) {
            if (!attendu.equals(AUCUN) && !observe.equals(AUCUN)) {
                vp++;
                if (a.hasNonNull("transitif")) {
                    transitifsAnnotes++;
                    if (Boolean.valueOf(a.path("transitif").asBoolean()).equals(o.transitif())) {
                        transitifsCorrects++;
                    }
                }
            } else if (!observe.equals(AUCUN)) {
                fp++;
            } else if (!attendu.equals(AUCUN)) {
                fn++;
            }
        }

        private void compterConfirmes(String attendu, String observe) {
            boolean attenduConfirme = confirme(attendu);
            boolean observeConfirme = confirme(observe);
            if (attenduConfirme && observeConfirme) {
                vpConfirmes++;
            } else if (observeConfirme) {
                fpConfirmes++;
            } else if (attenduConfirme) {
                fnConfirmes++;
            }
        }

        private void compterNiveau(String niveau, String attendu, String observe) {
            Compte k = parNiveau.get(niveau);
            if (attendu.equals(niveau) && observe.equals(niveau)) {
                parNiveau.put(niveau, new Compte(k.vp() + 1, k.fp(), k.fn()));
            } else if (observe.equals(niveau)) {
                parNiveau.put(niveau, new Compte(k.vp(), k.fp() + 1, k.fn()));
            } else if (attendu.equals(niveau)) {
                parNiveau.put(niveau, new Compte(k.vp(), k.fp(), k.fn() + 1));
            }
        }

        private static boolean confirme(String niveau) {
            return niveau.equals("EXACT") || niveau.equals("PROBABLE");
        }

        private static String nature(String attendu, String observe) {
            if (attendu.equals(AUCUN)) {
                return "FAUX_POSITIF";
            }
            return observe.equals(AUCUN) ? "FAUX_NEGATIF" : "NIVEAU";
        }
    }

    @Test
    void precisionEtRappelSurLeJeuDeReference() throws Exception {
        db.reset();
        JsonNode jeu = MAPPER.readTree(Fixtures.resource(JEU + "jeu.json"));
        UUID espace = Api.nouvelEspace("alice", "Jeu de référence de la corrélation");

        // 1. Inventaires : SBOM datées par rapport à l'instant présent, ou manifestes de repli.
        Map<String, UUID> projetsParNom = importerInventaires(jeu, espace);

        // 2. Avis réels servis par la source OSV simulée ; la collecte déclenche la corrélation.
        AvisDuJeu avis = chargerAvis(jeu);
        Fixtures.stubOsv(avis.json(), avis.modifie());
        synchronizer.synchronize("OSV");
        assertEquals(avis.json().size(), db.count("SELECT count(*) FROM signal"), "tous les avis du jeu sont collectés");

        // 3. Second déclencheur, la corrélation par projet : même résultat, aucun impact créé, résolu ni rouvert.
        for (UUID projet : projetsParNom.values()) {
            for (CorrelationEngine.CorrelationReport r : engine.correlateProject(projet)) {
                assertEquals(0, r.created() + r.resolved() + r.reopened(), "corrélation par projet stable pour " + projet);
            }
        }

        // 4. Comparaison à la vérité annotée.
        Map<String, Set<String>> composantsParProjet = composants();
        Map<String, Observe> observes = observes();
        Map<String, JsonNode> attendus = attendus(jeu, composantsParProjet, avis.json().keySet());
        int vraisNegatifs = vraisNegatifs(jeu, composantsParProjet, attendus, observes);
        Set<String> cles = new TreeSet<>(attendus.keySet());
        cles.addAll(observes.keySet());
        Mesure mesure = new Mesure();
        for (String c : cles) {
            mesure.comparer(c, attendus.get(c), observes.get(c));
        }

        // 5. Métriques, dont les alias CVE indexés et les doublons d'alias (deux signaux de même CVE sur un composant).
        Map<String, String> metriques = new LinkedHashMap<>();
        metriques.put("projets", Integer.toString(projetsParNom.size()));
        metriques.put("avis", Integer.toString(avis.json().size()));
        metriques.put("impactsAttendus", Integer.toString(attendus.size()));
        metriques.put("impactsObserves", Integer.toString(observes.size()));
        metriques.put("negatifsAnnotes", Integer.toString(jeu.path("negatifs").size()));
        metriques.put("vraisNegatifs", Integer.toString(vraisNegatifs));
        metriques.put("tousNiveaux", new Compte(mesure.vp, mesure.fp, mesure.fn).ligne());
        metriques.put("niveauxConfirmes", new Compte(mesure.vpConfirmes, mesure.fpConfirmes, mesure.fnConfirmes).ligne());
        mesure.parNiveau.forEach((niveau, k) -> metriques.put(niveau, k.ligne()));
        metriques.put("transitifCorrect", mesure.transitifsCorrects + "/" + mesure.transitifsAnnotes);
        metriques.put("aliasCveIndexes", aliasIndexes(avis.aliasCve()));
        metriques.put("doublonsAlias", Long.toString(doublonsAlias(observes, avis.aliasCve())));
        metriques.put("composantsSignales", Integer.toString(composantsSignales));

        ecrireRapport(metriques, mesure.ecarts, attendus, observes);

        Set<String> ecartsConnus = new TreeSet<>();
        for (JsonNode e : jeu.path("ecartsConnus")) {
            ecartsConnus.add(cle(e) + "|" + e.path("nature").asText() + "|" + e.path("observe").asText());
        }
        assertEquals(ecartsConnus, mesure.ecarts,
                "écarts entre la corrélation et la vérité annotée (à analyser et consigner dans le jeu)");
        Map<String, String> attenduesDuRapport = new LinkedHashMap<>();
        jeu.path("metriquesConsignees").fields()
                .forEachRemaining(f -> attenduesDuRapport.put(f.getKey(), f.getValue().asText()));
        assertEquals(attenduesDuRapport, metriques, "métriques différentes de celles du rapport versionné");
    }

    private Map<String, UUID> importerInventaires(JsonNode jeu, UUID espace) throws IOException {
        Map<String, UUID> projetsParNom = new LinkedHashMap<>();
        for (JsonNode p : jeu.path("projets")) {
            String nom = p.path("nom").asText();
            UUID projet = projects.create(espace, nom, null, null);
            projetsParNom.put(nom, projet);
            InventoryService.ImportResult resultat;
            if (p.has("sbom")) {
                ObjectNode sbom = (ObjectNode) MAPPER.readTree(Fixtures.resource(JEU + p.path("sbom").asText()));
                Instant genereLe = Instant.now().minus(p.path("ageJours").asLong(), ChronoUnit.DAYS)
                        .truncatedTo(ChronoUnit.SECONDS);
                ((ObjectNode) sbom.path("metadata")).put("timestamp", genereLe.toString());
                resultat = inventories.importSbom(projet, MAPPER.writeValueAsBytes(sbom), "jeu-reference");
            } else {
                Map<String, byte[]> manifestes = new LinkedHashMap<>();
                for (JsonNode m : p.path("manifestes")) {
                    // Nom transmis à l'import (détection du format) et fichier de la fixture, nommé autrement
                    // qu'un vrai manifeste pour ne pas passer pour un projet npm sans fichier de verrouillage.
                    manifestes.put(m.path("nom").asText(),
                            Fixtures.resource(JEU + m.path("fichier").asText()).getBytes(StandardCharsets.UTF_8));
                }
                resultat = inventories.importManifests(projet, manifestes);
            }
            assertEquals("IMPORTED", resultat.status(), "import de l'inventaire de " + nom + " : " + resultat.reason());
            // Composants écartés faute de PURL, signalés à l'import (INV-01, décision D6) : exactement ceux annotés.
            List<String> signales = new ArrayList<>();
            p.path("composantsSignales").forEach(s -> signales.add(String.join("|", s.path("groupe").asText(),
                    s.path("nom").asText(), s.path("version").asText(), s.path("motif").asText())));
            List<String> observes = new ArrayList<>();
            resultat.ignoredComponents().forEach(i -> observes.add(String.join("|", String.valueOf(i.group()),
                    String.valueOf(i.name()), String.valueOf(i.version()), i.reason())));
            assertEquals(signales, observes, "composants signalés à l'import de " + nom);
            assertEquals(signales.size(), resultat.ignored(), "nombre de composants ignorés de " + nom);
            composantsSignales += observes.size();
        }
        return projetsParNom;
    }

    /** Composants signalés à l'import, tous projets confondus (métrique {@code composantsSignales}). */
    private int composantsSignales;

    private static AvisDuJeu chargerAvis(JsonNode jeu) throws IOException {
        Map<String, String> avisParId = new LinkedHashMap<>();
        Map<String, String> modifieParId = new LinkedHashMap<>();
        Map<String, List<String>> aliasCve = new TreeMap<>();
        for (JsonNode chemin : jeu.path("avis")) {
            String json = Fixtures.resource(JEU + chemin.asText());
            JsonNode avis = MAPPER.readTree(json);
            String id = avis.path("id").asText();
            avisParId.put(id, json);
            modifieParId.put(id, avis.path("modified").asText());
            List<String> cve = new ArrayList<>();
            avis.path("aliases").forEach(a -> {
                if (a.asText().startsWith("CVE-")) {
                    cve.add(a.asText());
                }
            });
            aliasCve.put(id, cve);
        }
        return new AvisDuJeu(avisParId, modifieParId, aliasCve);
    }

    private static Map<String, JsonNode> attendus(JsonNode jeu, Map<String, Set<String>> composantsParProjet,
            Set<String> idsAvis) {
        Map<String, JsonNode> attendus = new LinkedHashMap<>();
        for (JsonNode v : jeu.path("verite")) {
            if (!v.path("horsInventaire").asBoolean(false)) {
                assertTrue(dansLInventaire(composantsParProjet, v), "composant annoté absent de l'inventaire : " + v);
            }
            assertTrue(idsAvis.contains(v.path("avis").asText()), "avis annoté absent du jeu : " + v);
            attendus.put(cle(v), v);
        }
        return attendus;
    }

    private static int vraisNegatifs(JsonNode jeu, Map<String, Set<String>> composantsParProjet,
            Map<String, JsonNode> attendus, Map<String, Observe> observes) {
        int vraisNegatifs = 0;
        for (JsonNode n : jeu.path("negatifs")) {
            assertTrue(dansLInventaire(composantsParProjet, n), "composant négatif absent de l'inventaire : " + n);
            assertFalse(attendus.containsKey(cle(n)), "paire annotée à la fois positive et négative : " + n);
            if (!observes.containsKey(cle(n))) {
                vraisNegatifs++;
            }
        }
        return vraisNegatifs;
    }

    private static boolean dansLInventaire(Map<String, Set<String>> composantsParProjet, JsonNode paire) {
        return composantsParProjet.getOrDefault(paire.path("projet").asText(), Set.of())
                .contains(norme(paire.path("composant").asText()));
    }

    /** Alias CVE des avis retrouvés dans l'index des alias, sous la forme « indexés/attendus ». */
    private String aliasIndexes(Map<String, List<String>> aliasCve) throws SQLException {
        int indexes = 0;
        int attendus = 0;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM signal_alias a JOIN signal s ON s.id = a.signal_id WHERE s.external_id = ? AND a.alias = ?")) {
            for (Map.Entry<String, List<String>> e : aliasCve.entrySet()) {
                for (String cve : e.getValue()) {
                    attendus++;
                    ps.setString(1, e.getKey());
                    ps.setString(2, cve);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        indexes += rs.getInt(1);
                    }
                }
            }
        }
        return indexes + "/" + attendus;
    }

    /** Nombre de couples (projet, composant, CVE) portés par plusieurs signaux. */
    private static long doublonsAlias(Map<String, Observe> observes, Map<String, List<String>> aliasCve) {
        Map<String, Set<String>> signauxParComposantEtCve = new HashMap<>();
        for (String c : observes.keySet()) {
            String[] parties = c.split("\\|", 3);
            for (String cve : aliasCve.getOrDefault(parties[1], List.of())) {
                signauxParComposantEtCve.computeIfAbsent(parties[0] + "|" + parties[2] + "|" + cve, k -> new HashSet<>())
                        .add(parties[1]);
            }
        }
        return signauxParComposantEtCve.values().stream().filter(s -> s.size() > 1).count();
    }

    private static String cle(JsonNode n) {
        return n.path("projet").asText() + "|" + n.path("avis").asText() + "|" + norme(n.path("composant").asText());
    }

    /** Identité d'un composant : clé de paquet normalisée et version, sans qualificatifs. */
    private static String norme(String composant) {
        return PackageKey.parse(composant)
                .map(p -> p.key() + "@" + (p.version() == null ? "" : p.version()))
                .orElse(composant);
    }

    private Map<String, Observe> observes() throws SQLException {
        Map<String, Observe> observes = new TreeMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT p.name, s.external_id, co.purl, i.level, i.transitive
                FROM project_impact i JOIN project p ON p.id = i.project_id JOIN signal s ON s.id = i.signal_id
                     JOIN component co ON co.id = i.component_id
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cle = rs.getString(1) + "|" + rs.getString(2) + "|" + norme(rs.getString(3));
                Observe precedent = observes.put(cle, new Observe(rs.getString(4), (Boolean) rs.getObject(5)));
                assertNull(precedent, "impact en double pour " + cle);
            }
        }
        return observes;
    }

    private Map<String, Set<String>> composants() throws SQLException {
        Map<String, Set<String>> composants = new HashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT p.name, co.purl FROM component co JOIN project p ON p.id = co.project_id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                composants.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(norme(rs.getString(2)));
            }
        }
        return composants;
    }

    private static String taux(int numerateur, int denominateur) {
        if (denominateur == 0) {
            return "n/a";
        }
        return String.format(Locale.FRANCE, "%.1f %%", 100.0 * numerateur / denominateur);
    }

    private static void ecrireRapport(Map<String, String> metriques, Set<String> ecarts, Map<String, JsonNode> attendus,
            Map<String, Observe> observes) throws IOException {
        StringBuilder md = new StringBuilder("# Mesure calculée — jeu de référence de la corrélation\n\n");
        md.append("Exécution du ").append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append(".\n\n");
        md.append("| Métrique | Valeur (VP/FP/FN pour les comptes) |\n|---|---|\n");
        metriques.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        md.append("\n## Écarts\n\n");
        ecarts.forEach(e -> md.append("- ").append(e).append('\n'));
        md.append("\n## Détail (projet | avis | composant → attendu / observé)\n\n");
        Set<String> cles = new TreeSet<>(attendus.keySet());
        cles.addAll(observes.keySet());
        for (String c : cles) {
            JsonNode a = attendus.get(c);
            Observe o = observes.get(c);
            md.append("- ").append(c).append(" → ").append(a == null ? AUCUN : a.path("niveau").asText()).append(" / ")
                    .append(o == null ? AUCUN : o.niveau()).append('\n');
        }
        Path dossier = Path.of("target", "jeu-reference-correlation");
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("mesure.md"), md.toString(), StandardCharsets.UTF_8);
    }
}
