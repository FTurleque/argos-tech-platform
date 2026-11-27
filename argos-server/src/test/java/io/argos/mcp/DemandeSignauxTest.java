package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.alerting.Gravite;
import io.argos.signal.recherche.RechercheVeille;

/**
 * Validation des arguments de {@code search_signals} ({@link OutilsSecurite.DemandeSignaux}) : ordre des
 * contrôles (le premier paramètre invalide dans l'ordre du contrat est signalé), normalisation des valeurs et
 * exigence d'au moins un mot ou un filtre. Sans base ni serveur.
 */
class DemandeSignauxTest {

    private static OutilsSecurite.DemandeSignaux demande(String requete, List<String> technologies, List<String> paquets,
            List<String> sources, List<String> projets, String gravite, Integer horizon, Integer limite) {
        return new OutilsSecurite.DemandeSignaux(requete, technologies, paquets, sources, projets, gravite, horizon, limite);
    }

    private static String refus(OutilsSecurite.DemandeSignaux demande) {
        return assertThrows(ParametreInvalide.class, () -> demande.criteres(Instant.now())).getMessage();
    }

    @Test
    void normalisationDesCriteres() {
        UUID projetA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID projetB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        Instant avant = Instant.now();

        OutilsSecurite.CriteresSignaux criteres = demande("  Oracle  ", Arrays.asList("rust", null, " "),
                List.of("pkg:maven/org.exemple/lib@1.0", "pas-une-purl"), List.of("osv", "Nvd"),
                List.of(projetB.toString(), projetA.toString(), projetB.toString()), "HIGH", 7, null).criteres(Instant.now());

        assertEquals(List.of(projetB, projetA), new ArrayList<>(criteres.projets()), "ordre donné, sans doublon");
        assertEquals(20, criteres.limite(), "20 signaux par défaut");
        RechercheVeille.Criteres recherche = criteres.recherche();
        assertEquals("Oracle", recherche.texte());
        assertEquals(Set.of(RechercheVeille.AVIS_SECURITE), recherche.types());
        assertEquals(Set.of("rust"), recherche.technologies());
        assertEquals(Set.of("pkg:maven/org.exemple/lib", "pas-une-purl"), recherche.paquets());
        assertEquals(Set.of("OSV", "NVD"), recherche.sources());
        assertEquals(Set.of(projetB, projetA), recherche.projets());
        assertEquals(Gravite.auMoins("HIGH"), recherche.gravites());
        assertTrue(!recherche.depuis().isBefore(avant.minus(Duration.ofDays(7)))
                && !recherche.depuis().isAfter(Instant.now().minus(Duration.ofDays(7))));
    }

    @Test
    void motSeulSansFiltre() {
        OutilsSecurite.CriteresSignaux criteres = demande("x", null, null, null, null, " ", null, 50).criteres(Instant.now());

        assertEquals(50, criteres.limite());
        assertTrue(criteres.projets().isEmpty());
        assertNull(criteres.recherche().gravites());
        assertNull(criteres.recherche().depuis());
    }

    @Test
    void premierParametreInvalideDansLOrdreDuContrat() {
        assertEquals("query : 500 caractères au plus.",
                refus(demande("x".repeat(501), null, null, null, List.of("pas-un-uuid"), "EXTREME", 0, 0)));
        assertEquals("project_ids : identifiant UUID attendu.",
                refus(demande(null, null, null, null, List.of("pas-un-uuid"), "EXTREME", 0, 0)));
        assertEquals("severity : LOW, MODERATE, MEDIUM, HIGH ou CRITICAL attendu.",
                refus(demande(null, null, null, null, null, "EXTREME", 0, 0)));
        assertEquals("horizon_days : valeur attendue entre 1 et 3650.", refus(demande(null, null, null, null, null, null, 0, 0)));
        assertEquals("limit : valeur attendue entre 1 et 50.", refus(demande(null, null, null, null, null, null, 30, 0)));
        assertEquals("limit : valeur attendue entre 1 et 50.", refus(demande(null, null, null, null, null, null, null, 51)),
                "la borne de limit précède l'exigence d'un filtre");
    }

    @Test
    void auMoinsUnMotOuUnFiltre() {
        String attendu = "query, technologies, packages, sources, project_ids, severity ou horizon_days :"
                + " au moins un mot ou un filtre est requis.";
        assertEquals(attendu, refus(demande(null, null, null, null, null, null, null, null)));
        assertEquals(attendu, refus(demande("  ", List.of(" "), List.of(), Arrays.asList((String) null), List.of(), "", null, 10)));
        for (OutilsSecurite.DemandeSignaux suffisante : List.of(
                demande(null, List.of("rust"), null, null, null, null, null, null),
                demande(null, null, List.of("pkg:npm/a"), null, null, null, null, null),
                demande(null, null, null, List.of("OSV"), null, null, null, null),
                demande(null, null, null, null, List.of(UUID.randomUUID().toString()), null, null, null),
                demande(null, null, null, null, null, "LOW", null, null),
                demande(null, null, null, null, null, null, 30, null))) {
            suffisante.criteres(Instant.now());
        }
    }
}
