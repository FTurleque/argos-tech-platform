package io.argos.observabilite;

import java.lang.reflect.Method;
import java.time.Duration;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import io.quarkus.scheduler.Scheduled;

/**
 * Observe chaque exécution d'une tâche planifiée (design.md DC-1) : identifiant de corrélation propre à
 * l'exécution et nom de la tâche dans le MDC, chronométrage, résultat transmis à {@link EtatTaches}. L'intercepteur
 * ne retient aucune erreur : une erreur d'exécution est journalisée avec sa pile et sous la corrélation de
 * l'exécution, puis relancée au planificateur.
 */
@TacheObservee
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class IntercepteurTaches {

    private static final Logger LOG = Logger.getLogger(IntercepteurTaches.class);

    @Inject
    EtatTaches etat;

    @AroundInvoke
    Object observer(InvocationContext invocation) throws Exception {
        String tache = nomDeTache(invocation.getMethod());
        Object correlationPrecedente = MDC.get(Correlation.CLE_CORRELATION);
        Object tachePrecedente = MDC.get(Correlation.CLE_TACHE);
        MDC.put(Correlation.CLE_CORRELATION, Correlation.nouvelIdentifiant());
        MDC.put(Correlation.CLE_TACHE, tache);
        long debut = System.nanoTime();
        boolean reussie = false;
        try {
            Object retour = invocation.proceed();
            reussie = true;
            return retour;
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "exécution de la tâche planifiée %s en échec", tache);
            throw erreur;
        } finally {
            etat.enregistrer(tache, reussie, Duration.ofNanos(System.nanoTime() - debut));
            retablir(Correlation.CLE_CORRELATION, correlationPrecedente);
            retablir(Correlation.CLE_TACHE, tachePrecedente);
        }
    }

    /** Nom de la tâche : identité de {@code @Scheduled}, sinon classe et méthode. */
    static String nomDeTache(Method methode) {
        Scheduled planification = methode.getAnnotation(Scheduled.class);
        if (planification != null && !planification.identity().isBlank()) {
            return planification.identity();
        }
        return methode.getDeclaringClass().getSimpleName() + "." + methode.getName();
    }

    private static void retablir(String cle, Object valeur) {
        if (valeur == null) {
            MDC.remove(cle);
        } else {
            MDC.put(cle, valeur);
        }
    }
}
