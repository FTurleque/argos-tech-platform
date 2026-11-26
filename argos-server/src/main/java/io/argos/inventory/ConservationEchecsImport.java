package io.argos.inventory;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Conversions;
import io.argos.conservation.RegleConservation;
import io.argos.conservation.SuppressionParLots;

/**
 * Règle de conservation des imports d'inventaire refusés ou en erreur ({@code argos.conservation.echecs-import},
 * ADR-0041, EXP-17 ; INV-05, INV-07-S2).
 */
@ApplicationScoped
public class ConservationEchecsImport implements RegleConservation {

    @Inject
    SuppressionParLots suppression;

    @Override
    public String nom() {
        return "echecs-import";
    }

    @Override
    public long purger(Instant limite) {
        return suppression.supprimer("DELETE FROM inventory_import_failure WHERE id IN"
                + " (SELECT id FROM inventory_import_failure WHERE received_at < ? LIMIT ?)", Conversions.utc(limite));
    }
}
