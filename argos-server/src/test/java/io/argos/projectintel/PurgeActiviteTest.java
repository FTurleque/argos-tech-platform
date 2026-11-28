package io.argos.projectintel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Date limite de la purge de l'historique d'activité ({@link PurgeActivite}, Q-2) : la durée de conservation est
 * retranchée du jour donné, au moins un mois, et l'instant limite est minuit de ce jour dans le fuseau d'alerte. Une
 * doublure du stockage capture les bornes transmises ; la suppression elle-même est vérifiée en base par
 * {@code ActiviteProjetsTest}.
 */
class PurgeActiviteTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 16);
    private static final int TREIZE_MOIS = 13;
    private static final int LIGNES_SUPPRIMEES = 2;

    @Test
    void conservationDeTreizeMoisJusquAMinuitDuJourLimiteAParis() {
        StockageCapture stockage = new StockageCapture();
        PurgeActivite purge = purge(stockage, TREIZE_MOIS);

        assertEquals(LIGNES_SUPPRIMEES, purge.purger(AUJOURDHUI));

        assertEquals(LocalDate.of(2025, 10, 16), stockage.limite, "le 16 octobre 2025 est à 13 mois exactement");
        assertEquals(Instant.parse("2025-10-15T22:00:00Z"), stockage.limiteInstant, "minuit à Paris, heure d'été");
    }

    @Test
    void conservationNulleOuNegativeRameneeAUnMois() {
        StockageCapture stockage = new StockageCapture();

        purge(stockage, 0).purger(AUJOURDHUI);

        assertEquals(LocalDate.of(2026, 10, 16), stockage.limite);
        assertEquals(Instant.parse("2026-10-15T22:00:00Z"), stockage.limiteInstant);
    }

    private static PurgeActivite purge(ActiviteStore stockage, int conservationMois) {
        PurgeActivite purge = new PurgeActivite();
        purge.store = stockage;
        purge.fuseau = "Europe/Paris";
        purge.conservationMois = conservationMois;
        return purge;
    }

    /** Stockage factice qui retient les bornes de la dernière purge demandée. */
    private static final class StockageCapture extends ActiviteStore {

        private LocalDate limite;
        private Instant limiteInstant;

        @Override
        int purger(LocalDate limiteJour, Instant limiteEnInstant) {
            this.limite = limiteJour;
            this.limiteInstant = limiteEnInstant;
            return LIGNES_SUPPRIMEES;
        }
    }
}
