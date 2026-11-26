package io.argos.vulnintel;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.commun.Jdbc;

/** Enregistrement d'une donnée d'enrichissement rattachée au signal existant (COL-04-S2), en transaction courte. */
@ApplicationScoped
public class StockEnrichissements {

    @Inject
    Jdbc jdbc;

    /** Crée ou remplace la donnée de la source pour cette référence. */
    @Transactional
    public void enregistrer(UUID signalId, String source, EnrichmentSource.Finding finding) {
        jdbc.modifier("""
                INSERT INTO enrichment(signal_id, source, reference, data, fetched_at) VALUES (?, ?, ?, ?::jsonb, now())
                ON CONFLICT (signal_id, source, reference) DO UPDATE SET data = excluded.data, fetched_at = excluded.fetched_at
                """, Jdbc.parametres(signalId, source, finding.reference(), finding.data().toString()));
    }
}
