package io.argos.signal.web;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.ingestion.web.LecteurFlux;
import io.argos.ingestion.web.LecteurPage;
import io.argos.ingestion.web.RecuperateurWeb;
import io.argos.ingestion.web.RecuperationRefusee;
import io.argos.workspace.RegleViolee;
import io.quarkus.scheduler.Scheduled;

/**
 * Collecte des sources de veille web (WEB-01-S1, WEB-02-S1, WEB-03-S2) : récupération bornée et
 * vérifiée à chaque redirection, lecture du flux ou de la page, enregistrement dédupliqué. Le
 * planificateur collecte toutes les {@code argos.veille-web.cadence} les sources échues ; chaque
 * source est collectée toutes les {@code argos.veille-web.frequence} (une heure par défaut, Q-2),
 * avec des tentatives espacées après un échec.
 *
 * <p>Un passage du planificateur traite au plus {@code argos.veille-web.sources-par-passage}
 * sources, une à une, et n'en commence plus après {@code argos.veille-web.duree-max-passage} ; les
 * sources restantes, toujours échues, sont reprises au passage suivant.
 *
 * <p>La collecte d'une source se fait sous son verrou de collecte ({@link StockActualites#verrouiller}) :
 * une collecte à la demande et le planificateur ne traitent jamais la même source en même temps, et
 * l'instantané d'une page est relu sous ce verrou.
 *
 * <p>La récupération se fait hors transaction ; seules les écritures sont transactionnelles. Le
 * motif enregistré pour un échec est le motif générique du refus ; le détail (hôte, adresse,
 * statut) n'est écrit que dans le journal applicatif.
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

    @ConfigProperty(name = "argos.veille-web.duree-max-passage", defaultValue = "PT4M")
    Duration dureeMaxPassage;

    @ConfigProperty(name = "argos.veille-web.elements-max", defaultValue = "100")
    int elementsMax;

    /** Horloge du planificateur, remplaçable par les tests. */
    Supplier<Instant> horloge = Instant::now;

    @Scheduled(identity = "collecte-veille-web", every = "{argos.veille-web.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifier() {
        if (planificationActive) {
            collecterSourcesEchues();
        }
    }

    /**
     * Collecte les sources dont l'échéance est passée, dans la limite du nombre de sources et de la
     * durée d'un passage. Une source en cours de collecte ailleurs, ou qui n'est plus échue une fois
     * son verrou pris, est laissée de côté.
     */
    public List<Rapport> collecterSourcesEchues() {
        Instant fin = horloge.get().plus(dureeMaxPassage);
        List<Rapport> rapports = new ArrayList<>();
        for (UUID id : stock.echues(sourcesParPassage)) {
            if (!horloge.get().isBefore(fin)) {
                LOG.infof("passage de collecte web arrêté après %s : sources restantes reprises au passage suivant",
                        dureeMaxPassage);
                break;
            }
            Optional<StockActualites.Verrou> verrou = stock.verrouiller(id);
            if (verrou.isEmpty()) {
                continue;
            }
            try (StockActualites.Verrou verrouPose = verrou.get()) {
                if (stock.echue(id)) {
                    stock.source(id).map(this::collecterSousVerrou).ifPresent(rapports::add);
                }
            }
        }
        return rapports;
    }

    /**
     * Collecte une source à la demande ; un échec la fait passer {@code EN_ERREUR} avec son motif.
     *
     * @throws RegleViolee {@code COLLECTE_EN_COURS} si une autre collecte de la source est en cours
     */
    public Rapport collecter(UUID sourceId) {
        try (StockActualites.Verrou verrouPose = stock.verrouiller(sourceId).orElseThrow(() -> new RegleViolee(
                "COLLECTE_EN_COURS", "Une collecte de cette source est déjà en cours."))) {
            StockActualites.SourceACollecter source = stock.source(sourceId)
                    .orElseThrow(() -> new IllegalArgumentException("source web inconnue : " + sourceId));
            return collecterSousVerrou(source);
        }
    }

    private Rapport collecterSousVerrou(StockActualites.SourceACollecter source) {
        UUID sourceId = source.id();
        try {
            RecuperateurWeb.Contenu contenu = recuperateur.recuperer(URI.create(source.adresse()));
            int nouvelles = integrer(source, contenu);
            stock.succes(sourceId, frequence);
            return new Rapport(sourceId, "ACTIVE", nouvelles, null);
        } catch (RecuperationRefusee refus) {
            return echec(sourceId, refus);
        } catch (RuntimeException erreur) {
            return echecInterne(sourceId, erreur);
        }
    }

    /** Enregistre un contenu déjà récupéré. */
    int integrer(StockActualites.SourceACollecter source, RecuperateurWeb.Contenu contenu) {
        if (SourceWeb.FLUX.equals(source.type())) {
            List<LecteurFlux.ElementFlux> elements = lecteurFlux.lire(contenu.octets()).elements();
            return stock.integrerFlux(source, elements.size() > elementsMax ? elements.subList(0, elementsMax) : elements);
        }
        LecteurPage.Instantane instantane = lecteurPage.lire(contenu.octets(), contenu.typeContenu(),
                contenu.adresseFinale().toString());
        return stock.integrerPage(source, instantane) ? 1 : 0;
    }

    /**
     * Première collecte d'une source tout juste créée, avec le contenu lu pour la valider ; l'appelant
     * tient le verrou de collecte de la source. Un échec laisse la source {@code EN_ERREUR} avec son
     * motif, reprise par le planificateur après l'attente d'un échec.
     */
    Rapport premiereCollecte(UUID sourceId, RecuperateurWeb.Contenu contenu) {
        try {
            StockActualites.SourceACollecter source = stock.source(sourceId).orElseThrow();
            int nouvelles = integrer(source, contenu);
            stock.succes(sourceId, frequence);
            return new Rapport(sourceId, "ACTIVE", nouvelles, null);
        } catch (RecuperationRefusee refus) {
            return echec(sourceId, refus);
        } catch (RuntimeException erreur) {
            return echecInterne(sourceId, erreur);
        }
    }

    private Rapport echec(UUID sourceId, RecuperationRefusee refus) {
        String motif = refus.motifPublic();
        LOG.infof("collecte de la source web %s en échec : %s (%s)", sourceId, refus.motif(), refus.getMessage());
        stock.echec(sourceId, motif, frequence);
        return new Rapport(sourceId, "EN_ERREUR", 0, motif);
    }

    private Rapport echecInterne(UUID sourceId, RuntimeException erreur) {
        String motif = "ERREUR_INTERNE : la collecte a été interrompue.";
        LOG.errorf(erreur, "collecte de la source web %s interrompue", sourceId);
        stock.echec(sourceId, motif, frequence);
        return new Rapport(sourceId, "EN_ERREUR", 0, motif);
    }
}
