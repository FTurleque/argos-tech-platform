package io.argos.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.impact.StrategieDisciplineTerritoire.Decision;
import io.argos.impact.StrategieDisciplineTerritoire.Issue;
import io.argos.signal.opportunite.Opportunite;

/** Stratégie discipline + territoire + date limite (ajouter-profil-artiste, tâche 2.3 ; ART-03, D1). */
class StrategieDisciplineTerritoireTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 16);
    private static final Set<String> DISCIPLINES = Set.of("arts-visuels");
    private static final Set<String> TERRITOIRES = Set.of("occitanie");

    private static Opportunite appel(List<String> disciplines, List<String> territoires, LocalDate limite) {
        return new Opportunite(UUID.randomUUID(), UUID.randomUUID(), "Source", "appels-residences",
                "Résidence d'artiste", "DRAC", "Critères", "https://exemple.test/appel", disciplines, territoires,
                limite, Instant.now());
    }

    /** ART-03-S1 : appel à résidence en arts visuels en Occitanie, date limite dans 20 jours. */
    @Test
    void art03S1AppelCorrespondantRestitueAvecSaDateLimite() {
        Decision decision = StrategieDisciplineTerritoire.evaluer(appel(List.of("arts-visuels"), List.of("occitanie"),
                AUJOURDHUI.plusDays(20)), DISCIPLINES, TERRITOIRES, AUJOURDHUI);
        assertEquals(Issue.OUVERTE, decision.issue());
        assertTrue(decision.issue().restituee());
        assertEquals(List.of("arts-visuels"), decision.disciplines());
        assertEquals(List.of("occitanie"), decision.territoires());
        assertTrue(decision.explication().contains("2026-12-06") && decision.explication().contains("dans 20 jours"),
                decision.explication());
    }

    /** ART-03-S2 : date limite passée, l'appel n'est pas présenté comme une opportunité ouverte. */
    @Test
    void art03S2DateLimiteDepasseeNonRestituee() {
        Decision decision = StrategieDisciplineTerritoire.evaluer(appel(List.of("arts-visuels"), List.of("occitanie"),
                AUJOURDHUI.minusDays(1)), DISCIPLINES, TERRITOIRES, AUJOURDHUI);
        assertEquals(Issue.DATE_LIMITE_DEPASSEE, decision.issue());
        assertFalse(decision.issue().restituee());
    }

    @Test
    void leJourDeLaDateLimiteResteOuvert() {
        assertEquals(Issue.OUVERTE, StrategieDisciplineTerritoire.evaluer(appel(List.of("arts-visuels"),
                List.of("occitanie"), AUJOURDHUI), DISCIPLINES, TERRITOIRES, AUJOURDHUI).issue());
    }

    @Test
    void uneAutreDisciplineOuUnAutreTerritoireNEstPasRestitue() {
        assertEquals(Issue.DISCIPLINE_NON_SUIVIE, StrategieDisciplineTerritoire.evaluer(appel(List.of("musique"),
                List.of("occitanie"), AUJOURDHUI.plusDays(5)), DISCIPLINES, TERRITOIRES, AUJOURDHUI).issue());
        assertEquals(Issue.TERRITOIRE_NON_SUIVI, StrategieDisciplineTerritoire.evaluer(appel(List.of("arts-visuels"),
                List.of("bretagne"), AUJOURDHUI.plusDays(5)), DISCIPLINES, TERRITOIRES, AUJOURDHUI).issue());
        assertEquals(Issue.DISCIPLINE_NON_SUIVIE, StrategieDisciplineTerritoire.evaluer(appel(List.of(),
                List.of("occitanie"), AUJOURDHUI.plusDays(5)), DISCIPLINES, TERRITOIRES, AUJOURDHUI).issue());
    }

    @Test
    void unAppelNationalVautPourTousLesTerritoires() {
        Decision decision = StrategieDisciplineTerritoire.evaluer(appel(List.of("arts-visuels", "musique"), List.of("france"),
                AUJOURDHUI.plusDays(5)), DISCIPLINES, TERRITOIRES, AUJOURDHUI);
        assertEquals(Issue.OUVERTE, decision.issue());
        assertEquals(List.of("france"), decision.territoires());
    }

    @Test
    void sansDateLimiteLAppelEstRestitueDateAConfirmer() {
        Decision decision = StrategieDisciplineTerritoire.evaluer(appel(List.of("arts-visuels"), List.of("occitanie"), null),
                DISCIPLINES, TERRITOIRES, AUJOURDHUI);
        assertEquals(Issue.DATE_A_CONFIRMER, decision.issue());
        assertTrue(decision.issue().restituee());
    }

    /** Déterminisme (ADR-0013) : mêmes entrées, même décision. */
    @Test
    void laDecisionEstDeterministe() {
        Opportunite opportunite = appel(List.of("arts-visuels"), List.of("occitanie"), AUJOURDHUI.plusDays(3));
        assertEquals(StrategieDisciplineTerritoire.evaluer(opportunite, DISCIPLINES, TERRITOIRES, AUJOURDHUI),
                StrategieDisciplineTerritoire.evaluer(opportunite, DISCIPLINES, TERRITOIRES, AUJOURDHUI));
    }

    /** ART-06 : le gabarit cite les critères, renvoie au texte officiel et ne se prononce pas sur l'éligibilité. */
    @Test
    void art06LeGabaritDeResumeNeSePrononcePasSurLEligibilite() {
        Opportunite opportunite = appel(List.of("arts-visuels"), List.of("occitanie"), AUJOURDHUI.plusDays(3));
        ResumeOpportunite.Resume resume = ResumeOpportunite.de(opportunite);
        assertEquals("Critères", resume.criteres());
        assertEquals("https://exemple.test/appel", resume.texteOfficiel());
        assertEquals(ResumeOpportunite.MENTION, resume.mention());
        String gabarit = resume.titre() + resume.organisme() + resume.dateLimite() + resume.mention();
        assertFalse(gabarit.toLowerCase().contains("vous êtes éligible"));
        assertFalse(gabarit.toLowerCase().contains("vous n'êtes pas éligible"));
        String longs = "x".repeat(ResumeOpportunite.CRITERES_MAX + 10);
        ResumeOpportunite.Resume tronque = ResumeOpportunite.de(new Opportunite(opportunite.id(), opportunite.sourceId(), "S", "r", "T",
                null, longs, null, List.of(), List.of(), null, Instant.now()));
        assertTrue(tronque.criteresTronques());
        assertEquals("à confirmer", tronque.dateLimite());
        assertEquals("S", tronque.organisme());
    }
}
