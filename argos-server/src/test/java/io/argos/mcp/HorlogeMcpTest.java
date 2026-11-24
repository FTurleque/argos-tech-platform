package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.HorlogeReglable;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Date du jour au fuseau configuré (AUD-QUA-10, tâche 1.3 du changement {@code injecter-horloge-et-executeurs}) :
 * à 23:30 UTC le 24 novembre, il est déjà le 25 à Paris ({@code argos.alerting.fuseau}) ; l'outil
 * {@code upcoming_releases} compte son horizon depuis la date de Paris, pas depuis celle du fuseau de la JVM.
 */
@QuarkusTest
class HorlogeMcpTest {

    private static final Instant MINUIT_MOINS_TRENTE_UTC = Instant.parse("2026-11-24T23:30:00Z");

    @Inject
    TestDb baseDeTest;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        Api.assurerCompte("karim");
        QuarkusMock.installMockForType(new HorlogeReglable(MINUIT_MOINS_TRENTE_UTC, ZoneId.of("Europe/Paris")), Clock.class);
    }

    @Test
    void lHorizonPartDeLaDateDuJourAuFuseauConfigure() {
        ExtractableResponse<Response> reponse = Mcp.appeler("karim", "upcoming_releases", Map.of("horizon_days", 1));

        assertEquals("2026-11-26", reponse.path("result.structuredContent.jusqua"), reponse.asString());
    }
}
