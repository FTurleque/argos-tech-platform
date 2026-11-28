package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Passage planifié de diffusion des alertes hors du conteneur (ALR-10, arc42 §8.5) : la file d'envoi est relevée à
 * l'heure du passage, même quand le passage se termine sur une erreur imprévue.
 */
class EnvoiAlertesTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T10:00:00Z");

    /** Contrôle de santé simulé : garde les instants de relève de la file. */
    private static final class ReleveFile extends SanteEnvoiAlertes {

        private final List<Instant> releves = new ArrayList<>();

        @Override
        EtatFile actualiser(Instant maintenant) {
            releves.add(maintenant);
            return new EtatFile(0, 0);
        }
    }

    /** Passage dont la diffusion échoue sur une erreur imprévue. */
    private static final class PassageEnErreur extends EnvoiAlertes {

        @Override
        public Map<UUID, Issue> traiterEnAttente(Instant maintenant) {
            throw new IllegalArgumentException("erreur imprévue simulée");
        }
    }

    @Test
    void laFileEstReleveeMemeQuandLePassageEchoue() {
        ReleveFile sante = new ReleveFile();
        EnvoiAlertes envoi = new PassageEnErreur();
        envoi.horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        envoi.sante = sante;

        assertThrows(IllegalArgumentException.class, envoi::planifie);

        assertEquals(List.of(MAINTENANT), sante.releves, "file relevée malgré l'échec du passage");
    }
}
