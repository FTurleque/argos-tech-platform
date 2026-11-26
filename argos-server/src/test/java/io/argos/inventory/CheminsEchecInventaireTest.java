package io.argos.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.scm.QuotaEpuise;
import io.argos.testsupport.DataSourceEnPanne;

/**
 * Chemins d'échec de l'écriture et de l'import planifié des inventaires (INV-07, INV-09-S4, INV-11) : base
 * injoignable, projet en échec qui n'interrompt pas le passage, erreur imprévue retenue puis relancée, relance
 * de recorrélation en échec. Les collaborateurs sont des doubles : aucun conteneur ni aucune base.
 */
class CheminsEchecInventaireTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T03:00:00Z");
    private static final UUID PROJET = UUID.randomUUID();
    private static final UUID DEPOT = UUID.randomUUID();

    // ------------------------------------------------------------------ InventoryStore : base injoignable

    @Test
    void ecrituresDInventaireEnEchecSurBaseInjoignable() {
        InventoryStore store = storeEnPanne();
        InventoryStore.Remplacement remplacement = new InventoryStore.Remplacement(DEPOT, "sbom",
                new ParsedInventory("1.6", null, List.of()), "ref");

        assertEquals("enregistrement de l'inventaire impossible",
                assertThrows(IllegalStateException.class, () -> store.replace(PROJET, remplacement)).getMessage());
        assertEquals("retrait du dépôt impossible",
                assertThrows(IllegalStateException.class, () -> store.detacherDepot(PROJET, DEPOT)).getMessage());
        assertEquals("effacement du marqueur de recorrélation impossible",
                assertThrows(IllegalStateException.class, () -> store.recorrelationTerminee(PROJET, UUID.randomUUID())).getMessage());
        assertEquals("enregistrement de l'échec d'import impossible",
                assertThrows(IllegalStateException.class, () -> store.recordFailure(PROJET, "motif", "ref")).getMessage());
    }

    @Test
    void remplacementSansEmplacementSansEffet() {
        assertDoesNotThrow(() -> storeEnPanne().remplacer(PROJET, List.of()));
    }

    // ------------------------------------------------------------------ ImportPlanifie : passage

    @Test
    void projetEnEchecNInterromptPasLePassage() {
        UUID enPanne = UUID.randomUUID();
        UUID sain = UUID.randomUUID();
        ImportPlanifie planifie = planifie(List.of(), List.of(enPanne, sain), projet -> {
            if (projet.equals(enPanne)) {
                throw new IllegalStateException("base injoignable");
            }
            return List.of(resultat(StatutImport.IMPORTED), resultat(StatutImport.KEPT), resultat(StatutImport.NONE),
                    resultat(StatutImport.REJECTED), resultat(StatutImport.ERROR), resultat(StatutImport.DEFERRED));
        });

        ImportPlanifie.Bilan bilan = planifie.importer(MAINTENANT);

        assertEquals(new ImportPlanifie.Bilan(2, 1, 2, 1, 1, 1, 0, 1, List.of()), bilan);
        assertDoesNotThrow(bilan::relancerErreurImprevue);
    }

    @Test
    void erreurImprevueRetenueEtRelanceeApresLePassage() {
        UUID premier = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UnsupportedOperationException imprevue = new UnsupportedOperationException("imprévue");
        List<UUID> parcourus = new ArrayList<>();
        ImportPlanifie planifie = planifie(List.of(), List.of(premier, second), projet -> {
            parcourus.add(projet);
            if (projet.equals(premier)) {
                throw imprevue;
            }
            return List.of(resultat(StatutImport.IMPORTED));
        });

        ImportPlanifie.Bilan bilan = planifie.importer(MAINTENANT);

        assertEquals(List.of(premier, second), parcourus, "le second projet est importé malgré l'erreur du premier");
        assertEquals(1, bilan.importes());
        assertEquals(1, bilan.projetsEnEchec());
        assertSame(imprevue, assertThrows(UnsupportedOperationException.class, bilan::relancerErreurImprevue));
    }

    @Test
    void relancesDeRecorrelationEnEchecCompteesAPart() {
        UUID relance = UUID.randomUUID();
        UUID enPanne = UUID.randomUUID();
        UUID imprevu = UUID.randomUUID();
        IllegalArgumentException erreurImprevue = new IllegalArgumentException("imprévue");
        ImportPlanifie planifie = planifie(List.of(relance, enPanne, imprevu), List.of(), projet -> List.of());
        planifie.store = new InventoryStore() {
            @Override
            public void relancerRecorrelation(UUID projectId) {
                if (projectId.equals(enPanne)) {
                    throw new IllegalStateException("base injoignable");
                }
                if (projectId.equals(imprevu)) {
                    throw erreurImprevue;
                }
            }
        };

        ImportPlanifie.Bilan bilan = planifie.importer(MAINTENANT);

        assertEquals(1, bilan.recorrelationsRelancees());
        assertEquals(0, bilan.projets());
        assertEquals(List.of(erreurImprevue), bilan.erreursImprevues());
    }

    @Test
    void passagePlanifieRelanceLErreurImprevue() {
        IllegalArgumentException imprevue = new IllegalArgumentException("imprévue");
        ImportPlanifie planifie = planifie(List.of(), List.of(PROJET), projet -> {
            throw imprevue;
        });

        assertSame(imprevue, assertThrows(IllegalArgumentException.class, planifie::planifie));
    }

    @Test
    void quotaRencontreTransmisAuxProjetsSuivants() {
        UUID premier = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<Integer> quotasVus = new ArrayList<>();
        ImportPlanifie planifie = new ImportPlanifie();
        planifie.horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        planifie.queries = requetes(List.of());
        planifie.store = new InventoryStore();
        planifie.projects = repertoire(List.of(premier, second));
        planifie.inventories = new InventoryService() {
            @Override
            public ImportProjet importerProjet(UUID projectId, Map<String, QuotaEpuise> quotas) {
                quotasVus.add(quotas.size());
                quotas.put("GITHUB", new QuotaEpuise("api.github.com", MAINTENANT));
                return new ImportProjet(projectId, List.of(resultat(StatutImport.DEFERRED)));
            }
        };

        ImportPlanifie.Bilan bilan = planifie.importer(MAINTENANT);

        assertEquals(List.of(0, 1), quotasVus, "le quota épuisé par le premier projet suspend la forge pour le second");
        assertEquals(2, bilan.reportes());
        assertTrue(bilan.erreursImprevues().isEmpty());
    }

    /** Import d'un projet simulé : résultats de ses dépôts, ou erreur. */
    @FunctionalInterface
    private interface ImportSimule {
        List<InventoryService.ResultatDepot> importer(UUID projet);
    }

    private static ImportPlanifie planifie(List<UUID> aRecorreler, List<UUID> projets, ImportSimule simulation) {
        ImportPlanifie planifie = new ImportPlanifie();
        planifie.horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        planifie.queries = requetes(aRecorreler);
        planifie.store = new InventoryStore() {
            @Override
            public void relancerRecorrelation(UUID projectId) {
                // Relance acceptée : aucun événement n'est publié hors du conteneur.
            }
        };
        planifie.projects = repertoire(projets);
        planifie.inventories = new InventoryService() {
            @Override
            public ImportProjet importerProjet(UUID projectId, Map<String, QuotaEpuise> quotas) {
                return new ImportProjet(projectId, simulation.importer(projectId));
            }
        };
        return planifie;
    }

    private static InventoryQueries requetes(List<UUID> aRecorreler) {
        return new InventoryQueries() {
            @Override
            public List<UUID> projetsARecorreler() {
                return aRecorreler;
            }
        };
    }

    private static ProjectDirectory repertoire(List<UUID> projets) {
        return new ProjectDirectory() {
            @Override
            public List<UUID> avecDepots() {
                return projets;
            }
        };
    }

    private static InventoryService.ResultatDepot resultat(StatutImport statut) {
        return new InventoryService.ResultatDepot(DEPOT, "GITHUB", "org/depot",
                new InventoryService.ImportResult(statut, "sbom", 0, 0, null));
    }

    private static InventoryStore storeEnPanne() {
        InventoryStore store = new InventoryStore();
        store.dataSource = DataSourceEnPanne.creer();
        store.horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        return store;
    }
}
