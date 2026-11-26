package io.argos.impact;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;

import io.argos.commun.Jdbc;
import io.argos.execution.PublicationAsynchrone;
import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryReplaced;
import io.argos.inventory.InventoryStore;
import io.argos.signal.SignalChanged;
import io.argos.signal.SignalQueries;

/**
 * Corrélation déterministe signal ↔ projets (DF-COR-2, ADR-0013).
 *
 * <p>Les impacts sont calculés et enregistrés dans une transaction propre, avant toute analyse complémentaire
 * (décision D3) : l'indisponibilité ou le plafond de l'IA ne peut pas les bloquer (COR-05). Le calcul et ses règles
 * (COR-07, COR-09, COR-11, INV-C4) sont ceux de {@link CorrelationDeterministe}, qui traite un lot de signaux en un
 * nombre fixe d'instructions (COR-13) ; la corrélation d'un projet découpe ses signaux en lots de
 * {@code argos.correlation.taille-lot}, une transaction par lot.
 */
@ApplicationScoped
public class CorrelationEngine {

    private static final Logger LOG = Logger.getLogger(CorrelationEngine.class);

    /** Résultat d'une corrélation. {@code analysis} : état de l'analyse complémentaire. */
    public record CorrelationReport(UUID signalId, int created, int updated, int resolved, int reopened,
            List<UUID> impactIds, StatutAnalyse analysis, List<ComplementaryAnalysis.Suggestion> suggestions) {
    }

    @Inject
    Jdbc jdbc;

    @Inject
    SignalQueries signals;

    @Inject
    InventoryQueries inventories;

    @Inject
    InventoryStore inventoryStore;

    @Inject
    CorrelationDeterministe deterministe;

    @Inject
    Instance<ComplementaryAnalysis> analysis;

    @Inject
    Event<ImpactsRecorded> impactsRecorded;

    @Inject
    Event<ImpactsTraites> impactsTraites;

    @Inject
    PublicationAsynchrone publication;

    /** Nombre maximal de signaux corrélés dans une même transaction (COR-13). */
    @ConfigProperty(name = "argos.correlation.taille-lot")
    int tailleLot;

