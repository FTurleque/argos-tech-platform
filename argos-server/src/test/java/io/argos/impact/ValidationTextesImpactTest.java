package io.argos.impact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * COR-10-S4 : motif et commentaire d'une transition d'impact, contrôlés avant toute lecture en base
 * ({@link TraitementImpacts#validerTextes}).
 */
class ValidationTextesImpactTest {

    private static final String TEXTE_MAXIMAL = "m".repeat(TraitementImpacts.LONGUEUR_MAX);
    private static final String TEXTE_TROP_LONG = TEXTE_MAXIMAL + "m";

    @Test
    void ecarterSansMotifEstRefuse() {
        IllegalArgumentException refus = assertThrows(IllegalArgumentException.class,
                () -> TraitementImpacts.validerTextes(EtatImpact.ECARTE, null, "commentaire"));
        assertEquals("motif obligatoire pour écarter un impact", refus.getMessage());
    }

    @Test
    void lesAutresTransitionsSePassentDeMotif() {
        assertDoesNotThrow(() -> TraitementImpacts.validerTextes(EtatImpact.CONFIRME, null, null));
    }

    @Test
    void motifOuCommentaireAuDelaDeLaLongueurMaximaleRefuse() {
        assertThrows(IllegalArgumentException.class,
                () -> TraitementImpacts.validerTextes(EtatImpact.ECARTE, TEXTE_TROP_LONG, null));
        assertThrows(IllegalArgumentException.class,
                () -> TraitementImpacts.validerTextes(EtatImpact.CONFIRME, null, TEXTE_TROP_LONG));
    }

    @Test
    void textesALaLongueurMaximaleAdmis() {
        assertDoesNotThrow(() -> TraitementImpacts.validerTextes(EtatImpact.ECARTE, TEXTE_MAXIMAL, TEXTE_MAXIMAL));
    }
}
