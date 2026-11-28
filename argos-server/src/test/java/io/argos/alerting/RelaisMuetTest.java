package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
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
import io.argos.testsupport.Asynchrone;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/**
 * Relais SMTP filtré (ALR-06-S2) : la connexion TCP aboutit mais le relais ne répond jamais. Le délai
 * {@code quarkus.mailer.timeout} interrompt chaque tentative, qui est comptée ; l'alerte atteint
 * {@code EN_ECHEC} au nombre maximal de tentatives, sans verrou tenu pendant l'attente.
 */
@QuarkusTest
@TestProfile(RelaisMuetTest.RelaisMuet.class)
@QuarkusTestResource(StubServer.class)
class RelaisMuetTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";

    /** Socket d'écoute qui n'envoie jamais le message d'accueil SMTP. */
    private static final ServerSocket RELAIS_MUET = ouvrir();

    /** Mailer réel dirigé vers le relais muet ; délai de 2 s, deux tentatives au plus. */
    public static class RelaisMuet implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.mailer.mock", "false",
                    "quarkus.mailer.host", "127.0.0.1",
                    "quarkus.mailer.port", String.valueOf(RELAIS_MUET.getLocalPort()),
                    "quarkus.mailer.start-tls", "DISABLED",
                    "quarkus.mailer.timeout", "PT2S",
                    "argos.alerting.envoi.tentatives-max", "2",
                    "argos.alerting.envoi.delai-initial", "PT1M",
                    "argos.alerting.envoi.delai-max", "PT30M");
        }
    }

    private static ServerSocket ouvrir() {
        try {
            return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (java.io.IOException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    @Inject
    TestDb baseDeTest;

    @Inject
    Asynchrone asynchrone;

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
    void alr06S2RelaisMuetChaqueTentativeEstCompteePuisEnEchec() {
        UUID project = projects.create(spaceA, "app", null, null);
        inventories.importSbom(project, Fixtures.cyclonedx("1.6", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                List.<String[]>of(new String[] {"c0", LIB + "@1.2.0"}), List.of("c0")).getBytes(StandardCharsets.UTF_8), "ci");
        asynchrone.attendre();
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body("{\"scope\":\"PERSONAL\",\"filters\":[{\"type\":\"SEVERITY\",\"value\":\"HIGH\"}],"
                        + "\"channel\":\"email\",\"frequency\":\"immediate\",\"mandatory\":false}")
                .when().post("/api/v1/subscriptions").then().statusCode(201);

        String json = Fixtures.osv("GHSA-MUET", "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", LIB, "0", "1.3.0")
                .replace("\"severity\":\"HIGH\"", "\"severity\":\"CRITICAL\"");
        Fixtures.stubOsv("GHSA-MUET", "2026-11-10T00:00:00Z", json);
        Instant instantDepart = Instant.now();
        synchronizer.synchronize("OSV");
        asynchrone.attendre();
        // Première tentative au passage de la tâche d'envoi qui suit l'émission (ALR-10-S2).
        envoi.traiterEnAttente(instantDepart);

        assertEquals("EN_ATTENTE|1", baseDeTest.string("SELECT etat || '|' || tentatives FROM alerte"));
        UUID id = baseDeTest.uuid("SELECT id FROM alerte");
        long debut = System.nanoTime();
        assertEquals(EnvoiAlertes.Issue.EN_ECHEC, envoi.traiterEnAttente(instantDepart.plus(5, ChronoUnit.MINUTES)).get(id));
        assertTrue(System.nanoTime() - debut < 30_000_000_000L, "tentative bornée par quarkus.mailer.timeout");
        assertEquals("EN_ECHEC|2|true", baseDeTest.string(
                "SELECT etat || '|' || tentatives || '|' || (prochain_essai IS NULL) FROM alerte"));
        assertEquals(Map.of(), envoi.traiterEnAttente(instantDepart.plus(1, ChronoUnit.DAYS)), "plus aucune tentative");
    }

    @Test
    void contenuIllisibleCompteUneTentativeEtPasseEnEchec() {
        UUID compte = baseDeTest.uuid("SELECT id FROM compte WHERE email = 'alice@argos.test'");
        UUID id = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO alerte(id, nature, compte_id, canal, fin_periode, titre, contenu, etat_resume_ia, etat, cree_le) "
                + "VALUES ('" + id + "', 'DAILY', '" + compte + "', 'EMAIL', now(), 'Résumé', '{\"inconnu\":1}', "
                + "'SANS_OBJET', 'EN_ATTENTE', now())");
        assertEquals(EnvoiAlertes.Issue.EN_ECHEC, envoi.envoyer(List.of(id), Instant.now()).get(id));
        assertEquals("EN_ECHEC|1", baseDeTest.string("SELECT etat || '|' || tentatives FROM alerte"));
    }
}
