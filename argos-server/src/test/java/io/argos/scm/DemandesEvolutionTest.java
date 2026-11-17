package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Reconnaissance des adresses de demandes d'évolution GitHub et GitLab (REG-08, Q-3). */
class DemandesEvolutionTest {

    private final DemandesEvolution demandes = new DemandesEvolution(Duration.ofSeconds(1), "https://github.com",
            "https://api.github.com", "https://gitlab.exemple.fr/forge", Optional.empty(), Optional.empty());

    @Test
    void ticketsEtDemandesDeFusionReconnus() {
        DemandesEvolution.Demande g = demandes.reconnaitre("https://github.com/argos/app/issues/42").orElseThrow();
        assertEquals(DemandesEvolution.Forge.GITHUB, g.forge());
        assertEquals("argos/app", g.depot());
        assertEquals(42, g.numero());
        assertEquals("https://github.com/argos/app/issues/42", g.adresse());
        assertEquals("pull", demandes.reconnaitre("https://github.com/argos/app/pull/7/").orElseThrow().nature());

        DemandesEvolution.Demande l = demandes.reconnaitre("https://gitlab.exemple.fr/forge/equipe/sous-groupe/app/-/issues/3")
                .orElseThrow();
        assertEquals(DemandesEvolution.Forge.GITLAB, l.forge());
        assertEquals("equipe/sous-groupe/app", l.depot());
        assertEquals("https://gitlab.exemple.fr/forge/equipe/sous-groupe/app/-/issues/3", l.adresse());
        assertEquals("merge_requests", demandes.reconnaitre(
                "https://gitlab.exemple.fr/forge/equipe/app/-/merge_requests/12").orElseThrow().nature());
    }

    @Test
    void adressesNonReconnues() {
        for (String a : new String[] {null, "", "pas une adresse", "javascript:alert(1)",
                "https://jira.exemple.fr/browse/ARG-1", "http://github.com/argos/app/issues/42",
                "https://github.com.attaquant.fr/argos/app/issues/42", "https://github.com/argos/app/issues/42?x=1",
                "https://user@github.com/argos/app/issues/42", "https://github.com/../app/issues/1",
                "https://github.com/argos/app/wiki/1", "https://gitlab.exemple.fr/equipe/app/-/issues/3",
                "https://gitlab.exemple.fr/forge/../app/-/issues/3", "https://github.com/argos/app/issues/42#commentaire"}) {
            assertTrue(demandes.reconnaitre(a).isEmpty(), String.valueOf(a));
        }
    }
}
