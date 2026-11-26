package io.argos.vulnintel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.scheduler.Scheduled;

import io.argos.observabilite.ErreursImprevues;
import io.argos.observabilite.TacheObservee;

/**
 * Traitement planifié de la file d'enrichissement (COL-04, ADR-0039, ADR-0031).
 *
 * <p>À chaque échéance ({@code argos.enrichissement.cadence}), au plus {@code argos.enrichissement.lot} signaux échus
 * sont enrichis, hors de toute transaction : la taille du lot et la cadence bornent le débit vers NVD, qui limite les
 * appels sans clé d'API. Un signal dont toutes les sources ont répondu quitte la file ; sinon il est reporté avec un
 * délai doublé à chaque tentative, de {@code delai-initial} à {@code delai-max}, et quitte la file après
 * {@code tentatives-max} tentatives (le rattrapage le replacera s'il reste sans aucun enrichissement).
 *
 * <p>Le rattrapage ({@code argos.enrichissement.rattrapage.cadence}) replace en file les signaux à CVE sans aucun
 * enrichissement, par exemple ceux enregistrés avant la file ou retirés après leurs tentatives.
 *
 * <p>Frontière d'erreur (arc42 §8.5) : la panne d'accès aux données d'un signal est journalisée et le signal reste en
 * file ; une erreur imprévue est journalisée, les signaux suivants sont traités, puis le passage la relance.
 */
@ApplicationScoped
public class TraitementEnrichissements {

    private static final Logger LOG = Logger.getLogger(TraitementEnrichissements.class);

    /** Exposant au-delà duquel le délai ne double plus (évite le débordement ; le délai maximal borne déjà). */
    private static final int EXPOSANT_MAX = 20;

    /** Bilan d'un passage, avec les erreurs imprévues que {@link #relancerErreurImprevue()} relance. */
    public record Bilan(int enrichis, int reportes, int abandonnes, int enErreur, List<RuntimeException> erreursImprevues) {

        /** Relance la première erreur imprévue du passage ; sans effet s'il n'y en a pas. */
        public void relancerErreurImprevue() {
            ErreursImprevues.relancer(erreursImprevues);
        }
    }

    /** Issue de l'enrichissement d'un signal. */
    private enum Issue { ENRICHI, REPORTE, ABANDONNE, EN_ERREUR }

    @Inject
    Clock horloge;

    @Inject
    Enricher enricher;

    @Inject
    FileEnrichissement file;

    @ConfigProperty(name = "argos.enrichissement.lot", defaultValue = "5")
    int lot;

    @ConfigProperty(name = "argos.enrichissement.tentatives-max", defaultValue = "8")
    int tentativesMax;

    @ConfigProperty(name = "argos.enrichissement.delai-initial", defaultValue = "PT5M")
    Duration delaiInitial;

    @ConfigProperty(name = "argos.enrichissement.delai-max", defaultValue = "PT6H")
    Duration delaiMax;

    @ConfigProperty(name = "argos.enrichissement.rattrapage.lot", defaultValue = "500")
    int lotRattrapage;

    @TacheObservee
    @Scheduled(identity = "vulnintel-enrichissement", every = "{argos.enrichissement.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifie() {
        traiter(horloge.instant()).relancerErreurImprevue();
    }

    @TacheObservee
    @Scheduled(identity = "vulnintel-rattrapage", every = "{argos.enrichissement.rattrapage.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void rattrapagePlanifie() {
        int replaces = enricher.retryMissing(lotRattrapage);
        if (replaces > 0) {
            LOG.infof("%d signal(aux) sans enrichissement replacé(s) en file", replaces);
        }
    }

    /** Enrichit les signaux échus à {@code maintenant} (méthode testable). */
    public Bilan traiter(Instant maintenant) {
        int enrichis = 0;
        int reportes = 0;
        int abandonnes = 0;
        int enErreur = 0;
        ErreursImprevues imprevues = new ErreursImprevues();
        for (FileEnrichissement.Attente attente : file.echus(maintenant, lot)) {
            switch (enrichir(attente, maintenant, imprevues)) {
                case ENRICHI -> enrichis++;
                case REPORTE -> reportes++;
                case ABANDONNE -> abandonnes++;
                case EN_ERREUR -> enErreur++;
            }
        }
        return new Bilan(enrichis, reportes, abandonnes, enErreur, imprevues.retenues());
    }

    private Issue enrichir(FileEnrichissement.Attente attente, Instant maintenant, ErreursImprevues imprevues) {
        try {
            List<String> indisponibles = enricher.enrich(attente.signalId());
            if (indisponibles.isEmpty()) {
                file.terminer(attente.signalId());
                return Issue.ENRICHI;
            }
            return reporter(attente, maintenant, "sources indisponibles : " + String.join(", ", indisponibles));
        } catch (IllegalStateException | QuarkusTransactionException panne) {
            LOG.errorf(panne, "enrichissement du signal %s en échec", attente.signalId());
            return Issue.EN_ERREUR;
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "enrichissement du signal %s interrompu par une erreur imprévue", attente.signalId());
            imprevues.retenir(imprevue);
            return Issue.EN_ERREUR;
        }
    }

    private Issue reporter(FileEnrichissement.Attente attente, Instant maintenant, String motif) {
        int tentatives = attente.tentatives() + 1;
        if (tentatives >= tentativesMax) {
            file.terminer(attente.signalId());
            LOG.warnf("enrichissement du signal %s abandonné après %d tentatives (%s)", attente.signalId(), tentatives, motif);
            return Issue.ABANDONNE;
        }
        file.reporter(attente.signalId(), tentatives, maintenant.plus(delai(tentatives)), motif);
        return Issue.REPORTE;
    }

    /** Délai avant la tentative suivante : initial × 2^(tentatives-1), borné par le délai maximal. */
    Duration delai(int tentatives) {
        long facteur = 1L << Math.min(tentatives - 1, EXPOSANT_MAX);
        Duration calcule = delaiInitial.multipliedBy(facteur);
        return calcule.compareTo(delaiMax) > 0 ? delaiMax : calcule;
    }
}
