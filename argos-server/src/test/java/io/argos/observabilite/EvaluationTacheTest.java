package io.argos.observabilite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** États d'une tâche planifiée publiés par le contrôle de disponibilité (EXP-15-S2, décision Q-2). */
class EvaluationTacheTest {

    private static final Instant DEMARRAGE = Instant.parse("2026-11-24T08:00:00Z");
    private static final Instant PRECEDENTE = Instant.parse("2026-11-24T09:00:00Z");
    private static final Instant SUIVANTE = Instant.parse("2026-11-24T10:00:00Z");
    private static final long FACTEUR = 2;

    @Test
    void sansEcheancePrecedenteLaTacheNEstJamaisEnRetard() {
        EvaluationTache evaluation = EvaluationTache.evaluer(new EvaluationTache.Echeances(Optional.empty(), Optional.of(SUIVANTE)),
                Optional.empty(), DEMARRAGE, FACTEUR, DEMARRAGE.plus(Duration.ofDays(3)));
        assertEquals(EvaluationTache.Etat.JAMAIS_EXECUTEE, evaluation.etat());
        assertFalse(evaluation.retardTolere().isPresent());
        assertFalse(evaluation.etat().degrade());
    }

    @Test
    void uneExecutionRecenteReussieEstALHeure() {
        EvaluationTache evaluation = evaluer(execution(PRECEDENTE, true), PRECEDENTE.plus(Duration.ofMinutes(90)));
        assertEquals(EvaluationTache.Etat.A_L_HEURE, evaluation.etat());
        assertEquals(Optional.of(Duration.ofHours(2)), evaluation.retardTolere());
    }

    @Test
    void uneExecutionRecenteEnEchecEstDegradee() {
        EvaluationTache evaluation = evaluer(execution(PRECEDENTE, false), PRECEDENTE.plus(Duration.ofMinutes(10)));
        assertEquals(EvaluationTache.Etat.EN_ECHEC, evaluation.etat());
        assertTrue(evaluation.etat().degrade());
    }

    @Test
    void auDelaDuFacteurLaTacheEstEnRetard() {
        EvaluationTache evaluation = evaluer(execution(PRECEDENTE, true), PRECEDENTE.plus(Duration.ofMinutes(121)));
        assertEquals(EvaluationTache.Etat.EN_RETARD, evaluation.etat());
        assertEquals("en-retard", evaluation.etat().libelle());
    }

    @Test
    void sansExecutionLaReferenceEstLeDemarrage() {
        assertEquals(EvaluationTache.Etat.JAMAIS_EXECUTEE,
                evaluer(Optional.empty(), DEMARRAGE.plus(Duration.ofMinutes(119))).etat());
        assertEquals(EvaluationTache.Etat.EN_RETARD, evaluer(Optional.empty(), DEMARRAGE.plus(Duration.ofMinutes(121))).etat());
    }

    private static EvaluationTache evaluer(Optional<EtatTaches.Execution> derniere, Instant maintenant) {
        return EvaluationTache.evaluer(new EvaluationTache.Echeances(Optional.of(PRECEDENTE), Optional.of(SUIVANTE)), derniere,
                DEMARRAGE, FACTEUR, maintenant);
    }

    private static Optional<EtatTaches.Execution> execution(Instant fin, boolean reussie) {
        return Optional.of(new EtatTaches.Execution(fin, reussie, Duration.ofSeconds(3)));
    }
}
