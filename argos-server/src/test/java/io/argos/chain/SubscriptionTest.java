package io.argos.chain;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Abonnements de veille (tâches 5.1 à 5.3) : ABO-01 à ABO-06 par l'API REST, avec des jetons réels
 * et des appartenances réelles aux espaces (gerer-comptes-et-acces).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class SubscriptionTest {

    private static final String JAVA = "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]";
    private static final String RUST = "[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"}]";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    javax.sql.DataSource dataSource;

    @Inject
    CorrelationEngine engine;

    /** Espace administré par karim. */
    private UUID espaceRh;
    /** Espace administré par oscar. */
    private UUID paie;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        espaceRh = Api.nouvelEspace("karim", "Équipe RH");
        paie = Api.nouvelEspace("oscar", "Équipe Paie");
    }

    private static RequestSpecification as(String user) {
        return Api.enTantQue(user).contentType(ContentType.JSON);
    }

    /** Fait de l'utilisateur un lecteur de l'espace, par invitation acceptée. */
    private void membre(String user, UUID espace) {
        Api.ajouterMembre(espace.equals(espaceRh) ? "karim" : "oscar", espace.toString(), user, "LECTEUR");
    }

    private static String personal(String filters) {
        return "{\"scope\":\"PERSONAL\",\"filters\":" + filters + ",\"channel\":\"in_app\",\"frequency\":\"daily\",\"mandatory\":false}";
    }

    private static String team(UUID workspace, String filters, boolean mandatory) {
        return "{\"scope\":\"TEAM\",\"teamWorkspaceId\":\"" + workspace + "\",\"filters\":" + filters
                + ",\"channel\":\"in_app\",\"frequency\":\"weekly\",\"mandatory\":" + mandatory + "}";
    }

    private void publish(String id, String ecosystem, String name, String purl) {
        String json = Fixtures.osv(id, "2026-11-10T00:00:00Z", ecosystem, name, purl, "0", "9.9.9");
        Fixtures.stubOsv(id, "2026-11-10T00:00:00Z", json);
        synchronizer.synchronize("OSV");
    }

    @Test
    void abo01S1AbonnementSansPorteeRefuse() {
        as("alice").body("{\"filters\":" + RUST + ",\"channel\":\"in_app\",\"frequency\":\"daily\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribut", equalTo("scope"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM subscription"));
    }

    @Test
    void abo02S1AbonnementComplet() {
        as("alice").body(personal(RUST))
                .when().post("/api/v1/subscriptions")
                .then().statusCode(201)
                .body("scope", equalTo("PERSONAL"))
                .body("ownerUserId", equalTo(Api.compte("alice").toString()))
                .body("channel", equalTo("IN_APP"))
                .body("frequency", equalTo("DAILY"))
                .body("mandatory", equalTo(false))
                .body("state", equalTo("ACTIVE"))
                .body("createdAt", notNullValue());
    }

    @Test
    void abo02S2AttributManquant() {
        as("alice").body("{\"scope\":\"PERSONAL\",\"filters\":[],\"channel\":\"in_app\",\"frequency\":\"daily\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribut", equalTo("filters"));
        as("alice").body("{\"scope\":\"PERSONAL\",\"filters\":" + RUST + ",\"frequency\":\"daily\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribut", equalTo("channel"));
        as("alice").body("{\"scope\":\"PERSONAL\",\"filters\":" + RUST + ",\"channel\":\"x\",\"frequency\":\"daily\"}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribut", equalTo("mandatory"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM subscription"));
    }

    @Test
    void abo06SeulLAdministrateurDeLEspaceCreeUnAbonnementTeam() {
        membre("jules", espaceRh);
        as("jules").body(team(espaceRh, JAVA, true)).when().post("/api/v1/subscriptions").then().statusCode(403);
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("jules")
                + "' AND ressource = 'POST /api/v1/subscriptions' AND motif = 'ROLE_INSUFFISANT'"));
        as("karim").body(team(espaceRh, JAVA, true)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).body("ownerWorkspaceId", equalTo(espaceRh.toString()));
    }

    @Test
    void abo03S1NouveauMembreHeriteDuSocleTeam() {
        publish("GHSA-JAVA", "Maven", "org.exemple:lib", "pkg:maven/org.exemple/lib");
        as("karim").body(team(espaceRh, JAVA, true)).when().post("/api/v1/subscriptions").then().statusCode(201);

        Api.compte("lina");
        as("lina").when().get("/api/v1/me/watch").then().statusCode(200).body("$", empty());
        membre("lina", espaceRh);
        as("lina").when().get("/api/v1/me/watch").then().statusCode(200).body("externalId", hasItem("GHSA-JAVA"));
    }

    @Test
    void abo04S1TechnologieHorsPerimetreDeLEquipe() {
        publish("GHSA-RUST", "crates.io", "hyper", "pkg:cargo/hyper");
        membre("marc", espaceRh);
        as("marc").body(personal("[{\"type\":\"TECHNOLOGY\",\"value\":\"Rust\"}]"))
                .when().post("/api/v1/subscriptions").then().statusCode(201);

        as("marc").when().get("/api/v1/me/watch").then().statusCode(200).body("externalId", hasItem("GHSA-RUST"));
    }

    @Test
    void abo05S1EtS2AucunDroitAccordeParUnAbonnement() {
        UUID projetPaie = projects.create(paie, "paie", null, null);
        inventories.importSbom(projetPaie, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"h", "pkg:cargo/hyper@0.14.0"}), List.of("h"))
                .getBytes(StandardCharsets.UTF_8), "ci");
        publish("GHSA-RUST2", "crates.io", "hyper", "pkg:cargo/hyper");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_impact WHERE project_id = '" + projetPaie + "'"));

        membre("nina", espaceRh);
        as("nina").body(personal("[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"},{\"type\":\"PROJECT\",\"value\":\""
                + projetPaie + "\"}]")).when().post("/api/v1/subscriptions").then().statusCode(201);

        as("nina").when().get("/api/v1/me/watch").then().statusCode(200)
                .body("externalId", hasItem("GHSA-RUST2"))
                .body("find { it.externalId == 'GHSA-RUST2' }.impacts", hasSize(0));
        // Les droits de nina sont inchangés : l'impact du projet de l'espace Paie reste inaccessible.
        UUID impact = baseDeTest.uuid("SELECT id FROM project_impact");
        as("nina").when().get("/api/v1/impacts/" + impact).then().statusCode(404);
        as("nina").when().get("/api/v1/projects/" + projetPaie + "/inventory").then().statusCode(404);
        // Un membre de l'espace Paie voit l'impact.
        as("oscar").body(personal(RUST)).when().post("/api/v1/subscriptions").then().statusCode(201);
        as("oscar").when().get("/api/v1/me/watch").then().statusCode(200)
                .body("find { it.externalId == 'GHSA-RUST2' }.impacts", hasSize(1));
    }

    @Test
    void abo06TeamObligatoireNonQuittableEtFacultatifQuittable() {
        String mandatory = as("karim").body(team(espaceRh, JAVA, true))
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");
        String optional = as("karim").body(team(espaceRh, "[{\"type\":\"TECHNOLOGY\",\"value\":\"python\"}]", false))
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");
        membre("lina", espaceRh);
        membre("paul", paie);

        as("lina").when().post("/api/v1/subscriptions/" + mandatory + "/leave").then().statusCode(409)
                .body("attribut", equalTo("mandatory"));
        as("lina").when().post("/api/v1/subscriptions/" + optional + "/leave").then().statusCode(204);

        as("lina").when().get("/api/v1/subscriptions").then().statusCode(200)
                .body("id", hasItem(mandatory)).body("id", not(hasItem(optional)));
        as("karim").when().get("/api/v1/subscriptions").then().statusCode(200).body("id", hasItem(optional));
        as("paul").when().post("/api/v1/subscriptions/" + optional + "/leave").then().statusCode(404);
    }

    // ------------------------------------------------------------------ ABO-07, ABO-08 (automatiser-chaine-veille, 1.5)

    private static final String DEUX_PAQUETS = "[{\"type\":\"PACKAGE\",\"value\":\"pkg:maven/org.exemple/lib\"},"
            + "{\"type\":\"PACKAGE\",\"value\":\"pkg:maven/org.exemple/autre\"}]";

    private static String personalEmailImmediat(String filters) {
        return "{\"scope\":\"PERSONAL\",\"filters\":" + filters
                + ",\"channel\":\"email\",\"frequency\":\"immediate\",\"mandatory\":false}";
    }

    private UUID projetRhAvecLib() {
        UUID projet = projects.create(espaceRh, "portail-rh", null, null);
        inventories.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"lib", "pkg:maven/org.exemple/lib@1.2.0"}), List.of("lib"))
                .getBytes(StandardCharsets.UTF_8), "ci");
        return projet;
    }

    private long alertesDe(String user) {
        return baseDeTest.count("SELECT count(*) FROM alerte WHERE compte_id = '" + Api.compte(user) + "'");
    }

    @Test
    void abo07S1EtS2SuspensionPuisReactivationSansRejeu() {
        UUID projet = projetRhAvecLib();
        membre("jules", espaceRh);
        String id = as("jules").body(personalEmailImmediat(DEUX_PAQUETS))
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");

        as("jules").when().post("/api/v1/subscriptions/" + id + "/suspension").then().statusCode(200)
                .body("state", equalTo("SUSPENDED"))
                .body("filters", hasSize(2))
                .body("filters.value", hasItem("pkg:maven/org.exemple/autre"))
                .body("channel", equalTo("EMAIL"))
                .body("frequency", equalTo("IMMEDIATE"));
        // Idempotence : suspendre un abonnement suspendu répond 200 sans effet.
        as("jules").when().post("/api/v1/subscriptions/" + id + "/suspension").then().statusCode(200)
                .body("state", equalTo("SUSPENDED"));

        publish("GHSA-SUSP1", "Maven", "org.exemple:lib", "pkg:maven/org.exemple/lib");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM project_impact"), "le signal est corrélé");
        assertEquals(0, alertesDe("jules"), "aucune alerte au titre d'un abonnement suspendu");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM resume_element WHERE compte_id = '" + Api.compte("jules") + "'"));

        as("jules").when().post("/api/v1/subscriptions/" + id + "/reactivation").then().statusCode(200)
                .body("state", equalTo("ACTIVE")).body("filters", hasSize(2));
        publish("GHSA-SUSP2", "Maven", "org.exemple:lib", "pkg:maven/org.exemple/lib");

        assertEquals(1, alertesDe("jules"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte a JOIN signal s ON s.id = a.signal_id WHERE a.compte_id = '"
                + Api.compte("jules") + "' AND s.external_id = 'GHSA-SUSP2'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM alerte a JOIN signal s ON s.id = a.signal_id"
                + " WHERE s.external_id = 'GHSA-SUSP1'"), "la période de suspension n'est pas rejouée");

        // Recorrélation du projet après la réactivation (import quotidien, J4) : impact inchangé, aucune alerte
        // pour le signal corrélé pendant la suspension ([PROPOSITION] PI-4, intégration du 2026-11-19).
        engine.correlateProject(projet);
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM alerte a JOIN signal s ON s.id = a.signal_id"
                + " WHERE s.external_id = 'GHSA-SUSP1'"), "la recorrélation ne rejoue pas la période de suspension");
        assertEquals(1, alertesDe("jules"));
    }

    @Test
    void abo07S3SuspensionDUnTeamParUnContributeurRefusee() {
        String id = as("karim").body(team(espaceRh, JAVA, true)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        Api.ajouterMembre("karim", espaceRh.toString(), "jules", "CONTRIBUTEUR");

        as("jules").when().post("/api/v1/subscriptions/" + id + "/suspension").then().statusCode(403);
        as("jules").when().post("/api/v1/subscriptions/" + id + "/reactivation").then().statusCode(403);
        as("jules").when().delete("/api/v1/subscriptions/" + id).then().statusCode(403);

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("jules")
                + "' AND ressource = 'POST /api/v1/subscriptions/" + id + "/suspension' AND motif = 'ROLE_INSUFFISANT'"));
        assertEquals("ACTIVE", baseDeTest.string("SELECT state FROM subscription WHERE id = '" + id + "'"));
        // Un non-membre ne voit pas l'abonnement : 404, sans le révéler.
        Api.compte("paul");
        as("paul").when().post("/api/v1/subscriptions/" + id + "/suspension").then().statusCode(404);
        // L'administrateur de l'espace suspend et réactive.
        as("karim").when().post("/api/v1/subscriptions/" + id + "/suspension").then().statusCode(200)
                .body("state", equalTo("SUSPENDED"));
        as("karim").when().post("/api/v1/subscriptions/" + id + "/reactivation").then().statusCode(200)
                .body("state", equalTo("ACTIVE"));
    }

    @Test
    void abo07S3RlsRefuseLaModificationParUnContributeurSousArgosApp() throws Exception {
        String id = as("karim").body(team(espaceRh, JAVA, true)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        String personnel = as("jules").body(personal(RUST)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        Api.ajouterMembre("karim", espaceRh.toString(), "jules", "CONTRIBUTEUR");

        // Même si la couche API se trompait, la base refuse sous argos_app (politiques V18).
        assertEquals(0, sousArgosApp("jules", "UPDATE subscription SET state = 'SUSPENDED' WHERE id = '" + id + "'"));
        assertEquals(0, sousArgosApp("jules", "DELETE FROM subscription WHERE id = '" + id + "'"));
        assertEquals(0, sousArgosApp("ines", "UPDATE subscription SET state = 'SUSPENDED' WHERE id = '" + personnel + "'"));
        assertEquals(0, sousArgosApp("ines", "DELETE FROM subscription WHERE id = '" + personnel + "'"));
        assertEquals(1, sousArgosApp("karim", "UPDATE subscription SET state = 'SUSPENDED' WHERE id = '" + id + "'"));
        assertEquals(1, sousArgosApp("jules", "UPDATE subscription SET state = 'SUSPENDED' WHERE id = '" + personnel + "'"));
        // Seul l'état est modifiable : les critères restent hors de portée d'argos_app.
        java.sql.SQLException refus = org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> sousArgosApp("jules", "UPDATE subscription SET filters = '[]' WHERE id = '" + personnel + "'"));
        assertEquals("42501", refus.getSQLState(), "privilège UPDATE limité à la colonne state");
        assertEquals("SUSPENDED", baseDeTest.string("SELECT state FROM subscription WHERE id = '" + id + "'"));
        assertEquals(1, baseDeTest.count("SELECT jsonb_array_length(filters) FROM subscription WHERE id = '" + personnel + "'"));
    }

    /** Exécute une écriture dans une transaction sous argos_app avec le compte donné ; renvoie le nombre de lignes. */
    private int sousArgosApp(String user, String sql) throws java.sql.SQLException {
        UUID compte = Api.compte(user);
        try (java.sql.Connection connexion = dataSource.getConnection()) {
            connexion.setAutoCommit(false);
            try (java.sql.Statement requete = connexion.createStatement()) {
                requete.execute("SET LOCAL ROLE argos_app");
                requete.execute("SELECT set_config('argos.compte_courant', '" + compte + "', true), "
                        + "set_config('argos.espace_courant', '', true)");
                int lignes = requete.executeUpdate(sql);
                connexion.commit();
                return lignes;
            } catch (java.sql.SQLException erreur) {
                connexion.rollback();
                throw erreur;
            }
        }
    }

    @Test
    void abo07S4AbonnementsSuspendusListesAvecLeurEtat() {
        String actif = as("jules").body(personal(RUST)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        String suspendu = as("jules").body(personal(JAVA)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        as("jules").when().post("/api/v1/subscriptions/" + suspendu + "/suspension").then().statusCode(200);

        as("jules").when().get("/api/v1/subscriptions").then().statusCode(200)
                .body("$", hasSize(2))
                .body("find { it.id == '" + actif + "' }.state", equalTo("ACTIVE"))
                .body("find { it.id == '" + suspendu + "' }.state", equalTo("SUSPENDED"));
        // Le retrait d'un PERSONAL est une suspension (P-8) : critères conservés, réactivable.
        as("jules").when().post("/api/v1/subscriptions/" + actif + "/leave").then().statusCode(204);
        assertEquals("SUSPENDED", baseDeTest.string("SELECT state FROM subscription WHERE id = '" + actif + "'"));
        as("jules").when().post("/api/v1/subscriptions/" + actif + "/leave").then().statusCode(204);
        as("jules").when().post("/api/v1/subscriptions/" + actif + "/reactivation").then().statusCode(200)
                .body("state", equalTo("ACTIVE")).body("filters[0].value", equalTo("rust"));
    }

    @Test
    void abo08S1SuppressionDUnAbonnementSuspendu() {
        String id = as("jules").body(personal(RUST)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        as("jules").when().post("/api/v1/subscriptions/" + id + "/suspension").then().statusCode(200);

        as("jules").when().delete("/api/v1/subscriptions/" + id).then().statusCode(204);

        as("jules").when().get("/api/v1/subscriptions").then().statusCode(200).body("id", not(hasItem(id)));
        as("jules").when().post("/api/v1/subscriptions/" + id + "/reactivation").then().statusCode(404);
        as("jules").when().delete("/api/v1/subscriptions/" + id).then().statusCode(404);
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM subscription WHERE id = '" + id + "'"));
    }

    @Test
    void abo08S2SuppressionParUnAutreUtilisateurSansRevelerLAbonnement() {
        String id = as("jules").body(personal(RUST)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        String inconnu = UUID.randomUUID().toString();
        Api.compte("ines");

        String corpsAutre = as("ines").when().delete("/api/v1/subscriptions/" + id).then().statusCode(404)
                .extract().asString();
        String corpsInconnu = as("ines").when().delete("/api/v1/subscriptions/" + inconnu).then().statusCode(404)
                .extract().asString();
        as("ines").when().post("/api/v1/subscriptions/" + id + "/suspension").then().statusCode(404);

        assertEquals(corpsInconnu, corpsAutre, "même réponse que pour un abonnement inexistant");
        assertEquals("ACTIVE", baseDeTest.string("SELECT state FROM subscription WHERE id = '" + id + "'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM subscription WHERE id = '" + id + "'"));
    }

    @Test
    void abo08S3SuppressionDUnTeamFacultatifQuitteParDesMembres() {
        String id = as("karim").body(team(espaceRh, JAVA, false)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).extract().path("id");
        membre("lina", espaceRh);
        membre("marc", espaceRh);
        as("lina").when().post("/api/v1/subscriptions/" + id + "/leave").then().statusCode(204);
        as("marc").when().post("/api/v1/subscriptions/" + id + "/leave").then().statusCode(204);
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM subscription_opt_out WHERE subscription_id = '" + id + "'"));

        as("karim").when().delete("/api/v1/subscriptions/" + id).then().statusCode(204);

        assertEquals(0, baseDeTest.count("SELECT count(*) FROM subscription WHERE id = '" + id + "'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM subscription_opt_out WHERE subscription_id = '" + id + "'"));
        for (String user : List.of("karim", "lina", "marc")) {
            as(user).when().get("/api/v1/subscriptions").then().statusCode(200).body("id", not(hasItem(id)));
        }
    }

    @Test
    void contratOpenApiDesActionsSurLesAbonnements() {
        Api.enTantQue("alice").queryParam("format", "json")
                .when().get("/q/openapi")
                .then().statusCode(200)
                .body("paths.'/api/v1/subscriptions'.get", notNullValue())
                .body("paths.'/api/v1/subscriptions/{id}/suspension'.post.responses.'403'", notNullValue())
                .body("paths.'/api/v1/subscriptions/{id}/suspension'.post.responses.'404'", notNullValue())
                .body("paths.'/api/v1/subscriptions/{id}/reactivation'.post.responses.'200'", notNullValue())
                .body("paths.'/api/v1/subscriptions/{id}'.delete.responses.'204'", notNullValue())
                .body("paths.'/api/v1/subscriptions/{id}'.delete.responses.'403'", notNullValue())
                .body("components.schemas.Subscription.properties.keySet()", hasItem("state"));
    }
}
