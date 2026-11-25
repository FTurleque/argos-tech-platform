package io.argos.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** TE-4 : une valeur de niveau absente ou inconnue lue en base est une erreur explicite, jamais un niveau par défaut. */
class NiveauImpactTest {

    @Test
    void lesTroisNiveauxSontConvertis() {
        assertEquals(MatchRules.Level.EXACT, ImpactQueries.niveau("EXACT"));
        assertEquals(MatchRules.Level.PROBABLE, ImpactQueries.niveau("PROBABLE"));
        assertEquals(MatchRules.Level.A_VERIFIER, ImpactQueries.niveau("A_VERIFIER"));
    }

    @Test
    void uneValeurInconnueLeveUneErreurExplicite() {
        IllegalStateException erreur = assertThrows(IllegalStateException.class, () -> ImpactQueries.niveau("CRITIQUE"));
        assertTrue(erreur.getMessage().contains("CRITIQUE"));
    }

    @Test
    void uneValeurAbsenteLeveUneErreurExplicite() {
        assertThrows(IllegalStateException.class, () -> ImpactQueries.niveau(null));
    }
}
