package io.argos.observabilite;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.aigateway.SantePasserelleIa;
import io.argos.alerting.EnvoiAlertes;
import io.argos.alerting.SanteEnvoiAlertes;
import io.argos.ingestion.SanteSources;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Métriques réservées (EXP-14) et santé par tâche planifiée et par dépendance facultative (EXP-15), dans le profil
 * {@link ProfilObservabilite} : planificateur actif avec la seule tâche d'essai, retard toléré nul, IA désactivée.
 */
@QuarkusTest
@TestProfile(ProfilObservabilite.class)
@QuarkusTestResource(StubServer.class)
class MetriquesEtSanteTest {

    private static final String LIB = "pkg:maven/org.exemple/observee";
    private static final String NOM_PROJET = "projet-observabilite-confidentiel";
    private static final Pattern ETIQUETTE = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern UUID_TEXTE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Duration ATTENTE_MAX = Duration.ofSeconds(20);

    @Inject
    TestDb baseDeTest;

    @Inject
    TacheEssai tache;

    @Inject
    ProjectDirectory projets;

    @Inject
    InventoryService inventaires;

    @Inject
    SourceSynchronizer synchroniseur;

    @Inject
    EnvoiAlertes envoi;

    @BeforeEach
    void reinitialiser() {
        baseDeTest.reset();
        Api.moi("alice").statusCode(200);
    }

    @Test
    void exp14S1MetriquesDUneExecutionEnEchec() {
        tache.echouer(true);
        try {
            assertThrows(IllegalStateException.class, () -> tache.executer());
        } finally {
            tache.echouer(false);
        }
        String metriques = lireMetriques();
        assertTrue(valeur(metriques, "argos_taches_executions_seconds_count", "resultat=\"echec\"", TacheEssai.IDENTITE) >= 1,
                metriques);
        assertTrue(metriques.contains("argos_taches_derniere_duree_seconds{tache=\"" + TacheEssai.IDENTITE + "\"}"),
                argos(metriques));
        for (String technique : List.of("http_server_requests_seconds_count", "jvm_memory_used_bytes",
                "agroal_active_count")) {
            assertTrue(metriques.contains(technique), "métrique technique " + technique);
        }
    }

    @Test
    void exp14S2AppelAnonymeOuNonAdministrateurRefuse() {
        given().when().get("/q/metrics").then().statusCode(401).body(not(containsString("argos_")));
        Api.assurerCompte("paul");
        Api.enTantQue("paul").when().get("/q/metrics").then().statusCode(403);
    }

    @Test
    void exp14S3EtiquettesSansDonneePersonnelle() {
        UUID espace = Api.nouvelEspace("alice", "Espace observé");
        UUID projet = projets.create(espace, NOM_PROJET, null, null);
        inventaires.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", LIB + "@1.2.0"}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"SEVERITY\",\"value\":\"HIGH\"}],"
                        + "\"channel\":\"email\",\"frequency\":\"immediate\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);
        String avis = Fixtures.osv("GHSA-OBSERVE", "2026-11-10T00:00:00Z", "Maven", "org.exemple:observee", LIB, "0", "1.3.0")
                .replace("\"severity\":\"HIGH\"", "\"severity\":\"CRITICAL\"");
        Fixtures.stubOsv("GHSA-OBSERVE", "2026-11-10T00:00:00Z", avis);
        synchroniseur.synchronize("OSV");
        envoi.traiterEnAttente(Instant.now());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte WHERE etat = 'ENVOYEE'"), "alerte envoyée");

        String metriques = lireMetriques();
        assertTrue(metriques.contains("argos_alertes_envois_total{issue=\"envoyee\"}"), argos(metriques));
        Matcher etiquettes = ETIQUETTE.matcher(metriques);
        while (etiquettes.find()) {
            String valeurEtiquette = etiquettes.group(2);
            assertTrue(!valeurEtiquette.contains("@") && !UUID_TEXTE.matcher(valeurEtiquette).find()
                    && !valeurEtiquette.contains(NOM_PROJET) && !valeurEtiquette.contains("Espace observé"),
                    "étiquette " + etiquettes.group(1) + "=" + valeurEtiquette);
        }
    }

    @Test
    void exp15S1SourceDegradeeServeurDisponible() {
        baseDeTest.exec("UPDATE source SET state = 'DEGRADED', consecutive_failures = 5, "
                + "last_success_at = '2026-11-20T10:00:00Z' WHERE id = 'OSV'");
        JsonPath sante = disponibilite(200);
        Map<String, Object> donnees = donnees(sante, SanteSources.NOM);
        assertEquals("degrade", donnees.get("etat"));
        assertEquals("DEGRADED", donnees.get("OSV.etat"));
        assertEquals("2026-11-20T10:00:00Z", donnees.get("OSV.derniere-synchronisation-reussie"));
        assertEquals("UP", sante.getString("status"));
        String metriques = lireMetriques();
        assertTrue(metriques.contains("argos_sources{etat=\"DEGRADED\"} 1.0"), argos(metriques));
    }

    @Test
    void exp15S2TacheEnRetardServeurDisponible() {
        String controle = SanteTaches.PREFIXE + TacheEssai.IDENTITE;
        Map<String, Object> donnees = Awaitility.await().atMost(ATTENTE_MAX)
                .until(() -> donnees(disponibilite(200), controle), etat -> "en-retard".equals(etat.get("etat")));
        assertTrue(donnees.containsKey("derniere-execution") || donnees.containsKey("retard-tolere"), donnees.toString());
        assertEquals("PT0S", donnees.get("retard-tolere"));
    }

    @Test
    void exp15S3TacheEtIaDesactiveesSansEchec() {
        JsonPath sante = disponibilite(200);
        List<String> noms = sante.getList("checks.name");
        assertTrue(noms.stream().noneMatch((String nom) -> nom.equals(SanteTaches.PREFIXE + "argos-envoi-alertes")),
                "tâche désactivée sans contrôle : " + noms);
        assertEquals("desactivee", donnees(sante, SantePasserelleIa.NOM).get("etat"));
        given().when().get("/q/health/ready").then().body("checks.status", not(hasItem("DOWN")))
                .body("checks.find { it.name == '" + SanteEnvoiAlertes.NOM + "' }.status", equalTo("UP"));
    }

    private static JsonPath disponibilite(int statut) {
        return given().when().get("/q/health/ready").then().statusCode(statut).extract().jsonPath();
    }

    private static Map<String, Object> donnees(JsonPath sante, String controle) {
        Map<String, Object> donnees = sante.getMap("checks.find { it.name == '" + controle + "' }.data");
        return donnees == null ? Map.of() : donnees;
    }

    private static String lireMetriques() {
        return Api.enTantQue("alice").accept("text/plain").when().get("/q/metrics")
                .then().statusCode(200).extract().asString().replace(",}", "}");
    }

    /** Lignes des métriques d'Argos, pour le message d'un échec. */
    private static String argos(String metriques) {
        return String.join(System.lineSeparator(), metriques.lines().filter(ligne -> ligne.startsWith("argos_")).toList());
    }

    /** Valeur de la série dont la ligne contient l'étiquette et la tâche données, 0 si absente. */
    private static double valeur(String metriques, String nom, String etiquette, String tacheAttendue) {
        return metriques.lines().filter(ligne -> ligne.startsWith(nom + "{") && ligne.contains(etiquette)
                && ligne.contains("tache=\"" + tacheAttendue + "\"")).mapToDouble(ligne -> Double.parseDouble(
                        ligne.substring(ligne.lastIndexOf(' ') + 1))).sum();
    }
}
