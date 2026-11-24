package io.argos.observabilite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Propagation de la corrélation aux fils des exécuteurs (EXP-13, design.md « Transactions, concurrence »). */
class CorrelationTest {

    @AfterEach
    void nettoyer() {
        MDC.clear();
    }

    @Test
    void leTraitementSoumisPorteLaCorrelationDuFilAppelant() throws InterruptedException, ExecutionException {
        ExecutorService executeur = Executors.newSingleThreadExecutor();
        try {
            executeur.submit(() -> MDC.put(Correlation.CLE_CORRELATION, "valeur-du-fil-executant")).get();
            String identifiant = Correlation.nouvelIdentifiant();
            MDC.put(Correlation.CLE_CORRELATION, identifiant);
            MDC.put(Correlation.CLE_TACHE, "tache-appelante");

            assertEquals(identifiant + "|tache-appelante", executeur.submit(Correlation.propager(
                    () -> Correlation.courant() + "|" + MDC.get(Correlation.CLE_TACHE))).get());
            assertEquals("valeur-du-fil-executant", executeur.submit(Correlation::courant).get(),
                    "MDC du fil exécutant rétabli");
        } finally {
            executeur.shutdownNow();
        }
    }

    @Test
    void chaqueIdentifiantEstNouveau() {
        assertNotEquals(Correlation.nouvelIdentifiant(), Correlation.nouvelIdentifiant());
        assertEquals("", Correlation.courant());
    }
}
