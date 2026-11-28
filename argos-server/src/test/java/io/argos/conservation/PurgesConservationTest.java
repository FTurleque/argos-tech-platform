package io.argos.conservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.commun.ErreurBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Tâche de purge hors du conteneur (EXP-17-S4, ADR-0041) : ordre des règles, isolement d'une règle en échec,
 * métriques, durée de conservation obligatoire et positive.
 */
class PurgesConservationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-26T03:45:00Z");
    private static final int ORDINAL_SOURCE = 100;

    private final List<String> appels = new ArrayList<>();
    private PurgesConservation purges;
    private SimpleMeterRegistry registre;

    /** Règle d'essai : rend un nombre fixe de lignes, ou échoue comme une base injoignable. */
    private class RegleEssai implements RegleConservation {

        private final String nomRegle;
        private final boolean enPanne;

        RegleEssai(String nomRegle, boolean enPanne) {
            this.nomRegle = nomRegle;
            this.enPanne = enPanne;
        }

        @Override
        public String nom() {
            return nomRegle;
        }

        @Override
        public long purger(Instant limite) {
            appels.add(nomRegle + "@" + limite);
            if (enPanne) {
                throw new ErreurBase("Accès à la base impossible : DELETE", new SQLException("refusé"));
            }
            return 3;
        }
    }

    @BeforeEach
    void preparer() {
        purges = new PurgesConservation();
        registre = new SimpleMeterRegistry();
        purges.registre = registre;
        purges.configuration = new SmallRyeConfigBuilder().withSources(new PropertiesConfigSource(Map.of(
                "argos.conservation.alpha", "P10D",
                "argos.conservation.beta", "P1D",
                "argos.conservation.gamma", "PT12H",
                "argos.conservation.nulle", "PT0S"), "essai", ORDINAL_SOURCE)).build();
    }

    @Test
    void uneRegleEnEchecNEmpechePasLesSuivantes() {
        List<PurgesConservation.Bilan> bilans = purges.executer(MAINTENANT,
                List.of(new RegleEssai("gamma", false), new RegleEssai("beta", true), new RegleEssai("alpha", false)));

        assertEquals(List.of("alpha@" + MAINTENANT.minus(Duration.ofDays(10)), "beta@" + MAINTENANT.minus(Duration.ofDays(1)),
                "gamma@" + MAINTENANT.minus(Duration.ofHours(12))), appels, "règles appliquées dans l'ordre de leurs noms");
        assertTrue(bilans.get(0).reussie());
        assertFalse(bilans.get(1).reussie());
        assertEquals(0, bilans.get(1).supprimees());
        assertTrue(bilans.get(2).reussie());
        assertEquals(1.0, registre.get(PurgesConservation.METRIQUE_ECHECS).tag("regle", "beta").counter().count());
        assertEquals(3.0, registre.get(PurgesConservation.METRIQUE_LIGNES).tag("regle", "alpha").counter().count());
    }

    @Test
    void uneErreurImprevueRemonte() {
        RegleConservation imprevue = new RegleEssai("alpha", false) {
            @Override
            public long purger(Instant limite) {
                throw new IllegalArgumentException("défaut de programmation");
            }
        };
        List<RegleConservation> regles = List.of(imprevue);
        assertThrows(IllegalArgumentException.class, () -> purges.executer(MAINTENANT, regles));
    }

    @Test
    void dureeAbsenteOuNulleRefusee() {
        assertThrows(IllegalStateException.class, () -> purges.conservation(new RegleEssai("inconnue", false)));
        assertThrows(IllegalStateException.class, () -> purges.conservation(new RegleEssai("nulle", false)));
        assertEquals(Duration.ofDays(10), purges.conservation(new RegleEssai("alpha", false)));
    }
}
