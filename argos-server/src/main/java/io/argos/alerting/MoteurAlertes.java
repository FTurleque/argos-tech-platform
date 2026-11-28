package io.argos.alerting;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;

import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;
import io.argos.impact.ImpactQueries;
import io.argos.execution.ExecuteurBorne;
import io.argos.impact.ImpactsRecorded;
import io.argos.impact.MatchRules;
import io.argos.workspace.subscription.SubscriptionService;

/**
 * Évaluation des règles d'alerte sur les impacts publiés par {@code impact-engine} (DC-1), en un nombre de requêtes
 * indépendant du nombre de membres et de canaux (ALR-10) :
 * <ol>
 *   <li>les membres des espaces touchés, leurs espaces, les impacts actifs du signal dans ces espaces, leurs
 *       abonnements actifs et leurs alertes immédiates existantes sont lus en une transaction ; chaque destinataire ne
 *       voit que les impacts des espaces dont il est membre (lecture technique filtrée par appartenance, décision du
 *       porteur du projet du 2026-11-26, ACC-04), jamais un impact écarté ou résolu (ALR-07) ;</li>
 *   <li>ses abonnements actifs donnent ses règles (ABO-03, ABO-04) ;</li>
 *   <li>une règle immédiate dont la gravité minimale est atteinte et qui concerne au moins un impact
 *       exact ou probable produit une alerte sur son canal (ALR-01) ; un impact « à vérifier » n'en
 *       produit jamais (ALR-02) ;</li>
 *   <li>une seule alerte par signal, destinataire et canal cite tous les projets concernés (ALR-03, D2) ;</li>
 *   <li>tout autre élément concerné entre dans le prochain résumé du destinataire (ALR-01-S2, ALR-04).</li>
 * </ol>
 * Le résumé IA est demandé une fois par signal avec un délai borné ; son absence ne retient aucune
 * alerte (ALR-05). Les alertes et éléments de résumé sont écrits par lot ; les alertes créées restent en attente et
 * la tâche planifiée {@link EnvoiAlertes} les diffuse (ALR-10-S2).
 */
@ApplicationScoped
public class MoteurAlertes {

    private static final Logger LOG = Logger.getLogger(MoteurAlertes.class);
    static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    Clock horloge;

    /** Exécuteur dédié aux demandes de résumé IA, hors du pool commun : une demande expirée est annulée. */
    @Inject
    ExecuteurResumeIa executeurIa;

    @Inject
    Jdbc jdbc;

    @Inject
    Destinataires destinataires;

    @Inject
    ImpactQueries impacts;

    @Inject
    SubscriptionService abonnements;

    @Inject
    Instance<ResumeIa> resumeIa;

    @ConfigProperty(name = "argos.alerting.lien-base")
    String lienBase;

    @ConfigProperty(name = "argos.alerting.resume-ia.delai-max")
    Duration delaiResumeIa;

    /** Bilan d'une évaluation : alertes créées et éléments ajoutés aux résumés. */
    public record Bilan(List<UUID> alertes, int elementsDeResume) {
    }

    private record Plan(Destinataires.Destinataire destinataire, Map<String, Map<UUID, ImpactQueries.ImpactView>> immediates,
            Set<CleResume> resumes) {
    }

    /**
     * Données d'une évaluation, lues en une transaction : membres et leurs espaces, impacts actifs du signal dans les
     * espaces touchés, abonnements actifs et canaux des alertes immédiates existantes de chaque membre.
     */
    private record Lecture(List<Destinataires.Membre> membres, List<ImpactQueries.ImpactView> impacts,
            Map<UUID, List<SubscriptionService.Subscription>> abonnements, Map<UUID, Set<String>> alertesExistantes) {

        static Lecture vide() {
            return new Lecture(List.of(), List.of(), Map.of(), Map.of());
        }

        Set<String> canauxExistants(UUID compte) {
            return alertesExistantes.getOrDefault(compte, Set.of());
        }
    }

    /** Alerte immédiate à écrire : destinataire, canal et contenu sérialisé. */
    private record AlerteAEcrire(UUID compte, String canal, String contenu) {
    }

    /** Élément de résumé à écrire : destinataire, fréquence et canal. */
    private record ElementAEcrire(UUID compte, String frequence, String canal) {
    }

    /** Résumé périodique visé : fréquence et canal de la règle d'alerte. */
    private record CleResume(String frequence, String canal) {
    }

