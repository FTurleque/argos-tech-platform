package io.argos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exécuteur borné possédé par un bean (AUD-QUA-11) : issues d'une tâche et arrêt à la destruction du bean. Aucune
 * attente réelle : la tâche lente attend un signal que le test ne donne pas, et le délai accordé est nul.
 */
class ExecuteurBorneTest {

    private static final Duration GENEREUX = Duration.ofSeconds(30);

    /** Exécuteur d'essai : un fil, file d'une demande. */
    private static final class ExecuteurEssai extends ExecuteurBorne {

        ExecuteurEssai() {
            super("essai-executeur-borne", "argos.essai.executeur");
        }

        @Override
        protected Tailles tailles() {
            return new Tailles(1, 1, 1);
        }
    }

    private final CountDownLatch jamais = new CountDownLatch(1);
    private ExecuteurEssai executeur;

    @BeforeEach
    void demarrer() {
        executeur = new ExecuteurEssai();
        executeur.demarrer();
    }

    @AfterEach
    void arreter() {
        jamais.countDown();
        executeur.arreter();
    }

    @Test
    void unResultatDansLeDelaiEstObtenu() {
        ExecuteurBorne.Issue<String> issue = executeur.executer(() -> "résumé", GENEREUX);
        assertEquals(ExecuteurBorne.Etat.OBTENU, issue.etat());
        assertEquals(Optional.of("résumé"), issue.valeur());
    }

    @Test
    void uneErreurDeLaTacheEstRendueAvecSaCause() {
        IllegalStateException panne = new IllegalStateException("panne simulée");
        ExecuteurBorne.Issue<String> issue = executeur.executer(() -> {
            throw panne;
        }, GENEREUX);
        assertEquals(ExecuteurBorne.Etat.ECHEC, issue.etat());
        assertEquals(Optional.of(panne), issue.cause());
    }

    @Test
    void uneTacheExpireeEstAnnuleeParInterruption() {
        ExecuteurBorne.Issue<String> issue = executeur.executer(() -> {
            try {
                jamais.await();
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
            return "trop tard";
        }, Duration.ZERO);
        assertEquals(ExecuteurBorne.Etat.DELAI_DEPASSE, issue.etat());
        assertTrue(issue.valeur().isEmpty());
    }

    @Test
    void lExecuteurSArreteALaDestructionDuBeanEtRefuseEnsuite() {
        assertFalse(executeur.arrete());
        executeur.arreter();
        assertTrue(executeur.arrete());
        assertEquals(ExecuteurBorne.Etat.REFUSEE, executeur.executer(() -> "après l'arrêt", GENEREUX).etat());
    }
}
