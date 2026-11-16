package io.argos.chain;

import static io.restassured.RestAssured.given;
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
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.HeaderCallerResolver;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.argos.workspace.WorkspaceDirectory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/** Abonnements de veille (tâches 5.1 à 5.3) : ABO-01 à ABO-06 par l'API REST. */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class SubscriptionTest {

    @Inject
    TestDb db;

    @Inject
    WorkspaceDirectory workspaces;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    private UUID rh;
    private UUID paie;

    @BeforeEach
    void reset() {
        db.reset();
        rh = workspaces.create("Équipe RH");
        paie = workspaces.create("Équipe Paie");
    }

    private static RequestSpecification as(String user, UUID... memberOf) {
        return given().header(HeaderCallerResolver.USER, user)
                .header(HeaderCallerResolver.WORKSPACES, String.join(",", java.util.Arrays.stream(memberOf).map(UUID::toString).toList()))
                .contentType(ContentType.JSON);
    }

    private static RequestSpecification admin(String user, UUID workspace) {
        return as(user, workspace).header(HeaderCallerResolver.ADMIN, workspace.toString());
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
        as("alice", rh).body("{\"filters\":[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"}],\"channel\":\"in_app\","
                + "\"frequency\":\"daily\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribute", equalTo("scope"));
        assertEquals(0, db.count("SELECT count(*) FROM subscription"));
    }

    @Test
    void abo02S1AbonnementComplet() {
        as("alice", rh).body(personal("[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"}]"))
                .when().post("/api/v1/subscriptions")
                .then().statusCode(201)
                .body("scope", equalTo("PERSONAL"))
                .body("ownerUserId", equalTo("alice"))
                .body("channel", equalTo("IN_APP"))
                .body("frequency", equalTo("DAILY"))
                .body("mandatory", equalTo(false))
                .body("state", equalTo("ACTIVE"))
                .body("createdAt", notNullValue());
    }

    @Test
    void abo02S2AttributManquant() {
        as("alice", rh).body("{\"scope\":\"PERSONAL\",\"filters\":[],\"channel\":\"in_app\",\"frequency\":\"daily\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribute", equalTo("filters"));
        as("alice", rh).body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"}],\"frequency\":\"daily\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribute", equalTo("channel"));
        as("alice", rh).body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"}],\"channel\":\"x\",\"frequency\":\"daily\"}")
                .when().post("/api/v1/subscriptions")
                .then().statusCode(400).body("attribute", equalTo("mandatory"));
        assertEquals(0, db.count("SELECT count(*) FROM subscription"));
    }

    @Test
    void abo06SeulLAdministrateurDeLEspaceCreeUnAbonnementTeam() {
        as("bob", rh).body(team(rh, "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]", true))
                .when().post("/api/v1/subscriptions").then().statusCode(403);
        assertEquals(1, db.count("SELECT count(*) FROM access_refusal WHERE user_id = 'bob'"));
        admin("carole", rh).body(team(rh, "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]", true))
                .when().post("/api/v1/subscriptions").then().statusCode(201).body("ownerWorkspaceId", equalTo(rh.toString()));
    }

    @Test
    void abo03S1NouveauMembreHeriteDuSocleTeam() {
        publish("GHSA-JAVA", "Maven", "org.exemple:lib", "pkg:maven/org.exemple/lib");
        admin("carole", rh).body(team(rh, "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]", true))
                .when().post("/api/v1/subscriptions").then().statusCode(201);

        as("dave").when().get("/api/v1/me/watch").then().statusCode(200).body("$", empty());
        as("dave", rh).when().get("/api/v1/me/watch").then().statusCode(200)
                .body("externalId", hasItem("GHSA-JAVA"));
    }

    @Test
    void abo04S1TechnologieHorsPerimetreDeLEquipe() {
        publish("GHSA-RUST", "crates.io", "hyper", "pkg:cargo/hyper");
        as("erin", rh).body(personal("[{\"type\":\"TECHNOLOGY\",\"value\":\"Rust\"}]"))
                .when().post("/api/v1/subscriptions").then().statusCode(201);

        as("erin", rh).when().get("/api/v1/me/watch").then().statusCode(200)
                .body("externalId", hasItem("GHSA-RUST"));
    }

    @Test
    void abo05S1EtS2AucunDroitAccordeParUnAbonnement() {
        UUID projetPaie = projects.create(paie, "paie", null, null);
        inventories.importSbom(projetPaie, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"h", "pkg:cargo/hyper@0.14.0"}), List.of("h")).getBytes(StandardCharsets.UTF_8), "ci");
        publish("GHSA-RUST2", "crates.io", "hyper", "pkg:cargo/hyper");
        assertEquals(1, db.count("SELECT count(*) FROM project_impact WHERE project_id = '" + projetPaie + "'"));

        as("frank", rh).body(personal("[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"},{\"type\":\"PROJECT\",\"value\":\""
                + projetPaie + "\"}]")).when().post("/api/v1/subscriptions").then().statusCode(201);

        as("frank", rh).when().get("/api/v1/me/watch").then().statusCode(200)
                .body("externalId", hasItem("GHSA-RUST2"))
                .body("find { it.externalId == 'GHSA-RUST2' }.impacts", hasSize(0));
        // Les droits de frank sont inchangés : l'impact du projet de l'espace Paie reste inaccessible.
        UUID impact = db.uuid("SELECT id FROM project_impact");
        as("frank", rh).when().get("/api/v1/impacts/" + impact).then().statusCode(404);
        as("frank", rh).when().get("/api/v1/projects/" + projetPaie + "/inventory").then().statusCode(404);
        // Un membre de l'espace Paie voit l'impact.
        as("gina", paie).body(personal("[{\"type\":\"TECHNOLOGY\",\"value\":\"rust\"}]")).when().post("/api/v1/subscriptions")
                .then().statusCode(201);
        as("gina", paie).when().get("/api/v1/me/watch").then().statusCode(200)
                .body("find { it.externalId == 'GHSA-RUST2' }.impacts", hasSize(1));
    }

    @Test
    void abo06TeamObligatoireNonQuittableEtFacultatifQuittable() {
        String mandatory = admin("carole", rh).body(team(rh, "[{\"type\":\"TECHNOLOGY\",\"value\":\"java\"}]", true))
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");
        String optional = admin("carole", rh).body(team(rh, "[{\"type\":\"TECHNOLOGY\",\"value\":\"python\"}]", false))
                .when().post("/api/v1/subscriptions").then().statusCode(201).extract().path("id");

        as("dave", rh).when().post("/api/v1/subscriptions/" + mandatory + "/leave").then().statusCode(409)
                .body("attribute", equalTo("mandatory"));
        as("dave", rh).when().post("/api/v1/subscriptions/" + optional + "/leave").then().statusCode(204);

        as("dave", rh).when().get("/api/v1/subscriptions").then().statusCode(200)
                .body("id", hasItem(mandatory)).body("id", not(hasItem(optional)));
        as("carole", rh).when().get("/api/v1/subscriptions").then().statusCode(200).body("id", hasItem(optional));
        as("zoe", paie).when().post("/api/v1/subscriptions/" + optional + "/leave").then().statusCode(404);
    }
}
