package io.argos.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.TestDb;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/** Journal des refus borné par auteur (ACC-15) : au-delà de la limite, le refus n'est pas écrit mais compté. */
@QuarkusTest
@TestProfile(JournalRefusBorneTest.LimiteBasse.class)
class JournalRefusBorneTest {

    private static final int LIMITE = 3;
    private static final int REQUETES = 10;

    /** Limite de trois refus journalisés par minute et par auteur. */
    public static class LimiteBasse implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.audit.refus.par-minute", String.valueOf(LIMITE));
        }
    }

    @Inject
    TestDb baseDeTest;

    @Inject
    JournalRefus journal;

    @Inject
    MeterRegistry registre;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
    }

    /** Valeur du compteur ; zéro tant que le journal, initialisé à sa première utilisation, ne l'a pas enregistré. */
    private double nonJournalises() {
        Counter compteur = registre.find(JournalRefus.METRIQUE_NON_JOURNALISES).counter();
        return compteur == null ? 0 : compteur.count();
    }

    /** ACC-15-S1 : un sujet sans compte qui insiste n'écrit que la limite. */
    @Test
    void sujetSansCompteBorne() {
        String sujet = "sujet-" + UUID.randomUUID();
        double avant = nonJournalises();
        for (int requete = 0; requete < REQUETES; requete++) {
            journal.enregistrer(null, sujet, "GET /api/v1/me", "INSCRIPTION_SUR_INVITATION");
        }
        assertEquals(LIMITE, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE sujet_oidc = ?", sujet));
        assertEquals(REQUETES - LIMITE, nonJournalises() - avant);
    }

    /** ACC-15-S2 : un autre auteur garde sa propre limite. */
    @Test
    void auteursIndependants() {
        String sujet = "sujet-" + UUID.randomUUID();
        for (int requete = 0; requete < REQUETES; requete++) {
            journal.enregistrer(null, sujet, "GET /api/v1/me", "INSCRIPTION_SUR_INVITATION");
        }
        UUID compte = UUID.randomUUID();
        journal.enregistrer(compte, null, "GET /api/v1/espaces/x", "NON_MEMBRE");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = ?", compte));
    }

    @Test
    void cleDAuteur() {
        UUID compte = UUID.randomUUID();
        assertEquals(compte, JournalRefus.auteur(compte, "sujet"));
        assertEquals(JournalRefus.auteur(null, "sujet"), JournalRefus.auteur(null, "sujet"));
        assertNotEquals(JournalRefus.auteur(null, "sujet"), JournalRefus.auteur(null, "autre"));
        assertEquals(JournalRefus.auteur(null, null), JournalRefus.auteur(null, " "));
    }
}
