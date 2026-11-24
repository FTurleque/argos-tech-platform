package io.argos.observabilite;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.jboss.logging.MDC;

/**
 * Identifiant de corrélation des journaux (EXP-13, arc42 §8.6) : une valeur par requête HTTP (API et MCP) et par
 * exécution de tâche planifiée, tenue dans le MDC sous la clé {@link #CLE_CORRELATION} ; une exécution planifiée
 * porte aussi le nom de sa tâche sous {@link #CLE_TACHE}. Les journaux JSON de production reprennent ces clés dans
 * leur champ {@code mdc}.
 *
 * <p>Le serveur crée toujours l'identifiant (décision Q-4 du porteur du projet, 2026-11-24) : aucune valeur reçue
 * d'un appelant n'entre dans les journaux.
 */
public final class Correlation {

    /** Clé MDC de l'identifiant de corrélation. */
    public static final String CLE_CORRELATION = "correlation";

    /** Clé MDC du nom de la tâche planifiée en cours. */
    public static final String CLE_TACHE = "tache";

    /** En-tête de réponse qui restitue l'identifiant de la requête à l'appelant. */
    public static final String EN_TETE_REPONSE = "X-Request-Id";

    private Correlation() {
    }

    /** Nouvel identifiant de corrélation, opaque et sans donnée de l'appelant. */
    public static String nouvelIdentifiant() {
        return UUID.randomUUID().toString();
    }

    /** Identifiant de corrélation du fil courant, vide hors requête et hors exécution planifiée. */
    public static String courant() {
        Object valeur = MDC.get(CLE_CORRELATION);
        return valeur == null ? "" : valeur.toString();
    }

    /**
     * Tâche à exécuter sur un autre fil avec les valeurs de corrélation du fil appelant : elles sont posées
     * avant l'exécution et le MDC du fil exécutant est rétabli ensuite.
     */
    public static <T> Callable<T> propager(Callable<T> tache) {
        Map<String, Object> valeurs = Map.copyOf(MDC.getMap());
        return () -> {
            Map<String, Object> precedentes = Map.copyOf(MDC.getMap());
            remplacer(valeurs);
            try {
                return tache.call();
            } finally {
                remplacer(precedentes);
            }
        };
    }

    private static void remplacer(Map<String, Object> valeurs) {
        MDC.clear();
        valeurs.forEach(MDC::put);
    }
}
