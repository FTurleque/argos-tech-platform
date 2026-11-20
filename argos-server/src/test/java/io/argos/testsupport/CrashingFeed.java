package io.argos.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.ingestion.AdvisoryFeed;
import io.argos.ingestion.FeedEntry;
import io.argos.ingestion.NormalizedAdvisory;
import io.argos.ingestion.RawAdvisory;
import io.argos.ingestion.osv.OsvNormalizer;

/**
 * Source de test {@code CRASHTEST} qui simule un arrêt brutal du backend : après un nombre donné
 * d'avis lus, elle lève une {@link Error} qui interrompt la synchronisation sans la clore,
 * comme un processus tué (COL-05-S2, QS-07).
 */
@ApplicationScoped
public class CrashingFeed implements AdvisoryFeed {

    public static final String SOURCE_ID = "CRASHTEST";

    /** Arrêt simulé. */
    public static class SimulatedCrash extends Error {
        public SimulatedCrash() {
            super("arrêt brutal simulé");
        }
    }

    private final List<FeedEntry> entries = Collections.synchronizedList(new ArrayList<>());
    private final List<String> contents = Collections.synchronizedList(new ArrayList<>());
    private final List<String> fetched = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger crashAfter = new AtomicInteger(-1);
    private final java.util.concurrent.atomic.AtomicReference<RuntimeException> failure =
            new java.util.concurrent.atomic.AtomicReference<>();

    public void configure(List<FeedEntry> feedEntries, List<String> rawContents, int crashAfterFetches) {
        entries.clear();
        contents.clear();
        fetched.clear();
        entries.addAll(feedEntries);
        contents.addAll(rawContents);
        crashAfter.set(crashAfterFetches);
        failure.set(null);
    }

    /** La prochaine lecture d'avis lève cette exception inattendue (bogue simulé), une seule fois. */
    public void failOnceWith(RuntimeException unexpected) {
        failure.set(unexpected);
    }

    public List<String> fetched() {
        synchronized (fetched) {
            return List.copyOf(fetched);
        }
    }

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public List<FeedEntry> changesSince(String position) {
        synchronized (entries) {
            return entries.stream().filter(entry -> entry.isAfter(position)).sorted(FeedEntry.ORDER).toList();
        }
    }

    @Override
    public RawAdvisory fetch(FeedEntry entry) {
        RuntimeException unexpected = failure.getAndSet(null);
        if (unexpected != null) {
            throw unexpected;
        }
        if (crashAfter.get() >= 0 && fetched.size() >= crashAfter.get()) {
            crashAfter.set(-1);
            throw new SimulatedCrash();
        }
        fetched.add(entry.externalId());
        return new RawAdvisory(entry.externalId(), contents.get(entries.indexOf(entry)));
    }

    @Override
    public NormalizedAdvisory normalize(RawAdvisory raw) {
        return OsvNormalizer.normalize(raw);
    }
}
