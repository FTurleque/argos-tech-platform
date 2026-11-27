package io.argos.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.DataSourceEnPanne;
import io.argos.testsupport.TestDb;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Journaux d'audit (ACC-06, MCP-06, IAG-01) : bornes des champs, compte ou sujet OIDC, valeurs négatives
 * ramenées à zéro, et une base injoignable qui n'interrompt jamais l'appelant.
 */
@QuarkusTest
class JournauxAuditTest {

    private static final int LONGUEUR_MAX_RESSOURCE = 500;
    private static final int LONGUEUR_MAX_OUTIL = 100;
    private static final int LONGUEUR_MAX_TACHE = 100;
    private static final int LONGUEUR_MAX_FOURNISSEUR = 32;
    private static final int LONGUEUR_MAX_MODELE = 200;
    private static final String EMPREINTE = "0".repeat(64);

    @Inject
    TestDb baseDeTest;

    @Inject
    JournalRefus journalRefus;

    @Inject
    JournalAppelsMcp journalMcp;

    @Inject
    JournalAppelsIa journalIa;

    @BeforeEach
    void viderLesJournaux() {
        baseDeTest.reset();
    }

    @Test
    void refusSansCompteGardeLeSujetEtBorneLaRessource() {
        journalRefus.enregistrer(null, "sujet-oidc", "GET /" + "x".repeat(LONGUEUR_MAX_RESSOURCE), "SANS_COMPTE");

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id IS NULL AND sujet_oidc = 'sujet-oidc'"));
        assertEquals(String.valueOf(LONGUEUR_MAX_RESSOURCE), baseDeTest.string("SELECT length(ressource) FROM refus_acces"));
        assertEquals("SANS_COMPTE", baseDeTest.string("SELECT motif FROM refus_acces"));
    }

    @Test
    void refusAvecCompteGardeLaRessourceCourteIntacte() {
        UUID compte = UUID.randomUUID();
        journalRefus.enregistrer(compte, null, "DELETE /api/v1/espaces/1", "ROLE_INSUFFISANT");

        assertEquals(compte.toString(), baseDeTest.string("SELECT compte_id FROM refus_acces"));
        assertEquals("DELETE /api/v1/espaces/1", baseDeTest.string("SELECT ressource FROM refus_acces"));
    }

    @Test
    void refusNonJournaliseSansInterrompreLAppelant() {
        JournalRefus enPanne = new JournalRefus();
        enPanne.dataSource = DataSourceEnPanne.creer();
        enPanne.registre = new SimpleMeterRegistry();
        enPanne.refusParMinute = 1;
        enPanne.initialiser();

        assertDoesNotThrow(() -> enPanne.enregistrer(null, "sujet", "GET /x", "MOTIF"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM refus_acces"));
    }

    @Test
    void appelMcpAvecCompteOublieLeSujetEtBorneOutilEtDuree() {
        UUID compte = UUID.randomUUID();
        journalMcp.enregistrer(compte, "sujet-ignore", "o".repeat(LONGUEUR_MAX_OUTIL + 1), "{}", -5,
                JournalAppelsMcp.Issue.PARAMETRES_INVALIDES);
        journalMcp.enregistrer(null, "sujet-garde", "search_signals", "{\"q\": 1}", Long.MAX_VALUE, JournalAppelsMcp.Issue.SUCCES);

        assertNull(baseDeTest.string("SELECT sujet_oidc FROM mcp_appel WHERE compte_id IS NOT NULL"));
        assertEquals(String.valueOf(LONGUEUR_MAX_OUTIL),
                baseDeTest.string("SELECT length(outil) FROM mcp_appel WHERE compte_id IS NOT NULL"));
        assertEquals("0", baseDeTest.string("SELECT duree_ms FROM mcp_appel WHERE compte_id IS NOT NULL"));
        assertEquals("sujet-garde", baseDeTest.string("SELECT sujet_oidc FROM mcp_appel WHERE compte_id IS NULL"));
        assertEquals(String.valueOf(Integer.MAX_VALUE), baseDeTest.string("SELECT duree_ms FROM mcp_appel WHERE compte_id IS NULL"));
    }

    @Test
    void appelMcpNonJournaliseSansInterrompreLAppelant() {
        JournalAppelsMcp enPanne = new JournalAppelsMcp();
        enPanne.dataSource = DataSourceEnPanne.creer();

        assertDoesNotThrow(() -> enPanne.enregistrer(null, "sujet", "outil", "{}", 1, JournalAppelsMcp.Issue.ERREUR));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM mcp_appel"));
    }

    @Test
    void appelIaBorneLesTextesEtRameneLesValeursNegativesAZero() {
        journalIa.enregistrer(new JournalAppelsIa.Entree("t".repeat(LONGUEUR_MAX_TACHE + 1), 1,
                "f".repeat(LONGUEUR_MAX_FOURNISSEUR + 1), "m".repeat(LONGUEUR_MAX_MODELE + 1), null, "INDISPONIBLE",
                -1, -2, null, EMPREINTE, -3, -4));

        assertEquals(LONGUEUR_MAX_TACHE + "|" + LONGUEUR_MAX_FOURNISSEUR + "|" + LONGUEUR_MAX_MODELE,
                baseDeTest.string("SELECT length(tache) || '|' || length(fournisseur) || '|' || length(modele) FROM ia_appel"));
        assertEquals("0|0|0|0", baseDeTest.string(
                "SELECT jetons_entree || '|' || jetons_sortie || '|' || donnees_masquees || '|' || duree_ms FROM ia_appel"));
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(baseDeTest.string("SELECT cout_eur FROM ia_appel"))));
    }

    @Test
    void appelIaSansTacheNiFournisseurEnregistreDesTextesVides() {
        UUID espace = UUID.randomUUID();
        journalIa.enregistrer(new JournalAppelsIa.Entree(null, 2, null, null, espace, "PRODUIT", 10, 20,
                new BigDecimal("0.000125"), EMPREINTE, 1, Long.MAX_VALUE));

        assertEquals("||", baseDeTest.string("SELECT tache || '|' || fournisseur || '|' || modele FROM ia_appel"));
        assertEquals(espace.toString(), baseDeTest.string("SELECT espace_id FROM ia_appel"));
        assertEquals(String.valueOf(Integer.MAX_VALUE), baseDeTest.string("SELECT duree_ms FROM ia_appel"));
    }

    @Test
    void appelIaNonJournaliseSansInterrompreLAppelant() {
        JournalAppelsIa enPanne = new JournalAppelsIa();
        enPanne.dataSource = DataSourceEnPanne.creer();

        assertDoesNotThrow(() -> enPanne.enregistrer(new JournalAppelsIa.Entree("tache", 1, "OLLAMA", "modele", null,
                "PRODUIT", 0, 0, BigDecimal.ONE, EMPREINTE, 0, 0)));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM ia_appel"));
    }
}
