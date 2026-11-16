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
    TestDb db;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    /** Espace administré par karim. */
    private UUID rh;
    /** Espace administré par oscar. */
    private UUID paie;

    @BeforeEach
    void reset() {
        db.reset();
        rh = Api.nouvelEspace("karim", "Équipe RH");
        paie = Api.nouvelEspace("oscar", "Équipe Paie");
    }

    private static RequestSpecification as(String user) {
        return Api.en(user).contentType(ContentType.JSON);
    }

    /** Fait de l'utilisateur un lecteur de l'espace, par invitation acceptée. */
    private void membre(String user, UUID espace) {
        Api.ajouterMembre(espace.equals(rh) ? "karim" : "oscar", espace.toString(), user, "LECTEUR");
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
        assertEquals(0, db.count("SELECT count(*) FROM subscription"));
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
        assertEquals(0, db.count("SELECT count(*) FROM subscription"));
    }

    @Test
    void abo06SeulLAdministrateurDeLEspaceCreeUnAbonnementTeam() {
        membre("jules", rh);
        as("jules").body(team(rh, JAVA, true)).when().post("/api/v1/subscriptions").then().statusCode(403);
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("jules")
                + "' AND ressource = 'POST /api/v1/subscriptions' AND motif = 'ROLE_INSUFFISANT'"));
        as("karim").body(team(rh, JAVA, true)).when().post("/api/v1/subscriptions")
                .then().statusCode(201).body("ownerWorkspaceId", equalTo(rh.toString()));
    }

    @Test
    void abo03S1NouveauMembreHeriteDuSocleTeam() {
        publish("GHSA-JAVA", "Maven", "org.exemple:lib", "pkg:maven/org.exemple/lib");
        as("karim").body(team(rh, JAVA, true)).when().post("/api/v1/subscriptions").then().statusCode(201);

        Api.compte("lina");
        as("lina").when().get("/api/v1/me/watch").then().statusCode(200).body("$", empty());
        membre("lina", rh);
        as("lina").when().get("/api/v1/me/watch").then().statusCode(200).body("externalId", hasItem("GHSA-JAVA"));
    }

    @Test
    void abo04S1TechnologieHorsPerimetreDeLEquipe() {
        publish("GHSA-RUST", "crates.io", "hyper", "pkg:cargo/hyper");
        membre("marc", rh);
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
        assertEquals(1, db.count("SELECT count(*) FROM project_impact WHERE project_id = '" + projetPaie + "'"));

        membre("nina", rh);
        as("nina").body(personal("[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"},{\"type\":\"PROJECT\",\"value\":\""
                + projetPaie + "\"}]")).when().post("/api/v1/subscriptions").then().statusCode(201);

        as("nina").when().get("/api/v1/me/watch").then().statusCode(200)
                .body("externalId", hasItem("GHSA-RUST2"))
                .body("find { it.externalId == 'GHSA-RUST2' }.impacts", hasSize(0));
        // Les droits de nina sont inchangés : l'impact du projet de l'espace Paie reste inaccessible.
        UUID impact = db.uuid("SELECT id FROM project_impact");
        as("nina").when().get("/api/v1/impacts/" + impact).then().statusCode(404);
        as("nina").when().get("/api/v1/projects/" + projetPaie + "/inventory").then().statusCode(404);
        // Un membre de l'espace Paie voit l'impact.
        as("oscar").body(personal(RUST)).when().post("/api/v1/subscriptions").then().statusCode(201);
        as("oscar").when().get("/api/v1/me/watch").then().statusCode(200)
                .body("find { it.externalId == 'GHSA-RUST2' }.impacts", hasSize(1));
    }

    @Test
    void abo06TeamObligatoireNonQuittableEtFacultatifQuittable() {
        String mandatory = as("karim").body(team(rh, JAVA, true))
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");
        String optional = as("karim").body(team(rh, "[{\"type\":\"TECHNOLOGY\",\"value\":\"python\"}]", false))
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");
        membre("lina", rh);
        membre("paul", paie);

        as("lina").when().post("/api/v1/subscriptions/" + mandatory + "/leave").then().statusCode(409)
                .body("attribut", equalTo("mandatory"));
        as("lina").when().post("/api/v1/subscriptions/" + optional + "/leave").then().statusCode(204);

        as("lina").when().get("/api/v1/subscriptions").then().statusCode(200)
                .body("id", hasItem(mandatory)).body("id", not(hasItem(optional)));
        as("karim").when().get("/api/v1/subscriptions").then().statusCode(200).body("id", hasItem(optional));
        as("paul").when().post("/api/v1/subscriptions/" + optional + "/leave").then().statusCode(404);
    }
}
