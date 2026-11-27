package io.argos.inventory;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.conservation.RegleParHorodatage;

/**
 * Règle de conservation des imports d'inventaire refusés ou en erreur ({@code argos.conservation.echecs-import},
 * ADR-0041, EXP-17 ; INV-05, INV-07-S2).
 */
@ApplicationScoped
public class ConservationEchecsImport extends RegleParHorodatage {

    public ConservationEchecsImport() {
        super("echecs-import", "inventory_import_failure", "received_at");
    }
}
