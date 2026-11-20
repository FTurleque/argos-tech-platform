package io.argos.alerting;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/**
 * Serveur d'e-mails injoignable (ALR-06-S2, tâche 3.2) : le vrai client SMTP de Quarkus Mailer vise un
 * port local fermé. L'envoi est retenté avec un délai croissant puis marqué en échec ; l'alerte reste
 * consultable dans Argos.
 */
@QuarkusTest
@TestProfile(EchecSmtpTest.SmtpInjoignable.class)
@QuarkusTestResource(StubServer.class)
class EchecSmtpTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";

    /** Mailer réel (non simulé) dirigé vers un port local sans serveur ; trois tentatives au plus. */
    public static class SmtpInjoignable implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.mailer.mock", "false",
                    "quarkus.mailer.host", "127.0.0.1",
                    "quarkus.mailer.port", String.valueOf(portFerme()),
                    "quarkus.mailer.start-tls", "DISABLED",
                    "argos.alerting.envoi.tentatives-max", "3",
                    "argos.alerting.envoi.delai-initial", "PT1M",
                    "argos.alerting.envoi.delai-max", "PT30M");
        }

        private static int portFerme() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (java.io.IOException erreur) {
                throw new IllegalStateException(erreur);
            }
        }
    }

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    EnvoiAlertes envoi;

    private UUID spaceA;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
        spaceA = Api.nouvelEspace("alice", "Espace A");
    }

    @Test
    void alr06S2EnvoiRetenteMarqueEnEchecEtAlerteVisible() {
        UUID project = projects.create(spaceA, "app", null, null);
        inventories.importSbom(project, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", LIB + "@1.2.0"}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        Api.en("alice").contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"SEVERITY\",\"value\":\"HIGH\"}],"
                        + "\"channel\":\"email\",\"frequency\":\"immediate\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);

        String json = Fixtures.osv("GHSA-SMTP", "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.3.0")
                .replace("\"severity\":\"HIGH\"", "\"severity\":\"CRITICAL\"");
        Fixtures.stubOsv("GHSA-SMTP", "2026-11-10T00:00:00Z", json);
        Instant instantDepart = Instant.now();
        synchronizer.synchronize("OSV");

        // Première tentative à l'émission : échec, nouvelle tentative programmée.
        assertEquals("EN_ATTENTE|1", etat());
        UUID id = baseDeTest.uuid("SELECT id FROM alerte");
        assertEquals(Map.of(), envoi.traiterEnAttente(instantDepart), "aucune tentative avant l'échéance");

        assertEquals(EnvoiAlertes.Issue.A_RETENTER, envoi.traiterEnAttente(instantDepart.plus(2, ChronoUnit.MINUTES)).get(id));
        assertEquals("EN_ATTENTE|2", etat());
        assertEquals(EnvoiAlertes.Issue.EN_ECHEC, envoi.traiterEnAttente(instantDepart.plus(10, ChronoUnit.MINUTES)).get(id));
        assertEquals("EN_ECHEC|3", etat());
        assertEquals(Map.of(), envoi.traiterEnAttente(instantDepart.plus(1, ChronoUnit.DAYS)), "plus aucune tentative");

        Api.en("alice").when().get("/api/v1/alertes")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].etat", equalTo("EN_ECHEC"))
                .body("[0].tentatives", equalTo(3))
                .body("[0].canal", equalTo("EMAIL"));
    }

    private String etat() {
        return baseDeTest.string("SELECT etat || '|' || tentatives FROM alerte");
    }
}
