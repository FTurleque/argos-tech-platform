package io.argos.observabilite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Dernière exécution de chaque tâche planifiée, tenue en mémoire depuis le démarrage (design.md, « Persistance et
 * intégrité »), et métriques des exécutions (EXP-14-S1) :
 * <ul>
 * <li>{@code argos_taches_executions_seconds} : nombre et durée cumulée des exécutions, étiquettes {@code tache} et
 * {@code resultat} ({@value #SUCCES} ou {@value #ECHEC}) ;</li>
 * <li>{@code argos_taches_derniere_duree_seconds} : durée de la dernière exécution, étiquette {@code tache}.</li>
 * </ul>
 * Les étiquettes ne prennent que des valeurs bornées : identité de la tâche et résultat (INV-2).
 */
@ApplicationScoped
public class EtatTaches {

    /** Résultat d'une exécution terminée sans erreur. */
    public static final String SUCCES = "succes";

    /** Résultat d'une exécution terminée par une erreur. */
    public static final String ECHEC = "echec";

    static final String METRIQUE_EXECUTIONS = "argos.taches.executions";
    static final String METRIQUE_DERNIERE_DUREE = "argos.taches.derniere.duree";
    private static final long NANOS_PAR_SECONDE = TimeUnit.SECONDS.toNanos(1);

    /** Dernière exécution terminée d'une tâche. */
    public record Execution(Instant fin, boolean reussie, Duration duree) {
    }

    @Inject
    Clock horloge;

    @Inject
    MeterRegistry registre;

    private final Map<String, Execution> dernieres = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> dernieresDurees = new ConcurrentHashMap<>();

    /** Enregistre la fin d'une exécution et met à jour ses métriques. */
    public void enregistrer(String tache, boolean reussie, Duration duree) {
        dernieres.put(tache, new Execution(horloge.instant(), reussie, duree));
        Timer.builder(METRIQUE_EXECUTIONS)
                .description("Exécutions des tâches planifiées, par tâche et par résultat")
                .tag("tache", tache).tag("resultat", reussie ? SUCCES : ECHEC)
                .register(registre).record(duree);
        dernieresDurees.computeIfAbsent(tache, this::jaugeDerniereDuree).set(duree.toNanos());
    }

    /** Dernière exécution terminée depuis le démarrage, vide si la tâche ne s'est pas encore exécutée. */
    public Optional<Execution> derniere(String tache) {
        return Optional.ofNullable(dernieres.get(tache));
    }

    private AtomicLong jaugeDerniereDuree(String tache) {
        AtomicLong nanos = new AtomicLong();
        Gauge.builder(METRIQUE_DERNIERE_DUREE, nanos, valeur -> (double) valeur.get() / NANOS_PAR_SECONDE)
                .description("Durée de la dernière exécution d'une tâche planifiée")
                .baseUnit("seconds").tag("tache", tache).register(registre);
        return nanos;
    }
}
