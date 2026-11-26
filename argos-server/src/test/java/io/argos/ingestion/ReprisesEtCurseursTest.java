package io.argos.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Cas limites de la reprise des synchronisations (COL-05) : réglages refusés, délai imposé par la source
 * négatif ou au-delà du maximum, attente exponentielle bornée, délai initial nul ; position de curseur
 * absente, sans identifiant ou à égalité de date.
 */
class ReprisesEtCurseursTest {

    private static final Duration INITIAL = Duration.ofSeconds(2);
    private static final Duration MAXIMUM = Duration.ofMinutes(5);
    private static final int TENTATIVES = 5;
    private static final int SEUIL_DEGRADE = 3;
    private static final int TENTATIVE_LOINTAINE = 40;
    private static final Instant DATE = Instant.parse("2026-11-20T10:00:00Z");

    private final RetryPolicy politique = new RetryPolicy(TENTATIVES, INITIAL, MAXIMUM, SEUIL_DEGRADE);

    @Test
    void reglagesInferieursAUnRefuses() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(0, INITIAL, MAXIMUM, SEUIL_DEGRADE));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(TENTATIVES, INITIAL, MAXIMUM, 0));
        assertEquals(TENTATIVES, politique.maxAttempts());
        assertEquals(SEUIL_DEGRADE, politique.degradedThreshold());
    }

    @Test
    void delaiImposeParLaSourceRameneEntreZeroEtLeMaximum() {
        assertEquals(Duration.ZERO, politique.delayAfter(1, Optional.of(Duration.ofSeconds(-5))));
        assertEquals(Duration.ofSeconds(30), politique.delayAfter(1, Optional.of(Duration.ofSeconds(30))));
        assertEquals(MAXIMUM, politique.delayAfter(1, Optional.of(Duration.ofHours(2))));
    }

    @Test
    void attenteExponentielleAvecGigueBorneeParLeMaximum() {
        Duration premiere = politique.delayAfter(1, Optional.empty());
        assertTrue(premiere.compareTo(INITIAL) >= 0 && premiere.compareTo(INITIAL.plus(INITIAL.dividedBy(5))) <= 0,
                String.valueOf(premiere));
        Duration troisieme = politique.delayAfter(3, Optional.empty());
        assertTrue(troisieme.compareTo(INITIAL.multipliedBy(4)) >= 0, String.valueOf(troisieme));
        assertEquals(MAXIMUM, politique.delayAfter(TENTATIVE_LOINTAINE, Optional.empty()));
    }

    @Test
    void delaiInitialNulSansGigue() {
        RetryPolicy immediate = new RetryPolicy(TENTATIVES, Duration.ZERO, MAXIMUM, SEUIL_DEGRADE);
        assertEquals(Duration.ZERO, immediate.delayAfter(2, Optional.empty()));
    }

    @Test
    void sansCurseurToutAvisEstPosterieur() {
        FeedEntry avis = new FeedEntry("OSV-1", DATE);
        assertTrue(avis.isAfter(null));
        assertTrue(avis.isAfter("  "));
    }

    @Test
    void curseurSansIdentifiantOuAEgaliteDeDateDepartageParLIdentifiant() {
        FeedEntry avis = new FeedEntry("OSV-2", DATE);
        assertTrue(avis.isAfter(DATE.toString()), "même date, curseur sans identifiant");
        assertTrue(avis.isAfter(DATE + "|OSV-1"));
        assertFalse(avis.isAfter(DATE + "|OSV-3"));
        assertFalse(avis.isAfter(avis.position()), "position atteinte : avis déjà enregistré");
        assertFalse(avis.isAfter(DATE.plusSeconds(1).toString()));
        assertTrue(avis.isAfter(DATE.minusSeconds(1) + "|OSV-9"));
    }

    @Test
    void ordreParDatePuisIdentifiant() {
        FeedEntry ancien = new FeedEntry("OSV-9", DATE.minusSeconds(1));
        FeedEntry premier = new FeedEntry("OSV-1", DATE);
        FeedEntry second = new FeedEntry("OSV-2", DATE);
        assertEquals(List.of(ancien, premier, second), List.of(second, premier, ancien).stream().sorted(FeedEntry.ORDER).toList());
    }
}
