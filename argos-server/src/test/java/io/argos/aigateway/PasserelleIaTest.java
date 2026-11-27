package io.argos.aigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Passerelle IA en base (changement {@code encadrer-appels-ia}) : prompts versionnés inscrits au démarrage, compteurs
 * atomiques des quotas (QS-08) et absence de droits du rôle applicatif sur les tables de la passerelle. Le
 * comportement de la passerelle elle-même est vérifié hors du conteneur par {@code PasserelleIaUnitaireTest}.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class PasserelleIaTest {

    private static final int QUOTA_ESPACE = 100;
    private static final int QUOTA_COMPTE = 20;
    private static final int PLAFOND_INSTANCE = 1000;
    private static final int PROMPTS_PUBLIES = 6;

    @Inject
    TestDb baseDeTest;

    @Inject
    DataSource dataSource;

    @Inject
    PasserelleIa passerelle;

    @Inject
    RegistrePrompts registre;

    @Inject
    QuotasIa quotas;

    @ConfigProperty(name = "argos.ia.fuseau")
    ZoneId fuseau;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    private void quota(String portee, int appels) {
        baseDeTest.exec("INSERT INTO ia_quota_jour (jour, portee, appels) VALUES ('" + LocalDate.now(fuseau) + "', '" + portee
                + "', " + appels + ") ON CONFLICT (jour, portee) DO UPDATE SET appels = EXCLUDED.appels");
    }

    private long appelsDe(String portee) {
        return baseDeTest.count("SELECT coalesce(sum(appels), 0) FROM ia_quota_jour WHERE portee = '" + portee + "'");
    }

    // ------------------------------------------------------------------ 2.1 IAG-01

    @Test
    void registreDesPromptsVersionnesInscritAuDemarrage() {
        for (PromptIa prompt : registre.tous()) {
            assertEquals(prompt.empreinte(), baseDeTest.string("SELECT empreinte FROM ia_prompt WHERE tache = '" + prompt.tache()
                    + "' AND version = " + prompt.version()), prompt.tache());
        }
        assertEquals(PROMPTS_PUBLIES, registre.tous().size());
        assertThrows(IllegalArgumentException.class, () -> passerelle.demander(
                new DemandeIa("tache-inconnue", null, List.of(EntreeIa.publique("titre", "x")), List.of())));
    }

    // ------------------------------------------------------------------ 2.3 IAG-03, QS-08

    /**
     * IAG-03-S1 : une réservation refusée par le quota de l'espace ou du compte annule l'incrément de l'instance déjà
     * fait dans la même transaction ; une réservation accordée incrémente l'instance et l'espace ; le plafond atteint
     * n'est jamais dépassé.
     */
    @Test
    void iag03S1ReservationRefuseeAnnuleLIncrementDeLInstance() {
        UUID epuise = UUID.randomUUID();
        UUID libre = UUID.randomUUID();
        UUID compte = UUID.randomUUID();
        quota(epuise.toString(), QUOTA_ESPACE);
        quota(QuotasIa.porteeCompte(compte), QUOTA_COMPTE);

        assertFalse(quotas.disponible(epuise));
        assertTrue(quotas.disponible(libre));
        assertEquals(QuotasIa.Reservation.QUOTA_ESPACE, quotas.reserver(epuise, null));
        assertEquals(QuotasIa.Reservation.QUOTA_COMPTE, quotas.reserver(null, compte));
        assertEquals(0, appelsDe(QuotasIa.INSTANCE), "la réservation de l'instance est annulée avec celle de l'espace ou du compte");

        assertEquals(QuotasIa.Reservation.ACCORDEE, quotas.reserver(libre, null));
        assertEquals(1, appelsDe(QuotasIa.INSTANCE));
        assertEquals(1, appelsDe(libre.toString()));

        quota(QuotasIa.INSTANCE, PLAFOND_INSTANCE);
        assertFalse(quotas.disponible(null));
        assertEquals(QuotasIa.Reservation.PLAFOND_INSTANCE, quotas.reserver(null, null));
        assertEquals(PLAFOND_INSTANCE, appelsDe(QuotasIa.INSTANCE));
    }

    /** Une réservation rendue retire un appel à chaque portée imputée : instance, espace et compte. */
    @Test
    void restitutionRetireUnAppelAChaquePortee() {
        UUID espace = UUID.randomUUID();
        UUID compte = UUID.randomUUID();
        quota(QuotasIa.INSTANCE, 5);
        quota(espace.toString(), 3);
        quota(QuotasIa.porteeCompte(compte), 2);
        quotas.liberer(espace, compte);
        assertEquals(4, baseDeTest.count("SELECT appels FROM ia_quota_jour WHERE portee = 'INSTANCE'"));
        assertEquals(2, baseDeTest.count("SELECT appels FROM ia_quota_jour WHERE portee = '" + espace + "'"));
        assertEquals(1, baseDeTest.count("SELECT appels FROM ia_quota_jour WHERE portee = '" + QuotasIa.porteeCompte(compte) + "'"));
    }

    // ------------------------------------------------------------------ RLS (ADR-0016)

    @Test
    void roleApplicatifSansAucunDroitSurLesTablesDeLaPasserelle() throws SQLException {
        for (String table : List.of("ia_appel", "ia_quota_jour", "ia_prompt")) {
            try (Connection connexion = dataSource.getConnection(); Statement instruction = connexion.createStatement()) {
                connexion.setAutoCommit(false);
                try {
                    instruction.execute("SET LOCAL ROLE argos_app");
                    SQLException refus = assertThrows(SQLException.class,
                            () -> instruction.executeQuery("SELECT count(*) FROM " + table));
                    assertNotNull(refus.getSQLState());
                    assertEquals("42501", refus.getSQLState(), table + " : " + refus.getMessage());
                } finally {
                    connexion.rollback();
                    connexion.setAutoCommit(true);
                }
            }
        }
        Api.moi("alice").statusCode(200);
    }
}
