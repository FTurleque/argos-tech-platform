package io.argos.inventory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.scm.QuotaEpuise;
import io.quarkus.scheduler.Scheduled;

/**
 * Import planifié des inventaires (INV-07, INV-09-S4 ; décision J3 du porteur du projet, 2026-11-19 ; ADR-0031) :
 * chaque jour ({@code argos.inventory.import.cron}, 03:00 par défaut, fuseau {@code argos.alerting.fuseau}), les
 * projets dont la recorrélation est restée en attente sont d'abord recorrélés, puis chaque dépôt GitHub ou GitLab
 * de chaque projet est interrogé en lecture seule ({@link InventoryService#importerProjet}). Aucun point d'entrée
 * entrant ne déclenche un import (pas de webhook).
 *
 * <p>Un projet en échec n'interrompt pas le passage ; un quota épuisé suspend l'interrogation de sa forge jusqu'au
 * passage suivant, les autres forges continuent. Les écritures prennent le verrou du projet partagé avec l'import
 * manuel (INV-08-S2). La tâche est désactivée dans le profil de test et dans la pile E2E.
 */
@ApplicationScoped
public class ImportPlanifie {

    private static final Logger LOG = Logger.getLogger(ImportPlanifie.class);

    /**
     * Bilan d'un passage : projets parcourus, dépôts importés, conservés (KEPT ou NONE), refusés, en erreur ou
     * reportés, recorrélations relancées, projets en échec inattendu.
     */
    public record Bilan(int projets, int importes, int conserves, int refuses, int erreurs, int reportes,
            int recorrelationsRelancees, int projetsEnEchec) {
    }

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    InventoryQueries queries;

    @Inject
    InventoryStore store;

    @Scheduled(identity = "inventory-import-quotidien", cron = "{argos.inventory.import.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifie() {
        importer(Instant.now());
    }

    /** Passage d'import à l'instant {@code maintenant} (méthode testable, ADR-0031). */
    public Bilan importer(Instant maintenant) {
        int relancees = 0;
        int enEchec = 0;
        for (UUID projet : queries.projetsARecorreler()) {
            try {
                store.relancerRecorrelation(projet);
                relancees++;
            } catch (IllegalStateException erreur) {
                LOG.errorf(erreur, "relance de la recorrélation du projet %s en échec", projet);
            }
        }
        int projetsParcourus = 0;
        int importes = 0;
        int conserves = 0;
        int refuses = 0;
        int erreurs = 0;
        int reportes = 0;
        Map<String, QuotaEpuise> quotas = new HashMap<>();
        for (UUID projet : projects.avecDepots()) {
            projetsParcourus++;
            try {
                for (InventoryService.ResultatDepot depot : inventories.importerProjet(projet, quotas).depots()) {
                    switch (depot.result().status()) {
                        case "IMPORTED" -> importes++;
                        case "REJECTED" -> refuses++;
                        case "ERROR" -> erreurs++;
                        case "DEFERRED" -> reportes++;
                        default -> conserves++;
                    }
                }
            } catch (IllegalStateException erreur) {
                enEchec++;
                LOG.errorf(erreur, "import planifié de l'inventaire du projet %s en échec", projet);
            }
        }
        Bilan bilan = new Bilan(projetsParcourus, importes, conserves, refuses, erreurs, reportes, relancees, enEchec);
        LOG.infof("import planifié des inventaires (%s) : %s", maintenant, bilan);
        return bilan;
    }
}
