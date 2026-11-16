package io.argos.signal.web;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.ingestion.web.LecteurFlux;
import io.argos.ingestion.web.LecteurPage;
import io.argos.ingestion.web.RecuperateurWeb;
import io.argos.ingestion.web.RecuperationRefusee;
import io.quarkus.scheduler.Scheduled;

/**
 * Collecte des sources de veille web (WEB-01-S1, WEB-02-S1, WEB-03-S2) : récupération bornée et
 * vérifiée à chaque redirection, lecture du flux ou de la page, enregistrement dédupliqué. Le
 * planificateur collecte toutes les {@code argos.veille-web.cadence} les sources échues ; chaque
 * source est collectée toutes les {@code argos.veille-web.frequence} (une heure par défaut, Q-2),
 * avec des tentatives espacées après un échec.
 *
 * <p>La récupération se fait hors transaction ; seules les écritures sont transactionnelles.
 */
@ApplicationScoped
public class CollecteWeb {

    private static final Logger LOG = Logger.getLogger(CollecteWeb.class);

    /** Résultat de la collecte d'une source. */
    public record Rapport(UUID sourceId, String etat, int nouvelles, String erreur) {
    }

    @Inject
    RecuperateurWeb recuperateur;

    @Inject
    LecteurFlux lecteurFlux;

    @Inject
    LecteurPage lecteurPage;

    @Inject
    StockActualites stock;

    @ConfigProperty(name = "argos.veille-web.frequence", defaultValue = "PT1H")
    Duration frequence;

    @ConfigProperty(name = "argos.veille-web.planification-active", defaultValue = "true")
    boolean planificationActive;

    @ConfigProperty(name = "argos.veille-web.sources-par-passage", defaultValue = "200")
    int sourcesParPassage;

    @ConfigProperty(name = "argos.veille-web.elements-max", defaultValue = "100")
    int elementsMax;

    @Scheduled(identity = "collecte-veille-web", every = "{argos.veille-web.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifier() {
        if (planificationActive) {
            collecterSourcesEchues();
        }
    }

    /** Collecte les sources dont l'échéance est passée. */
    public List<Rapport> collecterSourcesEchues() {
        return stock.echues(sourcesParPassage).stream().map(this::collecter).toList();
    }

    /** Collecte une source ; un échec la fait passer {@code EN_ERREUR} avec son motif. */
    public Rapport collecter(UUID sourceId) {
        StockActualites.SourceACollecter source = stock.source(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("source web inconnue : " + sourceId));
        try {
            RecuperateurWeb.Contenu contenu = recuperateur.recuperer(URI.create(source.adresse()));
            int nouvelles = integrer(source, contenu);
            stock.succes(sourceId, frequence);
            return new Rapport(sourceId, "ACTIVE", nouvelles, null);
        } catch (RecuperationRefusee e) {
            String motif = e.motif().name() + " : " + e.getMessage();
            LOG.infof("collecte de la source web %s en échec : %s", sourceId, motif);
            stock.echec(sourceId, motif, frequence);
            return new Rapport(sourceId, "EN_ERREUR", 0, motif);
        } catch (RuntimeException e) {
            String motif = "ERREUR_INTERNE : " + e.getClass().getSimpleName();
            LOG.errorf(e, "collecte de la source web %s interrompue", sourceId);
            stock.echec(sourceId, motif, frequence);
            return new Rapport(sourceId, "EN_ERREUR", 0, motif);
        }
    }

    /** Enregistre un contenu déjà récupéré (première collecte, à la création de la source). */
    int integrer(StockActualites.SourceACollecter source, RecuperateurWeb.Contenu contenu) {
        if (SourceWeb.FLUX.equals(source.type())) {
            List<LecteurFlux.ElementFlux> elements = lecteurFlux.lire(contenu.octets()).elements();
            return stock.integrerFlux(source.id(), source.espaceId(),
                    elements.size() > elementsMax ? elements.subList(0, elementsMax) : elements);
        }
        LecteurPage.Instantane instantane = lecteurPage.lire(contenu.octets(), contenu.typeContenu(),
                contenu.adresseFinale().toString());
        return stock.integrerPage(source, instantane) ? 1 : 0;
    }

    /** Première collecte d'une source tout juste créée, avec le contenu lu pour la valider. */
    public void premiereCollecte(UUID sourceId, RecuperateurWeb.Contenu contenu) {
        StockActualites.SourceACollecter source = stock.source(sourceId).orElseThrow();
        integrer(source, contenu);
        stock.succes(sourceId, frequence);
    }
}
