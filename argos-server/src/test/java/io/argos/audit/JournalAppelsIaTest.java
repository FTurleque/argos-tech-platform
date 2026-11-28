package io.argos.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.DataSourceEnPanne;

/**
 * Journal des appels de la passerelle IA hors du conteneur (IAG-01) : textes bornés ou vides, valeurs négatives
 * ramenées à zéro, durée saturée, et base injoignable qui n'interrompt pas l'appelant. Les écritures sont capturées par
 * une source de données simulée.
 */
class JournalAppelsIaTest {

    private static final int LONGUEUR_MAX_TACHE = 100;
    private static final int LONGUEUR_MAX_FOURNISSEUR = 32;
    private static final int LONGUEUR_MAX_MODELE = 200;
    private static final String EMPREINTE = "0".repeat(64);
    private static final int JETONS_ENTREE = 10;
    private static final int JETONS_SORTIE = 20;
    private static final int RANG_TACHE = 1;
    private static final int RANG_FOURNISSEUR = 3;
    private static final int RANG_MODELE = 4;
    private static final int RANG_ESPACE = 5;
    private static final int RANG_JETONS_ENTREE = 7;
    private static final int RANG_JETONS_SORTIE = 8;
    private static final int RANG_COUT = 9;
    private static final int RANG_MASQUEES = 11;
    private static final int RANG_DUREE = 12;

    private static JournalAppelsIa journal(SourceCapturante base) {
        JournalAppelsIa journal = new JournalAppelsIa();
        journal.dataSource = base.source();
        return journal;
    }

    @Test
    void appelIaBorneLesTextesEtRameneLesValeursNegativesAZero() {
        SourceCapturante base = new SourceCapturante();

        journal(base).enregistrer(new JournalAppelsIa.Entree("t".repeat(LONGUEUR_MAX_TACHE + 1), 1,
                "f".repeat(LONGUEUR_MAX_FOURNISSEUR + 1), "m".repeat(LONGUEUR_MAX_MODELE + 1), null, "INDISPONIBLE",
                -1, -2, null, EMPREINTE, -3, -4));

        SourceCapturante.Instruction insertion = base.seule();
        assertEquals(LONGUEUR_MAX_TACHE, ((String) insertion.parametre(RANG_TACHE)).length());
        assertEquals(LONGUEUR_MAX_FOURNISSEUR, ((String) insertion.parametre(RANG_FOURNISSEUR)).length());
        assertEquals(LONGUEUR_MAX_MODELE, ((String) insertion.parametre(RANG_MODELE)).length());
        assertEquals(0, insertion.parametre(RANG_JETONS_ENTREE));
        assertEquals(0, insertion.parametre(RANG_JETONS_SORTIE));
        assertEquals(0, insertion.parametre(RANG_MASQUEES));
        assertEquals(0, insertion.parametre(RANG_DUREE));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) insertion.parametre(RANG_COUT)));
    }

    @Test
    void appelIaSansTacheNiFournisseurEnregistreDesTextesVides() {
        SourceCapturante base = new SourceCapturante();
        UUID espace = UUID.randomUUID();
        BigDecimal cout = new BigDecimal("0.000125");

        journal(base).enregistrer(new JournalAppelsIa.Entree(null, 2, null, null, espace, "PRODUIT", JETONS_ENTREE,
                JETONS_SORTIE, cout, EMPREINTE, 1, Long.MAX_VALUE));

        SourceCapturante.Instruction insertion = base.seule();
        assertEquals("", insertion.parametre(RANG_TACHE));
        assertEquals("", insertion.parametre(RANG_FOURNISSEUR));
        assertEquals("", insertion.parametre(RANG_MODELE));
        assertEquals(espace, insertion.parametre(RANG_ESPACE));
        assertEquals(cout, insertion.parametre(RANG_COUT));
        assertEquals(Integer.MAX_VALUE, insertion.parametre(RANG_DUREE));
    }

    @Test
    void appelIaNonJournaliseSansInterrompreLAppelant() {
        JournalAppelsIa enPanne = new JournalAppelsIa();
        enPanne.dataSource = DataSourceEnPanne.creer();

        assertDoesNotThrow(() -> enPanne.enregistrer(new JournalAppelsIa.Entree("tache", 1, "OLLAMA", "modele", null,
                "PRODUIT", 0, 0, BigDecimal.ONE, EMPREINTE, 0, 0)));
    }
}
