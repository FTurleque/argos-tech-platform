package io.argos.ingestion;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.execution.ExecuteurBorne;
import io.argos.observabilite.EchecsAsynchrones;

/**
 * Exécuteur des synchronisations lancées par l'exploitant (COL-12, ADR-0039) : un fil, deux au plus et une file de
 * huit traitements par défaut ({@code argos.ingestion.lancement.fils}, {@code .fils-max}, {@code .file}). Le traitement
 * est démarré dans la requête, puis poursuivi ici par {@link SourceSynchronizer#poursuivre(String, UUID)}, comme une
 * synchronisation planifiée. Une erreur imprévue qui sort du traitement, déjà enregistrée comme échec permanent de la
 * source (COL-11), est journalisée avec sa cause et comptée ({@code argos_traitements_fond_echecs_total}) : aucun
 * appelant ne l'attend.
 */
@ApplicationScoped
public class LancementSynchronisation extends ExecuteurBorne {

    private static final Logger LOG = Logger.getLogger(LancementSynchronisation.class);

    /** Valeur de l'étiquette {@code traitement} des échecs comptés. */
    static final String TRAITEMENT = "synchronisation";

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    EchecsAsynchrones echecs;

    public LancementSynchronisation() {
        super("argos-synchronisation", "argos.ingestion.lancement");
    }

    /** Poursuit le traitement démarré en tâche de fond ; faux si la file est pleine. */
    public boolean poursuivreEnFond(String sourceId, UUID runId) {
        return lancer(() -> poursuivre(sourceId, runId));
    }

    private void poursuivre(String sourceId, UUID runId) {
        try {
            synchronizer.poursuivre(sourceId, runId);
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "synchronisation de fond de la source %s (run %s) interrompue par une erreur imprévue", sourceId,
                    runId);
            echecs.compterTraitement(TRAITEMENT);
        }
    }
}
