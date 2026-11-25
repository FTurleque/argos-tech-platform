package io.argos.inventory;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.observabilite.ErreursImprevues;
import io.argos.observabilite.TacheObservee;
import io.argos.scm.QuotaEpuise;
import io.quarkus.scheduler.Scheduled;

/**
 * Import planifié des inventaires (INV-07, INV-09-S4 ; décision J3 du porteur du projet, 2026-11-19 ; ADR-0031) :
 * chaque jour ({@code argos.inventory.import.cron}, 03:00 par défaut, fuseau {@code argos.alerting.fuseau}), les
 * projets dont la recorrélation est restée en attente sont d'abord recorrélés, puis chaque dépôt GitHub ou GitLab
 * de chaque projet est interrogé en lecture seule ({@link InventoryService#importerProjet}). Aucun point d'entrée
 * entrant ne déclenche un import (pas de webhook).
 *
 * <p>Un projet en échec n'interrompt pas le passage (INV-11) : une panne d'accès aux données ou une erreur imprévue est
 * journalisée avec sa cause et le projet compté en échec ; le passage planifié relance ensuite la première erreur
 * imprévue (frontière d'erreur, arc42 §8.5) ; un quota épuisé suspend l'interrogation de sa forge jusqu'au
 * passage suivant, les autres forges continuent. Les écritures prennent le verrou du projet partagé avec l'import
 * manuel (INV-08-S2). La tâche est désactivée dans le profil de test et dans la pile E2E.
 */
@ApplicationScoped
public class ImportPlanifie {

    private static final Logger LOG = Logger.getLogger(ImportPlanifie.class);

    /**
     * Bilan d'un passage : projets parcourus, dépôts importés, conservés (KEPT ou NONE), refusés, en erreur ou
     * reportés, recorrélations relancées, projets en échec inattendu, et erreurs imprévues retenues, que
     * {@link #relancerErreurImprevue()} relance.
     */
    public record Bilan(int projets, int importes, int conserves, int refuses, int erreurs, int reportes,
            int recorrelationsRelancees, int projetsEnEchec, List<RuntimeException> erreursImprevues) {

        /** Relance la première erreur imprévue du passage ; sans effet s'il n'y en a pas. */
        public void relancerErreurImprevue() {
            ErreursImprevues.relancer(erreursImprevues);
        }
    }

    @Inject
    Clock horloge;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    InventoryQueries queries;

    @Inject
    InventoryStore store;

    @TacheObservee
    @Scheduled(identity = "inventory-import-quotidien", cron = "{argos.inventory.import.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifie() {
        importer(horloge.instant()).relancerErreurImprevue();
    }

    /** Passage d'import à l'instant {@code maintenant} (méthode testable, ADR-0031). */
    public Bilan importer(Instant maintenant) {
        int enEchec = 0;
        ErreursImprevues imprevues = new ErreursImprevues();
        int relancees = relancerRecorrelations(imprevues);
        int projetsParcourus = 0;
        Map<String, QuotaEpuise> quotas = new HashMap<>();
        Map<Categorie, Integer> comptes = new EnumMap<>(Categorie.class);
        for (UUID projet : projects.avecDepots()) {
            projetsParcourus++;
            try {
                for (InventoryService.ResultatDepot depot : inventories.importerProjet(projet, quotas).depots()) {
                    comptes.merge(categorie(depot.result().status()), 1, Integer::sum);
                }
            } catch (IllegalStateException erreur) {
                enEchec++;
                LOG.errorf(erreur, "import planifié de l'inventaire du projet %s en échec", projet);
            } catch (RuntimeException imprevue) {
                enEchec++;
                imprevues.retenir(imprevue);
                LOG.errorf(imprevue, "import planifié de l'inventaire du projet %s interrompu par une erreur imprévue", projet);
            }
        }
        Bilan bilan = new Bilan(projetsParcourus, comptes.getOrDefault(Categorie.IMPORTES, 0),
                comptes.getOrDefault(Categorie.CONSERVES, 0), comptes.getOrDefault(Categorie.REFUSES, 0),
                comptes.getOrDefault(Categorie.ERREURS, 0), comptes.getOrDefault(Categorie.REPORTES, 0), relancees, enEchec,
                imprevues.retenues());
        LOG.infof("import planifié des inventaires (%s) : %s", maintenant, bilan);
        return bilan;
    }

    /**
     * Relance la recorrélation des projets restés en attente ; renvoie le nombre de relances réussies. Une erreur sur un
     * projet est journalisée avec sa cause ; une erreur imprévue est retenue dans {@code imprevues}.
     */
    private int relancerRecorrelations(ErreursImprevues imprevues) {
        int relancees = 0;
        for (UUID projet : queries.projetsARecorreler()) {
            try {
                store.relancerRecorrelation(projet);
                relancees++;
            } catch (IllegalStateException erreur) {
                LOG.errorf(erreur, "relance de la recorrélation du projet %s en échec", projet);
            } catch (RuntimeException imprevue) {
                imprevues.retenir(imprevue);
                LOG.errorf(imprevue, "relance de la recorrélation du projet %s interrompue par une erreur imprévue", projet);
            }
        }
        return relancees;
    }

    /** Rubrique du bilan où se compte l'issue d'un dépôt. */
    enum Categorie { IMPORTES, CONSERVES, REFUSES, ERREURS, REPORTES }

    /** Rubrique du bilan d'une issue : {@code KEPT} et {@code NONE} sont comptés « conservés ». */
    static Categorie categorie(StatutImport statut) {
        return switch (statut) {
            case IMPORTED -> Categorie.IMPORTES;
            case REJECTED -> Categorie.REFUSES;
            case ERROR -> Categorie.ERREURS;
            case DEFERRED -> Categorie.REPORTES;
            case KEPT, NONE -> Categorie.CONSERVES;
        };
    }
}