    void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) {
        try {
            correlate(event.signalId(), Optional.empty());
        } catch (IllegalStateException | QuarkusTransactionException erreur) {
            LOG.errorf(erreur, "corrélation du signal %s en échec", event.signalId());
        }
    }

    void onInventoryReplaced(@Observes(during = TransactionPhase.AFTER_SUCCESS) InventoryReplaced event) {
        try {
            correlateProject(event.projectId());
        } catch (IllegalStateException | QuarkusTransactionException erreur) {
            LOG.errorf(erreur, "corrélation du projet %s en échec", event.projectId());
        }
    }

    /**
     * Recorrèle un projet (INV-09, décision D4 du changement automatiser-chaine-veille) : les signaux qui affectent
     * un paquet de son inventaire et ceux qui portent un impact ouvert ou confirmé sur ce projet, afin de résoudre les
     * impacts devenus sans objet, par lots d'au plus {@code argos.correlation.taille-lot} signaux. Quand tous les lots
     * sont traités, le marqueur de recorrélation posé par l'import est effacé, sauf si l'inventaire a changé
     * entre-temps ; un échec le laisse posé, et l'import planifié suivant relance la recorrélation (INV-09-S4).
     * L'analyse complémentaire n'est demandée que pour les signaux dont un impact a été publié.
     */
    public List<CorrelationReport> correlateProject(UUID projectId) {
        Optional<UUID> importCourant = QuarkusTransaction.requiringNew().call(() -> inventories.importCourant(projectId));
        List<UUID> signalIds = QuarkusTransaction.requiringNew().call(() -> signalsToRecorrelate(projectId));
        List<CorrelationReport> reports = new ArrayList<>();
        int taille = Math.max(tailleLot, 1);
        for (int debut = 0; debut < signalIds.size(); debut += taille) {
            List<UUID> lot = signalIds.subList(debut, Math.min(debut + taille, signalIds.size()));
            reports.addAll(correlerLot(lot, Optional.of(projectId), false));
        }
        importCourant.ifPresent(importId -> QuarkusTransaction.requiringNew()
                .run(() -> inventoryStore.recorrelationTerminee(projectId, importId)));
        return reports;
    }

    /**
     * Signaux qui affectent l'inventaire du projet, puis signaux porteurs d'un impact ouvert ou confirmé sur ce projet,
     * sans doublon. Un impact résolu dont le composant revient est retrouvé par le premier ensemble ; un impact écarté
     * n'est jamais modifié par le système (COR-11-S3).
     */
    private List<UUID> signalsToRecorrelate(UUID projectId) throws SQLException {
        Set<UUID> ids = new LinkedHashSet<>(signals.signalsAffecting(inventories.packageKeys(projectId)));
        ids.addAll(signalsWithActiveImpacts(projectId));
        return List.copyOf(ids);
    }

    /** Corrèle un signal avec tous les projets, ou avec un seul projet. */
    public CorrelationReport correlate(UUID signalId, Optional<UUID> projectId) {
        return correlerLot(List.of(signalId), projectId, true).get(0);
    }

    /**
     * Corrèle un lot de signaux dans une transaction, puis publie les impacts de chaque signal et demande l'analyse
     * complémentaire : pour chaque signal si {@code analyseSystematique}, sinon pour ceux dont un impact est publié.
     */
    private List<CorrelationReport> correlerLot(List<UUID> signalIds, Optional<UUID> projectId,
            boolean analyseSystematique) {
        List<CorrelationDeterministe.Resultat> resultats = QuarkusTransaction.requiringNew()
                .call(() -> deterministe.correler(signalIds, projectId));
        List<CorrelationReport> reports = new ArrayList<>();
        for (CorrelationDeterministe.Resultat resultat : resultats) {
            publish(resultat.recorded());
            if (resultat.resolved() > 0) {
                impactsTraites.fire(new ImpactsTraites(resultat.signalId()));
            }
            boolean analyser = analyseSystematique || !resultat.recorded().impacts().isEmpty();
            reports.add(rapport(resultat, analyser));
        }
        return reports;
    }

    /** Rapport d'un signal, avec l'analyse complémentaire si elle est demandée et disponible. */
    private CorrelationReport rapport(CorrelationDeterministe.Resultat resultat, boolean analyser) {
        StatutAnalyse status = StatutAnalyse.NONE;
        List<ComplementaryAnalysis.Suggestion> suggestions = List.of();
        if (analyser && analysis.isResolvable()) {
            ComplementaryAnalysis complementary = analysis.get();
            if (!complementary.budgetAvailable()) {
                status = StatutAnalyse.BUDGET_EXHAUSTED;
            } else {
                try {
                    // Les suggestions sont informatives : elles ne créent aucun impact (COR-04).
                    suggestions = List.copyOf(complementary.analyze(resultat.signalId(), resultat.impactIds()));
                    status = StatutAnalyse.DONE;
                } catch (IllegalStateException erreur) {
                    status = StatutAnalyse.UNAVAILABLE;
                    LOG.infof(erreur, "analyse complémentaire indisponible pour le signal %s : %s", resultat.signalId(),
                            erreur.getMessage());
                }
            }
        }
        return new CorrelationReport(resultat.signalId(), resultat.created(), resultat.updated(), resultat.resolved(),
                resultat.reopened(), resultat.impactIds(), status, suggestions);
    }

    /**
     * Publie les impacts enregistrés, après la validation de leur transaction et avant l'analyse complémentaire, sans
     * attendre l'évaluation des alertes (ADR-0039) : un échec de l'observateur n'affecte ni les impacts ni la corrélation.
     */
    private void publish(ImpactsRecorded recorded) {
        if (recorded.impacts().isEmpty()) {
            return;
        }
        publication.publier(impactsRecorded, recorded);
    }

    private List<UUID> signalsWithActiveImpacts(UUID projectId) {
        return jdbc.lister("SELECT DISTINCT signal_id FROM project_impact WHERE project_id = ? AND state IN ('OUVERT', 'CONFIRME')",
                Jdbc.parametres(projectId), resultat -> resultat.getObject(1, UUID.class));
    }
}
