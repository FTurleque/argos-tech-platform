package io.argos.ingestion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * État des sources d'avis pour l'exploitation (EXP-15-S1, EXP-14, DF-COL-3) :
 * <ul>
 * <li>contrôle de disponibilité {@value #NOM} : état et dernière synchronisation réussie de chaque source d'avis
 * (sources communes à l'instance, aucune donnée d'espace), {@code etat=degrade} dès qu'une source est
 * {@code DEGRADED} ; le contrôle reste {@code UP} (design.md D5) ;</li>
 * <li>jauge {@code argos_sources{etat}} : nombre de sources par état, relevé à chaque contrôle et après chaque
 * passage planifié ({@link #actualiser()}).</li>
 * </ul>
 */
@Readiness
@ApplicationScoped
public class SanteSources implements HealthCheck {

    /** Nom du contrôle de disponibilité. */
    public static final String NOM = "sources-avis";

    private static final Logger LOG = Logger.getLogger(SanteSources.class);
    private static final List<String> ETATS = List.of("NOMINAL", "FAILING", "DEGRADED");
    private static final String DEGRADEE = "DEGRADED";

    @Inject
    SyncStore store;

    @Inject
    MeterRegistry registre;

    private final Map<String, AtomicLong> parEtat = Map.of("NOMINAL", new AtomicLong(), "FAILING", new AtomicLong(),
            DEGRADEE, new AtomicLong());

    @PostConstruct
    void enregistrerJauges() {
        for (String etat : ETATS) {
            Gauge.builder("argos.sources", parEtat.get(etat), AtomicLong::get)
                    .description("Sources d'avis par état de collecte").tag("etat", etat).register(registre);
        }
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder reponse = HealthCheckResponse.named(NOM).up();
        List<SyncStore.SourceState> sources;
        try {
            sources = lireEtReleverJauges();
        } catch (IllegalStateException erreur) {
            LOG.warnf(erreur, "contrôle %s : état des sources illisible", NOM);
            return reponse.withData("etat", "illisible").build();
        }
        boolean degradee = false;
        for (SyncStore.SourceState source : sources) {
            reponse.withData(source.sourceId() + ".etat", source.state());
            if (source.lastSuccessAt() != null) {
                reponse.withData(source.sourceId() + ".derniere-synchronisation-reussie", source.lastSuccessAt().toString());
            }
            degradee |= DEGRADEE.equals(source.state());
        }
        return reponse.withData("etat", degradee ? "degrade" : "nominal").build();
    }

    /** Relève le nombre de sources par état dans la jauge {@code argos_sources}. */
    public void actualiser() {
        lireEtReleverJauges();
    }

    private List<SyncStore.SourceState> lireEtReleverJauges() {
        List<SyncStore.SourceState> sources = store.sourceStates();
        for (String etat : ETATS) {
            parEtat.get(etat).set(sources.stream().filter(source -> etat.equals(source.state())).count());
        }
        return sources;
    }
}