    /** Avertit au démarrage en exploitation si les liens des messages visent encore le poste local. */
    void verifierLienBase(@Observes StartupEvent demarrage) {
        if (LaunchMode.current() == LaunchMode.NORMAL && lienBase.contains("://localhost")) {
            LOG.warnf("ARGOS_PUBLIC_URL n'est pas défini : les liens des alertes visent %s", lienBase);
        }
    }

    void surImpacts(@ObservesAsync ImpactsRecorded evenement) {
        evaluerOuJournaliser(evenement);
    }

    /**
     * Observateur asynchrone de la publication des impacts (ADR-0039) : la corrélation n'attend pas l'évaluation des
     * alertes. Une panne d'accès aux données pendant l'évaluation est journalisée avec sa cause ; une erreur imprévue
     * sort de l'observateur, journalisée et comptée par la publication asynchrone.
     */
    private void evaluerOuJournaliser(ImpactsRecorded evenement) {
        try {
            evaluer(evenement, horloge.instant());
        } catch (IllegalStateException | QuarkusTransactionException erreur) {
            LOG.errorf(erreur, "évaluation des alertes du signal %s en échec", evenement.signalId());
        }
    }

    /** Évalue les règles de chaque destinataire possible pour les impacts publiés. */
    public Bilan evaluer(ImpactsRecorded evenement, Instant maintenant) {
        Set<UUID> espaces = evenement.impacts().stream().map(ImpactsRecorded.Item::espaceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> paquets = new LinkedHashMap<>();
        evenement.impacts().forEach(impact -> paquets.put(impact.impactId(), impact.packageKey()));
        Lecture lecture = QuarkusTransaction.requiringNew().call(() -> lire(evenement.signalId(), espaces));
        List<Plan> plans = new ArrayList<>();
        for (Destinataires.Membre membre : lecture.membres()) {
            plan(evenement, membre, lecture, paquets).ifPresent(plans::add);
        }
        ResumeObtenu resume = nouvelleAlerte(plans, lecture) ? resumeIa(evenement, espaces) : ResumeObtenu.sans("SANS_OBJET");
        List<AlerteAEcrire> alertes = new ArrayList<>();
        List<ElementAEcrire> elements = new ArrayList<>();
        for (Plan plan : plans) {
            alertes.addAll(immediates(evenement, plan, resume));
            elements.addAll(elementsDeResume(plan, lecture));
        }
        return QuarkusTransaction.requiringNew().call(() -> new Bilan(
                List.copyOf(creerImmediates(evenement, alertes, resume, maintenant)),
                ajouterAuxResumes(evenement, elements, maintenant)));
    }

    /** Lit en une transaction les données de l'évaluation d'un signal (une requête par nature de donnée). */
    private Lecture lire(UUID signalId, Set<UUID> espaces) {
        List<Destinataires.Membre> membres = destinataires.membresEtEspaces(espaces);
        if (membres.isEmpty()) {
            return Lecture.vide();
        }
        Map<UUID, Set<UUID>> espacesParCompte = new LinkedHashMap<>();
        Set<UUID> espacesDesMembres = new LinkedHashSet<>();
        membres.forEach(membre -> {
            espacesParCompte.put(membre.destinataire().compteId(), membre.espaces());
            espacesDesMembres.addAll(membre.espaces());
        });
        // Tous les espaces des destinataires : une alerte cite chaque projet de leurs espaces touché par le signal.
        return new Lecture(membres, impacts.actifsDuSignal(signalId, espacesDesMembres),
                abonnements.activesDesComptes(espacesParCompte), alertesExistantes(signalId, espacesParCompte.keySet()));
    }

    /** Canaux des alertes immédiates (hors annulées) du signal pour chacun des comptes. */
    private Map<UUID, Set<String>> alertesExistantes(UUID signalId, Set<UUID> comptes) {
        Map<UUID, Set<String>> canaux = new HashMap<>();
        jdbc.lister("""
                SELECT compte_id, canal FROM alerte
                WHERE nature = 'IMMEDIATE' AND signal_id = ? AND compte_id = ANY (?) AND etat <> 'ANNULEE'
                """, Jdbc.parametres(signalId, new Jdbc.Tableau("uuid", comptes)),
                resultat -> Map.entry(resultat.getObject(1, UUID.class), resultat.getString(2)))
                .forEach(ligne -> canaux.computeIfAbsent(ligne.getKey(), compte -> new HashSet<>()).add(ligne.getValue()));
        return canaux;
    }

    /** Vrai si au moins un plan prévoit une alerte immédiate qui n'existe pas encore. */
    private static boolean nouvelleAlerte(List<Plan> plans, Lecture lecture) {
        return plans.stream().anyMatch(plan -> plan.immediates().keySet().stream()
                .anyMatch(canal -> !lecture.canauxExistants(plan.destinataire().compteId()).contains(canal)));
    }

    /** Alertes immédiates du plan, une par canal. */
    private List<AlerteAEcrire> immediates(ImpactsRecorded evenement, Plan plan, ResumeObtenu resume) {
        List<AlerteAEcrire> alertes = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, ImpactQueries.ImpactView>> canal : plan.immediates().entrySet()) {
            ContenuAlerte contenu = new ContenuAlerte(List.of(element(SignalCite.depuis(evenement), canal.getValue().values(),
                    lienBase)), resume.texte(), resume.etat());
            alertes.add(new AlerteAEcrire(plan.destinataire().compteId(), canal.getKey(), jsonOuErreur(contenu)));
        }
        return alertes;
    }

