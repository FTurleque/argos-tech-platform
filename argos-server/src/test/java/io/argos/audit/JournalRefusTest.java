package io.argos.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.DataSourceEnPanne;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Journal des refus d'accès hors du conteneur (ACC-06, ACC-15) : ressource bornée, compte ou sujet OIDC, limite par
 * auteur au-delà de laquelle le refus est compté sans être écrit, et base injoignable qui n'interrompt pas l'appelant.
 * Les écritures sont capturées par une source de données simulée.
 */
class JournalRefusTest {

    private static final int LONGUEUR_MAX_RESSOURCE = 500;
    private static final int LIMITE = 3;
    private static final int LIMITE_PAR_DEFAUT = 30;
    private static final int REQUETES = 10;
    private static final int RANG_COMPTE = 1;
    private static final int RANG_SUJET = 2;
    private static final int RANG_RESSOURCE = 3;
    private static final int RANG_MOTIF = 4;

    private final SimpleMeterRegistry registre = new SimpleMeterRegistry();

    private JournalRefus journal(DataSource source, int refusParMinute) {
        JournalRefus journal = new JournalRefus();
        journal.dataSource = source;
        journal.registre = registre;
        journal.refusParMinute = refusParMinute;
        journal.initialiser();
        return journal;
    }

    private double nonJournalises() {
        return registre.get(JournalRefus.METRIQUE_NON_JOURNALISES).counter().count();
    }

    @Test
    void refusSansCompteGardeLeSujetEtBorneLaRessource() {
        SourceCapturante base = new SourceCapturante();

        journal(base.source(), LIMITE_PAR_DEFAUT).enregistrer(null, "sujet-oidc",
                "GET /" + "x".repeat(LONGUEUR_MAX_RESSOURCE), "SANS_COMPTE");

        SourceCapturante.Instruction insertion = base.seule();
        assertNull(insertion.parametre(RANG_COMPTE));
        assertEquals("sujet-oidc", insertion.parametre(RANG_SUJET));
        assertEquals(LONGUEUR_MAX_RESSOURCE, ((String) insertion.parametre(RANG_RESSOURCE)).length());
        assertEquals("SANS_COMPTE", insertion.parametre(RANG_MOTIF));
    }

    @Test
    void refusAvecCompteGardeLaRessourceCourteIntacte() {
        SourceCapturante base = new SourceCapturante();
        UUID compte = UUID.randomUUID();

        journal(base.source(), LIMITE_PAR_DEFAUT).enregistrer(compte, null, "DELETE /api/v1/espaces/1", "ROLE_INSUFFISANT");

        assertEquals(compte, base.seule().parametre(RANG_COMPTE));
        assertEquals("DELETE /api/v1/espaces/1", base.seule().parametre(RANG_RESSOURCE));
    }

    @Test
    void refusNonJournaliseSansInterrompreLAppelant() {
        JournalRefus enPanne = journal(DataSourceEnPanne.creer(), 1);

        assertDoesNotThrow(() -> enPanne.enregistrer(null, "sujet", "GET /x", "MOTIF"));
    }

    /** ACC-15-S1 : un sujet sans compte qui insiste n'écrit que la limite ; les refus suivants sont comptés. */
    @Test
    void sujetSansCompteBorne() {
        SourceCapturante base = new SourceCapturante();
        JournalRefus journal = journal(base.source(), LIMITE);
        String sujet = "sujet-" + UUID.randomUUID();

        for (int requete = 0; requete < REQUETES; requete++) {
            journal.enregistrer(null, sujet, "GET /api/v1/me", "INSCRIPTION_SUR_INVITATION");
        }

        assertEquals(LIMITE, base.instructions().size());
        assertEquals(REQUETES - LIMITE, nonJournalises());
    }

    /** ACC-15-S2 : un autre auteur garde sa propre limite. */
    @Test
    void auteursIndependants() {
        SourceCapturante base = new SourceCapturante();
        JournalRefus journal = journal(base.source(), LIMITE);
        String sujet = "sujet-" + UUID.randomUUID();
        for (int requete = 0; requete < REQUETES; requete++) {
            journal.enregistrer(null, sujet, "GET /api/v1/me", "INSCRIPTION_SUR_INVITATION");
        }
        UUID compte = UUID.randomUUID();

        journal.enregistrer(compte, null, "GET /api/v1/espaces/x", "NON_MEMBRE");

        assertEquals(1, base.instructions().stream().filter(insertion -> compte.equals(insertion.parametre(RANG_COMPTE))).count());
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
