package io.argos.architecture.exemples;

/**
 * Attente par sommeil fixe, contre laquelle {@code TestsSansSommeilTest} vérifie que sa règle la refuse. Jamais
 * exécutée.
 */
public final class SommeilFixe {

    /** Durée de la pause de l'exemple, en millisecondes. */
    private static final long PAUSE_MILLIS = 1;

    private SommeilFixe() {
    }

    public static void attendre() throws InterruptedException {
        Thread.sleep(PAUSE_MILLIS);
    }
}
