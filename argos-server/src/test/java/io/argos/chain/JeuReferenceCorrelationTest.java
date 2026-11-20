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
    TestDb baseDeTest;

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
    record Compte(int vraisPositifs, int fauxPositifs, int fauxNegatifs) {

        String precision() {
            return taux(vraisPositifs, vraisPositifs + fauxPositifs);
        }

        String rappel() {
            return taux(vraisPositifs, vraisPositifs + fauxNegatifs);
        }

        String ligne() {
            return vraisPositifs + "/" + fauxPositifs + "/" + fauxNegatifs + " précision " + precision() + " rappel " + rappel();
        }
    }

    /** Avis du jeu : contenu JSON, date de modification et alias CVE, par identifiant. */
    record AvisDuJeu(Map<String, String> json, Map<String, String> modifie, Map<String, List<String>> aliasCve) {
    }

    /** Comptes accumulés en comparant chaque paire (projet, avis, composant) à la vérité annotée. */
    private static final class Mesure {
        private int vraisPositifs;
        private int fauxPositifs;
        private int fauxNegatifs;
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

        void comparer(String cle, JsonNode annotation, Observe observation) {
            String attendu = annotation == null ? AUCUN : annotation.path("niveau").asText();
            String observe = observation == null ? AUCUN : observation.niveau();
            compterTousNiveaux(attendu, observe, annotation, observation);
            compterConfirmes(attendu, observe);
            for (String niveau : NIVEAUX) {
                compterNiveau(niveau, attendu, observe);
            }
            if (!attendu.equals(observe)) {
                ecarts.add(cle + "|" + nature(attendu, observe) + "|" + observe);
            }
        }

        private void compterTousNiveaux(String attendu, String observe, JsonNode annotation, Observe observation) {
            if (!attendu.equals(AUCUN) && !observe.equals(AUCUN)) {
                vraisPositifs++;
                if (annotation.hasNonNull("transitif")) {
                    transitifsAnnotes++;
                    if (Boolean.valueOf(annotation.path("transitif").asBoolean()).equals(observation.transitif())) {
                        transitifsCorrects++;
                    }
                }
            } else if (!observe.equals(AUCUN)) {
                fauxPositifs++;
            } else if (!attendu.equals(AUCUN)) {
                fauxNegatifs++;
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
            Compte compte = parNiveau.get(niveau);
            if (attendu.equals(niveau) && observe.equals(niveau)) {
                parNiveau.put(niveau, new Compte(compte.vraisPositifs() + 1, compte.fauxPositifs(), compte.fauxNegatifs()));
            } else if (observe.equals(niveau)) {
                parNiveau.put(niveau, new Compte(compte.vraisPositifs(), compte.fauxPositifs() + 1, compte.fauxNegatifs()));
            } else if (attendu.equals(niveau)) {
                parNiveau.put(niveau, new Compte(compte.vraisPositifs(), compte.fauxPositifs(), compte.fauxNegatifs() + 1));
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
        baseDeTest.reset();
        JsonNode jeu = MAPPER.readTree(Fixtures.resource(JEU + "jeu.json"));
        UUID espace = Api.nouvelEspace("alice", "Jeu de référence de la corrélation");

        // 1. Inventaires : SBOM datées par rapport à l'instant présent, ou manifestes de repli.
        Map<String, UUID> projetsParNom = importerInventaires(jeu, espace);

        // 2. Avis réels servis par la source OSV simulée ; la collecte déclenche la corrélation.
        AvisDuJeu avis = chargerAvis(jeu);
        Fixtures.stubOsv(avis.json(), avis.modifie());
        synchronizer.synchronize("OSV");
        assertEquals(avis.json().size(), baseDeTest.count("SELECT count(*) FROM signal"), "tous les avis du jeu sont collectés");

        // 3. Second déclencheur, la corrélation par projet : même résultat, aucun impact créé, résolu ni rouvert.
        for (UUID projet : projetsParNom.values()) {
            for (CorrelationEngine.CorrelationReport rapport : engine.correlateProject(projet)) {
                assertEquals(0, rapport.created() + rapport.resolved() + rapport.reopened(),
                        "corrélation par projet stable pour " + projet);
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
        for (String paire : cles) {
            mesure.comparer(paire, attendus.get(paire), observes.get(paire));
        }

        // 5. Métriques, dont les alias CVE indexés et les doublons d'alias (deux signaux de même CVE sur un composant).
        Map<String, String> metriques = new LinkedHashMap<>();
        metriques.put("projets", Integer.toString(projetsParNom.size()));
        metriques.put("avis", Integer.toString(avis.json().size()));
        metriques.put("impactsAttendus", Integer.toString(attendus.size()));
        metriques.put("impactsObserves", Integer.toString(observes.size()));
        metriques.put("negatifsAnnotes", Integer.toString(jeu.path("negatifs").size()));
        metriques.put("vraisNegatifs", Integer.toString(vraisNegatifs));
        metriques.put("tousNiveaux", new Compte(mesure.vraisPositifs, mesure.fauxPositifs, mesure.fauxNegatifs).ligne());
        metriques.put("niveauxConfirmes", new Compte(mesure.vpConfirmes, mesure.fpConfirmes, mesure.fnConfirmes).ligne());
        mesure.parNiveau.forEach((niveau, compte) -> metriques.put(niveau, compte.ligne()));
        metriques.put("transitifCorrect", mesure.transitifsCorrects + "/" + mesure.transitifsAnnotes);
        metriques.put("aliasCveIndexes", aliasIndexes(avis.aliasCve()));
        metriques.put("doublonsAlias", Long.toString(doublonsAlias(observes, avis.aliasCve())));
        metriques.put("composantsSignales", Integer.toString(composantsSignales));

        ecrireRapport(metriques, mesure.ecarts, attendus, observes);

        Set<String> ecartsConnus = new TreeSet<>();
        for (JsonNode ecart : jeu.path("ecartsConnus")) {
            ecartsConnus.add(cle(ecart) + "|" + ecart.path("nature").asText() + "|" + ecart.path("observe").asText());
        }
        assertEquals(ecartsConnus, mesure.ecarts,
                "écarts entre la corrélation et la vérité annotée (à analyser et consigner dans le jeu)");
        Map<String, String> attenduesDuRapport = new LinkedHashMap<>();
        jeu.path("metriquesConsignees").fields()
                .forEachRemaining(champ -> attenduesDuRapport.put(champ.getKey(), champ.getValue().asText()));
        assertEquals(attenduesDuRapport, metriques, "métriques différentes de celles du rapport versionné");
    }

    private Map<String, UUID> importerInventaires(JsonNode jeu, UUID espace) throws IOException {
        Map<String, UUID> projetsParNom = new LinkedHashMap<>();
        for (JsonNode projetDuJeu : jeu.path("projets")) {
            String nom = projetDuJeu.path("nom").asText();
            UUID projet = projects.create(espace, nom, null, null);
            projetsParNom.put(nom, projet);
            InventoryService.ImportResult resultat;
            if (projetDuJeu.has("sbom")) {
                ObjectNode sbom = (ObjectNode) MAPPER.readTree(Fixtures.resource(JEU + projetDuJeu.path("sbom").asText()));
                Instant genereLe = Instant.now().minus(projetDuJeu.path("ageJours").asLong(), ChronoUnit.DAYS)
                        .truncatedTo(ChronoUnit.SECONDS);
                ((ObjectNode) sbom.path("metadata")).put("timestamp", genereLe.toString());
                resultat = inventories.importSbom(projet, MAPPER.writeValueAsBytes(sbom), "jeu-reference");
            } else {
                Map<String, byte[]> manifestes = new LinkedHashMap<>();
                for (JsonNode manifeste : projetDuJeu.path("manifestes")) {
                    // Nom transmis à l'import (détection du format) et fichier de la fixture, nommé autrement
                    // qu'un vrai manifeste pour ne pas passer pour un projet npm sans fichier de verrouillage.
                    manifestes.put(manifeste.path("nom").asText(),
                            Fixtures.resource(JEU + manifeste.path("fichier").asText()).getBytes(StandardCharsets.UTF_8));
                }
                resultat = inventories.importManifests(projet, manifestes);
            }
            assertEquals("IMPORTED", resultat.status(), "import de l'inventaire de " + nom + " : " + resultat.reason());
            // Composants écartés faute de PURL, signalés à l'import (INV-01, décision D6) : exactement ceux annotés.
            List<String> signales = new ArrayList<>();
            projetDuJeu.path("composantsSignales").forEach(signale -> signales.add(String.join("|", signale.path("groupe").asText(),
                    signale.path("nom").asText(), signale.path("version").asText(), signale.path("motif").asText())));
            List<String> observes = new ArrayList<>();
            resultat.ignoredComponents().forEach(composantIgnore -> observes.add(String.join("|", String.valueOf(composantIgnore.group()),
                    String.valueOf(composantIgnore.name()), String.valueOf(composantIgnore.version()), composantIgnore.reason())));
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
            avis.path("aliases").forEach(alias -> {
                if (alias.asText().startsWith("CVE-")) {
                    cve.add(alias.asText());
                }
            });
            aliasCve.put(id, cve);
        }
        return new AvisDuJeu(avisParId, modifieParId, aliasCve);
    }

    private static Map<String, JsonNode> attendus(JsonNode jeu, Map<String, Set<String>> composantsParProjet,
            Set<String> idsAvis) {
        Map<String, JsonNode> attendus = new LinkedHashMap<>();
        for (JsonNode annotation : jeu.path("verite")) {
            if (!annotation.path("horsInventaire").asBoolean(false)) {
                assertTrue(dansLInventaire(composantsParProjet, annotation), "composant annoté absent de l'inventaire : " + annotation);
            }
            assertTrue(idsAvis.contains(annotation.path("avis").asText()), "avis annoté absent du jeu : " + annotation);
            attendus.put(cle(annotation), annotation);
        }
        return attendus;
    }

    private static int vraisNegatifs(JsonNode jeu, Map<String, Set<String>> composantsParProjet,
            Map<String, JsonNode> attendus, Map<String, Observe> observes) {
        int vraisNegatifs = 0;
        for (JsonNode negatif : jeu.path("negatifs")) {
            assertTrue(dansLInventaire(composantsParProjet, negatif), "composant négatif absent de l'inventaire : " + negatif);
            assertFalse(attendus.containsKey(cle(negatif)), "paire annotée à la fois positive et négative : " + negatif);
            if (!observes.containsKey(cle(negatif))) {
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
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT count(*) FROM signal_alias a JOIN signal s ON s.id = a.signal_id WHERE s.external_id = ? AND a.alias = ?")) {
            for (Map.Entry<String, List<String>> entree : aliasCve.entrySet()) {
                for (String cve : entree.getValue()) {
                    attendus++;
                    requete.setString(1, entree.getKey());
                    requete.setString(2, cve);
                    try (ResultSet resultat = requete.executeQuery()) {
                        resultat.next();
                        indexes += resultat.getInt(1);
                    }
                }
            }
        }
        return indexes + "/" + attendus;
    }

    /** Nombre de couples (projet, composant, CVE) portés par plusieurs signaux. */
    private static long doublonsAlias(Map<String, Observe> observes, Map<String, List<String>> aliasCve) {
        Map<String, Set<String>> signauxParComposantEtCve = new HashMap<>();
        for (String paire : observes.keySet()) {
            String[] parties = paire.split("\\|", 3);
            for (String cve : aliasCve.getOrDefault(parties[1], List.of())) {
                signauxParComposantEtCve.computeIfAbsent(parties[0] + "|" + parties[2] + "|" + cve, cle -> new HashSet<>())
                        .add(parties[1]);
            }
        }
        return signauxParComposantEtCve.values().stream().filter(signaux -> signaux.size() > 1).count();
    }

    private static String cle(JsonNode annotation) {
        return annotation.path("projet").asText() + "|" + annotation.path("avis").asText() + "|"
                + norme(annotation.path("composant").asText());
    }

    /** Identité d'un composant : clé de paquet normalisée et version, sans qualificatifs. */
    private static String norme(String composant) {
        return PackageKey.parse(composant)
                .map(paquet -> paquet.key() + "@" + (paquet.version() == null ? "" : paquet.version()))
                .orElse(composant);
    }

    private Map<String, Observe> observes() throws SQLException {
        Map<String, Observe> observes = new TreeMap<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT p.name, s.external_id, co.purl, i.level, i.transitive
                FROM project_impact i JOIN project p ON p.id = i.project_id JOIN signal s ON s.id = i.signal_id
                     JOIN component co ON co.id = i.component_id
                """); ResultSet resultat = requete.executeQuery()) {
            while (resultat.next()) {
                String cle = resultat.getString(1) + "|" + resultat.getString(2) + "|" + norme(resultat.getString(3));
                Observe precedent = observes.put(cle, new Observe(resultat.getString(4), (Boolean) resultat.getObject(5)));
                assertNull(precedent, "impact en double pour " + cle);
            }
        }
        return observes;
    }

    private Map<String, Set<String>> composants() throws SQLException {
        Map<String, Set<String>> composants = new HashMap<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT p.name, co.purl FROM component co JOIN project p ON p.id = co.project_id");
                ResultSet resultat = requete.executeQuery()) {
            while (resultat.next()) {
                composants.computeIfAbsent(resultat.getString(1), cle -> new HashSet<>()).add(norme(resultat.getString(2)));
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
        StringBuilder rapport = new StringBuilder("# Mesure calculée — jeu de référence de la corrélation\n\n");
        rapport.append("Exécution du ").append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append(".\n\n");
        rapport.append("| Métrique | Valeur (VP/FP/FN pour les comptes) |\n|---|---|\n");
        metriques.forEach((cle, valeur) -> rapport.append("| ").append(cle).append(" | ").append(valeur).append(" |\n"));
        rapport.append("\n## Écarts\n\n");
        ecarts.forEach(ecart -> rapport.append("- ").append(ecart).append('\n'));
        rapport.append("\n## Détail (projet | avis | composant → attendu / observé)\n\n");
        Set<String> cles = new TreeSet<>(attendus.keySet());
        cles.addAll(observes.keySet());
        for (String paire : cles) {
            JsonNode annotation = attendus.get(paire);
            Observe observation = observes.get(paire);
            rapport.append("- ").append(paire).append(" → ").append(annotation == null ? AUCUN : annotation.path("niveau").asText())
                    .append(" / ")
                    .append(observation == null ? AUCUN : observation.niveau()).append('\n');
        }
        Path dossier = Path.of("target", "jeu-reference-correlation");
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("mesure.md"), rapport.toString(), StandardCharsets.UTF_8);
    }
}
