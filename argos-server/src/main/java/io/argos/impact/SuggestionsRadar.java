package io.argos.impact;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.aigateway.DemandeIa;
import io.argos.aigateway.EntreeIa;
import io.argos.aigateway.PasserelleIa;
import io.argos.aigateway.RegistrePrompts;
import io.argos.aigateway.ReponseIa;
import io.argos.catalog.standard.ReferentielEspace;
import io.argos.catalog.standard.ReferentielStandards;
import io.argos.catalog.standard.ReferentielStandardsEnBase;
import io.argos.catalog.standard.ReferentielStandardsEnBase.TechnologieSuivie;
import io.argos.observabilite.ErreursImprevues;
import io.argos.releaseradar.CalendrierSorties;

/**
 * Production des suggestions de l'IA sur le référentiel des espaces (RAD-08, RAD-02-S2). Pour chaque technologie
 * reliée au calendrier des cycles de vie dont la version recommandée a atteint sa fin de support, la passerelle IA
 * est consultée ; si elle suggère de passer la version à {@code HOLD}, la suggestion est enregistrée en attente de la
 * décision d'un administrateur. Le référentiel n'est jamais modifié. Traitement technique, appelé par la tâche
 * quotidienne du radar après la synchronisation des cycles de vie.
 */
@ApplicationScoped
public class SuggestionsRadar {

    private static final Logger LOG = Logger.getLogger(SuggestionsRadar.class);

    /** Avis de l'IA qui demande d'enregistrer la suggestion. */
    static final int AVIS_SUGGERER = 1;

    /** Statut suggéré pour une version arrivée en fin de support (RAD-02-S2). */
    static final ReferentielStandards.Statut STATUT_SUGGERE = ReferentielStandards.Statut.HOLD;

    /** Bilan d'un passage : suggestions enregistrées et erreurs imprévues retenues. */
    public record Bilan(int suggestions, List<RuntimeException> erreursImprevues) {
    }

    @Inject
    ReferentielStandardsEnBase referentiel;

    @Inject
    ReferentielEspace referentielEspace;

    @Inject
    CalendrierSorties calendrier;

    @Inject
    PasserelleIa passerelle;

    /**
     * Passage du jour donné. Une panne d'accès aux données ou une erreur imprévue sur un espace est journalisée avec sa
     * cause et n'interrompt pas les autres espaces ; les erreurs imprévues sont retenues dans le bilan.
     */
    public Bilan produire(LocalDate aujourdhui) {
        ErreursImprevues imprevues = new ErreursImprevues();
        int enregistrees = 0;
        for (UUID espace : referentiel.espacesSuivis()) {
            enregistrees += produireOuJournaliser(espace, aujourdhui, imprevues);
        }
        if (enregistrees > 0) {
            LOG.infof("%d suggestion(s) de l'IA enregistrée(s) sur les référentiels", enregistrees);
        }
        return new Bilan(enregistrees, imprevues.retenues());
    }

    /** Point d'isolation d'un espace. */
    private int produireOuJournaliser(UUID espace, LocalDate aujourdhui, ErreursImprevues imprevues) {
        try {
            int enregistrees = 0;
            for (TechnologieSuivie technologie : referentiel.technologies(List.of(espace), true)) {
                if (suggerer(technologie, aujourdhui)) {
                    enregistrees++;
                }
            }
            return enregistrees;
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "suggestions du référentiel de l'espace %s en échec", espace);
            return 0;
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "suggestions du référentiel de l'espace %s interrompues par une erreur imprévue", espace);
            imprevues.retenir(imprevue);
            return 0;
        }
    }

    /** Vrai si une suggestion a été enregistrée pour la technologie. */
    private boolean suggerer(TechnologieSuivie technologie, LocalDate aujourdhui) {
        Optional<CalendrierSorties.Echeance> finDeSupport = finDeSupportAtteinte(technologie, aujourdhui);
        if (finDeSupport.isEmpty() || referentielEspace.suggestionEnAttente(technologie.espaceId(), technologie.cle())) {
            return false;
        }
        CalendrierSorties.Echeance echeance = finDeSupport.get();
        ReponseIa reponse = passerelle.demander(new DemandeIa(RegistrePrompts.SUGGESTION_STANDARD, technologie.espaceId(),
                List.of(EntreeIa.publique("technologie", technologie.libelle()),
                        EntreeIa.publique("version", technologie.versionRecommandee()),
                        EntreeIa.publique("finSupport", echeance.date().toString()),
                        EntreeIa.publique("source", echeance.source())),
                List.of(echeance.source())));
        if (!reponse.produit()) {
            LOG.infof("aucune suggestion pour %s dans l'espace %s : passerelle IA %s", technologie.cle(),
                    technologie.espaceId(), reponse.issue());
            return false;
        }
        if (premierAvis(reponse) != AVIS_SUGGERER) {
            return false;
        }
        return referentielEspace.suggerer(technologie.espaceId(), technologie.cle(), STATUT_SUGGERE.name(),
                technologie.versionRecommandee(), reponse.champ("justification")).isPresent();
    }

    /** Fin de support de la version recommandée, si elle est atteinte au jour donné et que la version n'est pas HOLD. */
    private Optional<CalendrierSorties.Echeance> finDeSupportAtteinte(TechnologieSuivie technologie, LocalDate aujourdhui) {
        if (technologie.statut() == STATUT_SUGGERE || technologie.versionRecommandee() == null) {
            return Optional.empty();
        }
        return calendrier.finDeSupport(technologie.cle(), technologie.versionRecommandee(), Set.of(technologie.espaceId()))
                .filter(echeance -> !echeance.date().isAfter(aujourdhui));
    }

    private static int premierAvis(ReponseIa reponse) {
        String avis = reponse.champ("avis");
        if (avis == null || avis.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(avis.split(",")[0].strip());
        } catch (NumberFormatException illisible) {
            LOG.infof(illisible, "avis de l'IA illisible : %s", avis);
            return 0;
        }
    }
}
