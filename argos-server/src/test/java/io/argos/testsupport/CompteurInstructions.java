package io.argos.testsupport;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Compte les instructions SQL exécutées par le fil courant (COR-13-S1, ALR-10-S1, RAD-10-S1) : le pilote PostgreSQL
 * journalise chaque exécution envoyée au serveur ({@code FE=> Execute}) au niveau le plus fin ; le compteur écoute ce
 * journal le temps de la mesure, sans l'afficher, puis rétablit sa configuration. Une instruction envoyée par lot
 * ({@code executeBatch}) compte une fois par ligne : seule une instruction ensembliste compte une fois.
 *
 * <p>Le niveau minimal du journal est relevé par la configuration de test
 * ({@code quarkus.log.category."org.postgresql.core.v3.QueryExecutorImpl".min-level}).
 */
public final class CompteurInstructions implements AutoCloseable {

    /** Journal du pilote qui trace les messages du protocole. */
    private static final String JOURNAL = "org.postgresql.core.v3.QueryExecutorImpl";

    /** Début du message tracé pour chaque exécution d'une instruction. */
    private static final String EXECUTION = " FE=> Execute(";

    private final Logger journal;
    private final Level niveauInitial;
    private final boolean parentsInitiaux;
    private final Handler ecoute;
    private final AtomicInteger nombre = new AtomicInteger();

    private CompteurInstructions(Thread fil) {
        journal = Logger.getLogger(JOURNAL);
        niveauInitial = journal.getLevel();
        parentsInitiaux = journal.getUseParentHandlers();
        ecoute = new Handler() {
            @Override
            public void publish(LogRecord trace) {
                if (Thread.currentThread() == fil && trace.getMessage() != null && trace.getMessage().startsWith(EXECUTION)) {
                    nombre.incrementAndGet();
                }
            }

            @Override
            public void flush() {
                // Rien à vider : le compteur ne retient que le nombre de traces.
            }

            @Override
            public void close() {
                // Rien à libérer.
            }
        };
        ecoute.setLevel(Level.ALL);
        journal.setUseParentHandlers(false);
        journal.addHandler(ecoute);
        journal.setLevel(Level.FINEST);
    }

    /** Commence à compter les instructions exécutées par le fil courant. */
    public static CompteurInstructions demarrer() {
        return new CompteurInstructions(Thread.currentThread());
    }

    /** Nombre d'instructions exécutées par le fil courant depuis le démarrage. */
    public int nombre() {
        return nombre.get();
    }

    @Override
    public void close() {
        journal.removeHandler(ecoute);
        journal.setLevel(niveauInitial);
        journal.setUseParentHandlers(parentsInitiaux);
    }
}
