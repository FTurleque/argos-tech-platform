package io.argos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.awaitility.Awaitility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exécuteur borné possédé par un bean (AUD-QUA-11) : issues d'une tâche et arrêt à la destruction du bean. Aucune
 * attente réelle : la tâche lente attend un signal que le test ne donne pas, et le délai accordé est nul.
 */
class ExecuteurBorneTest {

    private static final Duration GENEREUX = Duration.ofSeconds(30);

    /** Délai d'un lot dont la seconde tâche ne se termine jamais : la première, immédiate, s'y termine. */
    private static final Duration DELAI_DU_LOT = Duration.ofSeconds(2);

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
    void uneTacheLanceeSansAttenteSExecuteEtLExecuteurRedevientInactif() throws InterruptedException {
        CountDownLatch executee = new CountDownLatch(1);
        assertTrue(executeur.lancer(executee::countDown));
        executee.await();
        Awaitility.await().atMost(GENEREUX).until(executeur::inactif);
    }

    @Test
    void uneTacheRefuseeParLaFilePleineSExecuteSurLeFilAppelant() {
        assertTrue(executeur.lancer(this::attendreSansFin));
        Awaitility.await().atMost(GENEREUX).until(() -> !executeur.inactif());
        assertTrue(executeur.lancer(this::attendreSansFin), "la file d'une demande accepte une tâche");
        assertFalse(executeur.lancer(() -> { }), "file pleine : tâche refusée");
        List<String> filsExecutants = new ArrayList<>();
        executeur.lancerOuExecuter(() -> filsExecutants.add(Thread.currentThread().getName()));
        assertEquals(List.of(Thread.currentThread().getName()), filsExecutants);
    }

    @Test
    void unLotDeTachesRendUneIssueParTacheDansLOrdre() {
        Callable<String> lente = () -> {
            attendreSansFin();
            return "trop tard";
        };
        List<ExecuteurBorne.Issue<String>> issues = executeur.executerTous(List.of(() -> "première", lente), DELAI_DU_LOT);
        assertEquals(List.of(ExecuteurBorne.Etat.OBTENU, ExecuteurBorne.Etat.DELAI_DEPASSE),
                issues.stream().map(ExecuteurBorne.Issue::etat).toList());
        assertEquals(Optional.of("première"), issues.get(0).valeur());
    }

    /** Une tâche encore en file, annulée au délai sans avoir démarré, ne laisse pas l'exécuteur actif. */
    @Test
    void uneTacheAnnuleeAvantDeDemarrerNeLaissePasLExecuteurActif() {
        assertTrue(executeur.lancer(this::attendreSansFin));
        ExecuteurBorne.Issue<String> issue = executeur.executer(() -> "jamais démarrée", Duration.ZERO);
        assertEquals(ExecuteurBorne.Etat.DELAI_DEPASSE, issue.etat());
        jamais.countDown();
        Awaitility.await().atMost(GENEREUX).until(executeur::inactif);
    }

    private void attendreSansFin() {
        try {
            jamais.await();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void uneAttenteInterrompueAnnuleLaTacheEtGardeLInterruption() {
        Thread.currentThread().interrupt();
        try {
            ExecuteurBorne.Issue<String> issue = executeur.executer(() -> {
                jamais.await();
                return "jamais rendu";
            }, GENEREUX);
            assertEquals(ExecuteurBorne.Etat.INTERROMPUE, issue.etat());
            assertTrue(Thread.currentThread().isInterrupted(), "interruption du fil appelant conservée");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void lExecuteurSArreteALaDestructionDuBeanEtRefuseEnsuite() {
        assertFalse(executeur.arrete());
        executeur.arreter();
        assertTrue(executeur.arrete());
        assertEquals(ExecuteurBorne.Etat.REFUSEE, executeur.executer(() -> "après l'arrêt", GENEREUX).etat());
    }
}
