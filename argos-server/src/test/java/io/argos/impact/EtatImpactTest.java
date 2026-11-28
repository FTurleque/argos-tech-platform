package io.argos.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Règles de l'état de traitement d'un impact (COR-09, COR-10, COR-12 ; décision J5) : transitions permises, invariant
 * INV-C3 et lecture du code d'état d'un filtre.
 */
class EtatImpactTest {

    /** COR-09-S1 et COR-10-S3 : transitions permises depuis chaque état ; l'état courant n'en fait jamais partie. */
    @Test
    void transitionsPermisesDepuisChaqueEtat() {
        assertEquals(Set.of(EtatImpact.CONFIRME, EtatImpact.ECARTE, EtatImpact.RESOLU), EtatImpact.OUVERT.suivants());
        assertEquals(Set.of(EtatImpact.RESOLU, EtatImpact.ECARTE), EtatImpact.CONFIRME.suivants());
        assertEquals(Set.of(EtatImpact.OUVERT), EtatImpact.ECARTE.suivants());
        assertEquals(Set.of(EtatImpact.OUVERT), EtatImpact.RESOLU.suivants());
        for (EtatImpact etat : EtatImpact.values()) {
            assertFalse(etat.suivants().contains(etat), etat.name());
        }
    }

    /** INV-C3 : un impact dont le composant a quitté l'inventaire ne redevient ni ouvert ni confirmé. */
    @Test
    void invC3ImpactSansComposantNeRedevientPasActif() {
        assertEquals(Set.of(), EtatImpact.RESOLU.suivants(false));
        assertEquals(Set.of(), EtatImpact.ECARTE.suivants(false));
        assertEquals(Set.of(EtatImpact.ECARTE, EtatImpact.RESOLU), EtatImpact.OUVERT.suivants(false));
        assertEquals(EtatImpact.RESOLU.suivants(), EtatImpact.RESOLU.suivants(true));
    }

    /** COR-12-S2 : le code d'état d'un filtre est lu sans tenir compte de la casse ni des espaces ; un code inconnu est vide. */
    @Test
    void cor12S2CodeDEtatDuFiltre() {
        assertEquals(Optional.of(EtatImpact.RESOLU), EtatImpact.depuis(" resolu "));
        assertEquals(Optional.of(EtatImpact.ECARTE), EtatImpact.depuis("ECARTE"));
        assertEquals(Optional.empty(), EtatImpact.depuis("INCONNU"));
        assertEquals(Optional.empty(), EtatImpact.depuis("  "));
        assertEquals(Optional.empty(), EtatImpact.depuis(null));
        assertEquals(Set.of(EtatImpact.OUVERT, EtatImpact.CONFIRME), EtatImpact.ACTIFS);
    }
}
