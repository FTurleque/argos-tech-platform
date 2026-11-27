package io.argos.conservation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import io.argos.observabilite.TacheObservee;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;

/**
 * Purge quotidienne des données journalisées (ADR-0041, EXP-17) : applique chaque {@link RegleConservation}
 * déclarée, dans l'ordre de leurs noms, avec la durée réglée par {@code argos.conservation.<nom>}.
 *
 * <p>Un échec d'accès à la base pendant une règle est journalisé au niveau erreur avec sa cause et compté
 * ({@code argos_purges_echecs_total}, étiquette {@code regle}) ; la règle suivante s'exécute. Toute autre erreur
 * remonte au planificateur. Au démarrage, chaque règle doit avoir un nom unique et une durée positive.
 */
@ApplicationScoped
public class PurgesConservation {

    static final String METRIQUE_LIGNES = "argos.purges.lignes";
    static final String METRIQUE_ECHECS = "argos.purges.echecs";
    private static final String ETIQUETTE_REGLE = "regle";

    private static final Logger LOG = Logger.getLogger(PurgesConservation.class);

    /** Issue d'une règle : limite appliquée, lignes supprimées, réussite. */
    public record Bilan(String regle, Instant limite, long supprimees, boolean reussie) {
    }

    @Inject
    @Any
    Instance<RegleConservation> regles;

    @Inject
    Clock horloge;

    @Inject
    Config configuration;

    @Inject
    MeterRegistry registre;

    void verifierAuDemarrage(@Observes StartupEvent demarrage) {
        Set<String> noms = new HashSet<>();
        for (RegleConservation regle : regles) {
            if (!noms.add(regle.nom())) {
                throw new IllegalStateException("règle de conservation déclarée deux fois : " + regle.nom());
            }
            conservation(regle);
        }
    }

    @TacheObservee
    @Scheduled(identity = "argos-purge-conservation", cron = "{argos.conservation.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifiee() {
        executer(horloge.instant());
    }

    /**
     * Applique toutes les règles à l'instant {@code maintenant} et rend le bilan de chacune. L'appelant n'ouvre pas de
     * transaction : chaque lot de suppression est validé à part.
     */
    public List<Bilan> executer(Instant maintenant) {
        List<RegleConservation> declarees = new ArrayList<>();
        regles.forEach(declarees::add);
        return executer(maintenant, declarees);
    }

    /** Applique les règles données, dans l'ordre de leurs noms. */
    List<Bilan> executer(Instant maintenant, List<RegleConservation> declarees) {
        List<RegleConservation> ordonnees = new ArrayList<>(declarees);
        ordonnees.sort(Comparator.comparing(RegleConservation::nom));
        List<Bilan> bilans = new ArrayList<>();
        for (RegleConservation regle : ordonnees) {
            bilans.add(appliquer(regle, maintenant.minus(conservation(regle))));
        }
        return List.copyOf(bilans);
    }

    /**
     * Point d'isolation de la tâche planifiée : un échec d'accès à la base pour une règle ({@code ErreurBase} ou
     * {@link IllegalStateException} d'un store) est journalisé avec sa cause et compté, sans empêcher les règles
     * suivantes.
     */
    private Bilan appliquer(RegleConservation regle, Instant limite) {
        try {
            long supprimees = regle.purger(limite);
            compteur(METRIQUE_LIGNES, "Lignes supprimées par la purge de conservation", regle.nom()).increment(supprimees);
            LOG.infof("conservation %s : %d ligne(s) antérieure(s) au %s supprimée(s)", regle.nom(), supprimees, limite);
            return new Bilan(regle.nom(), limite, supprimees, true);
        } catch (IllegalStateException erreur) {
            compteur(METRIQUE_ECHECS, "Règles de conservation en échec", regle.nom()).increment();
            LOG.errorf(erreur, "conservation %s : purge en échec", regle.nom());
            return new Bilan(regle.nom(), limite, 0, false);
        }
    }

    /** Durée réglée pour la règle ; absente, nulle ou négative : erreur de configuration. */
    Duration conservation(RegleConservation regle) {
        String propriete = RegleConservation.PREFIXE_PROPRIETE + regle.nom();
        Optional<Duration> duree = configuration.getOptionalValue(propriete, Duration.class);
        if (duree.isEmpty() || duree.get().isNegative() || duree.get().isZero()) {
            throw new IllegalStateException("durée de conservation absente ou non positive : " + propriete);
        }
        return duree.get();
    }

    private Counter compteur(String nom, String description, String regle) {
        return Counter.builder(nom).description(description).tag(ETIQUETTE_REGLE, regle).register(registre);
    }
}