    /**
     * Éléments de résumé du plan, sauf si le destinataire a une alerte immédiate du signal : existante, ou prévue par
     * ce plan (elle existe une fois les alertes écrites).
     */
    private static List<ElementAEcrire> elementsDeResume(Plan plan, Lecture lecture) {
        UUID compte = plan.destinataire().compteId();
        if (plan.resumes().isEmpty() || !plan.immediates().isEmpty() || !lecture.canauxExistants(compte).isEmpty()) {
            return List.of();
        }
        return plan.resumes().stream().map(cle -> new ElementAEcrire(compte, cle.frequence(), cle.canal())).toList();
    }

    private Optional<Plan> plan(ImpactsRecorded evenement, Destinataires.Membre membre, Lecture lecture,
            Map<UUID, String> paquets) {
        List<ImpactQueries.ImpactView> visibles = lecture.impacts().stream()
                .filter(impact -> membre.espaces().contains(impact.espaceId())).toList();
        if (visibles.isEmpty()) {
            return Optional.empty();
        }
        Destinataires.Destinataire destinataire = membre.destinataire();
        List<RegleAlerte> regles = lecture.abonnements().getOrDefault(destinataire.compteId(), List.of()).stream()
                .map(RegleAlerte::depuis).flatMap(Optional::stream).toList();
        Plan plan = new Plan(destinataire, new LinkedHashMap<>(), new LinkedHashSet<>());
        for (RegleAlerte regle : regles) {
            repartir(plan, regle, concernes(regle, visibles, evenement, paquets), evenement.signalSeverity());
        }
        if (plan.immediates().isEmpty() && plan.resumes().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    /** Impacts visibles que la règle concerne. */
    private static List<ImpactQueries.ImpactView> concernes(RegleAlerte regle, List<ImpactQueries.ImpactView> visibles,
            ImpactsRecorded evenement, Map<UUID, String> paquets) {
        return visibles.stream()
                .filter(impact -> regle.concerne(impact.projectId().toString(), cle(impact, paquets), evenement.signalSource()))
                .toList();
    }

    /** Range les impacts concernés par la règle : alerte immédiate sur son canal, sinon résumé de sa fréquence. */
    private static void repartir(Plan plan, RegleAlerte regle, List<ImpactQueries.ImpactView> concernes, String gravite) {
        if (concernes.isEmpty()) {
            return;
        }
        if (declencheImmediate(regle, concernes, gravite)) {
            Map<UUID, ImpactQueries.ImpactView> parImpact = plan.immediates().computeIfAbsent(regle.canal(),
                    canal -> new LinkedHashMap<>());
            concernes.forEach(impact -> parImpact.put(impact.id(), impact));
        } else {
            plan.resumes().add(new CleResume(regle.frequenceResume(), regle.canal()));
        }
    }

    /** Règle immédiate, gravité minimale atteinte et au moins un impact exact ou probable (ALR-01, ALR-02). */
    private static boolean declencheImmediate(RegleAlerte regle, List<ImpactQueries.ImpactView> concernes, String gravite) {
        return regle.immediate() && regle.seuilAtteint(gravite)
                && concernes.stream().anyMatch(impact -> impact.level() != MatchRules.Level.A_VERIFIER);
    }

    /** Clé du paquet d'un impact : celle de l'événement, sinon dérivée du PURL du composant. */
    private static String cle(ImpactQueries.ImpactView impact, Map<UUID, String> paquets) {
        String cle = paquets.get(impact.id());
        if (cle != null || impact.componentPurl() == null) {
            return cle;
        }
        int arobase = impact.componentPurl().lastIndexOf('@');
        return arobase > 4 ? impact.componentPurl().substring(0, arobase) : impact.componentPurl();
    }

    /** Signal cité par un élément d'alerte ou de résumé : identifiant, titre, gravité et source. */
    record SignalCite(UUID signalId, String titre, String gravite, String source, String externe) {

        static SignalCite depuis(ImpactsRecorded evenement) {
            return new SignalCite(evenement.signalId(), evenement.signalTitle(), evenement.signalSeverity(),
                    evenement.signalSource(), evenement.signalExternalId());
        }
    }

    /** Élément d'alerte ou de résumé : un projet cité une fois, avec le niveau d'impact le plus fort. */
    static ContenuAlerte.Element element(SignalCite signal, java.util.Collection<ImpactQueries.ImpactView> vues,
            String lienBase) {
        Map<UUID, ContenuAlerte.Projet> projets = new LinkedHashMap<>();
        for (ImpactQueries.ImpactView vue : vues) {
            ContenuAlerte.Projet actuel = projets.get(vue.projectId());
            if (actuel == null || rangNiveau(vue.level()) < rangNiveau(actuel.niveauImpact())) {
                projets.put(vue.projectId(), ContenuAlerte.Projet.impact(vue.projectId(), vue.projectName(), vue.level()));
            }
        }
        List<ContenuAlerte.Projet> tries = projets.values().stream()
                .sorted(Comparator.comparing(ContenuAlerte.Projet::nom)).toList();
        return new ContenuAlerte.Element(signal.signalId(), signal.titre(), signal.gravite(), tries,
                List.of(new ContenuAlerte.Source(signal.source(), signal.externe(), urlSource(signal.source(), signal.externe()))),
                LiensIhm.lien(lienBase, LiensIhm.SIGNAL, signal.signalId()));
    }

    /** Rang d'un niveau dans une alerte : le plus fort d'abord. */
    private static int rangNiveau(MatchRules.Level niveau) {
        return switch (niveau) {
            case EXACT -> 0;
            case PROBABLE -> 1;
            case A_VERIFIER -> 2;
        };
    }

    private static String urlSource(String source, String externe) {
        return "OSV".equalsIgnoreCase(source) ? "https://osv.dev/vulnerability/" + externe : null;
    }

    /** Résumé IA obtenu pour une alerte : texte éventuel et état ({@code DISPONIBLE}, {@code INDISPONIBLE}…). */
    private record ResumeObtenu(String texte, String etat) {

        static ResumeObtenu sans(String etat) {
            return new ResumeObtenu(null, etat);
        }
    }

    /** Résumé IA, imputé à l'un des espaces concernés et demandé avec un délai borné. */
    private ResumeObtenu resumeIa(ImpactsRecorded evenement, Set<UUID> espaces) {
        if (!resumeIa.isResolvable()) {
            return ResumeObtenu.sans("INDISPONIBLE");
        }
        ResumeIa resumeur = resumeIa.get();
        Set<UUID> concernes = espaces.stream().filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        if (!resumeur.budgetDisponible(concernes)) {
            return ResumeObtenu.sans("PLAFOND_ATTEINT");
        }
        ExecuteurBorne.Issue<String> issue = executeurIa.executer(
                () -> resumeur.resumer(evenement.signalId(), evenement.signalTitle(), concernes), delaiResumeIa);
        return resume(issue, evenement.signalId());
    }

    /**
     * Résumé d'après l'issue de la demande : une demande refusée (file pleine), expirée, interrompue ou en erreur vaut
     * résumé indisponible, cause journalisée avec sa pile ; le plafond atteint et le refus du modèle sont distingués.
     */
    private static ResumeObtenu resume(ExecuteurBorne.Issue<String> issue, UUID signalId) {
        if (issue.etat() == ExecuteurBorne.Etat.OBTENU) {
            String texte = issue.valeur().orElse("");
            return texte.isBlank() ? ResumeObtenu.sans("REFUSE") : new ResumeObtenu(texte, "DISPONIBLE");
        }
        if (issue.cause().filter(ResumeIa.PlafondAtteint.class::isInstance).isPresent()) {
            return ResumeObtenu.sans("PLAFOND_ATTEINT");
        }
        if (issue.etat() != ExecuteurBorne.Etat.INTERROMPUE) {
            LOG.infof(issue.cause().orElseThrow(), "résumé IA indisponible pour le signal %s (%s)", signalId, issue.etat());
        }
        return ResumeObtenu.sans("INDISPONIBLE");
    }

    /**
     * Écrit les alertes immédiates en une instruction ; une alerte existante n'est remplacée que si elle avait été
     * annulée (ALR-03, ALR-07-S1).
     *
     * @return identifiants des alertes créées ou rétablies
     */
    private List<UUID> creerImmediates(ImpactsRecorded evenement, List<AlerteAEcrire> alertes, ResumeObtenu resume,
            Instant maintenant) {
        if (alertes.isEmpty()) {
            return List.of();
        }
        return jdbc.lister("""
                INSERT INTO alerte(id, nature, compte_id, canal, signal_id, titre, gravite, contenu, resume_ia,
                                   etat_resume_ia, etat, cree_le)
                SELECT gen_random_uuid(), 'IMMEDIATE', d.compte_id, d.canal, ?, ?, ?, d.contenu::jsonb, ?, ?, 'EN_ATTENTE', ?
                FROM unnest(?::uuid[], ?::varchar[], ?::text[]) AS d(compte_id, canal, contenu)
                ON CONFLICT (signal_id, compte_id, canal) WHERE nature = 'IMMEDIATE' DO UPDATE SET
                       titre = excluded.titre, gravite = excluded.gravite, contenu = excluded.contenu,
                       resume_ia = excluded.resume_ia, etat_resume_ia = excluded.etat_resume_ia, etat = 'EN_ATTENTE',
                       tentatives = 0, prochain_essai = NULL, derniere_erreur = NULL, envoye_le = NULL,
                       cree_le = excluded.cree_le
                    WHERE alerte.etat = 'ANNULEE'
                RETURNING id
                """, Jdbc.parametres(evenement.signalId(), titreDuSignal(evenement), evenement.signalSeverity(), resume.texte(),
                        resume.etat(), Conversions.utc(maintenant),
                        new Jdbc.Tableau("uuid", alertes.stream().map(AlerteAEcrire::compte).toList()),
                        new Jdbc.Tableau("varchar", alertes.stream().map(AlerteAEcrire::canal).toList()),
                        new Jdbc.Tableau("text", alertes.stream().map(AlerteAEcrire::contenu).toList())),
                resultat -> resultat.getObject(1, UUID.class));
    }

    /** Ajoute les éléments aux résumés en une instruction ; un élément déjà présent n'est pas dupliqué. */
    private int ajouterAuxResumes(ImpactsRecorded evenement, List<ElementAEcrire> elements, Instant maintenant) {
        if (elements.isEmpty()) {
            return 0;
        }
        return jdbc.modifier("""
                INSERT INTO resume_element(compte_id, signal_id, frequence, canal, titre, gravite, source,
                                           identifiant_externe, enregistre_le)
                SELECT d.compte_id, ?, d.frequence, d.canal, ?, ?, ?, ?, ?
                FROM unnest(?::uuid[], ?::varchar[], ?::varchar[]) AS d(compte_id, frequence, canal)
                ON CONFLICT DO NOTHING
                """, Jdbc.parametres(evenement.signalId(), titreDuSignal(evenement), evenement.signalSeverity(),
                        evenement.signalSource(), evenement.signalExternalId(), Conversions.utc(maintenant),
                        new Jdbc.Tableau("uuid", elements.stream().map(ElementAEcrire::compte).toList()),
                        new Jdbc.Tableau("varchar", elements.stream().map(ElementAEcrire::frequence).toList()),
                        new Jdbc.Tableau("varchar", elements.stream().map(ElementAEcrire::canal).toList())));
    }

    /** Titre du signal, à défaut son identifiant externe. */
    private static String titreDuSignal(ImpactsRecorded evenement) {
        return evenement.signalTitle() == null ? evenement.signalExternalId() : evenement.signalTitle();
    }

    /** Contenu sérialisé ; une sérialisation impossible est une erreur imprévue, jamais un contenu vide. */
    private static String jsonOuErreur(ContenuAlerte contenu) {
        try {
            return JSON.writeValueAsString(contenu);
        } catch (JsonProcessingException erreur) {
            throw new IllegalStateException("sérialisation du contenu d'alerte impossible", erreur);
        }
    }

    static String json(Object valeur) throws SQLException {
        try {
            return JSON.writeValueAsString(valeur);
        } catch (JsonProcessingException erreur) {
            throw new SQLException("sérialisation du contenu impossible", erreur);
        }
    }
}
