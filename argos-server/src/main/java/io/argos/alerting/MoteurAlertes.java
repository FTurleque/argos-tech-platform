package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;

import io.argos.impact.ImpactQueries;
import io.argos.impact.ImpactsRecorded;
import io.argos.workspace.subscription.SubscriptionService;

/**
 * Évaluation des règles d'alerte sur les impacts publiés par {@code impact-engine} (DC-1).
 *
 * <p>Pour chaque membre d'un espace touché :
 * <ol>
 *   <li>les impacts du signal qu'il est autorisé à voir sont relus sous son contexte (RLS, ACC-04) ; seuls
 *       les impacts ouverts ou confirmés sont retenus, jamais un impact écarté ou résolu (ALR-07) ;</li>
 *   <li>ses abonnements actifs donnent ses règles (ABO-03, ABO-04) ;</li>
 *   <li>une règle immédiate dont la gravité minimale est atteinte et qui concerne au moins un impact
 *       exact ou probable produit une alerte sur son canal (ALR-01) ; un impact « à vérifier » n'en
 *       produit jamais (ALR-02) ;</li>
 *   <li>une seule alerte par signal, destinataire et canal cite tous les projets concernés (ALR-03, D2) ;</li>
 *   <li>tout autre élément concerné entre dans le prochain résumé du destinataire (ALR-01-S2, ALR-04).</li>
 * </ol>
 * Le résumé IA est demandé une fois par signal avec un délai borné ; son absence ne retient aucune
 * alerte (ALR-05). Les alertes enregistrées sont publiées ({@link AlerteEmise}) puis diffusées.
 */
@ApplicationScoped
public class MoteurAlertes {

    private static final Logger LOG = Logger.getLogger(MoteurAlertes.class);
    static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Exécuteur dédié aux demandes de résumé IA : deux fils au plus et une file bornée, hors du pool
     * commun. Une demande expirée est annulée (interruption) ; une file pleine vaut résumé indisponible.
     */
    private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), tache -> {
                Thread fil = new Thread(tache, "argos-resume-ia");
                fil.setDaemon(true);
                return fil;
            }, new ThreadPoolExecutor.AbortPolicy());

    @Inject
    DataSource dataSource;

    @Inject
    Destinataires destinataires;

    @Inject
    ImpactQueries impacts;

    @Inject
    SubscriptionService abonnements;

    @Inject
    Instance<ResumeIa> resumeIa;

    @Inject
    EnvoiAlertes envoi;

    @Inject
    Event<AlerteEmise> emises;

    @ConfigProperty(name = "argos.alerting.lien-base")
    String lienBase;

    @ConfigProperty(name = "argos.alerting.resume-ia.delai-max")
    Duration delaiResumeIa;

    /** Bilan d'une évaluation : alertes créées et éléments ajoutés aux résumés. */
    public record Bilan(List<UUID> alertes, int elementsDeResume) {
    }

    private record Plan(Destinataires.Destinataire destinataire, Map<String, Map<UUID, ImpactQueries.ImpactView>> immediates,
            Set<String> resumes) {
    }

    /** Avertit au démarrage en exploitation si les liens des messages visent encore le poste local. */
    void verifierLienBase(@Observes StartupEvent demarrage) {
        if (LaunchMode.current() == LaunchMode.NORMAL && lienBase.contains("://localhost")) {
            LOG.warnf("ARGOS_PUBLIC_URL n'est pas défini : les liens des alertes visent %s", lienBase);
        }
    }

    void surImpacts(@Observes ImpactsRecorded evenement) {
        try {
            evaluer(evenement, Instant.now());
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "évaluation des alertes du signal %s en échec", evenement.signalId());
        }
    }

    /** Évalue les règles de chaque destinataire possible pour les impacts publiés. */
    public Bilan evaluer(ImpactsRecorded evenement, Instant maintenant) {
        Set<UUID> espaces = evenement.impacts().stream().map(ImpactsRecorded.Item::workspaceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> paquets = new LinkedHashMap<>();
        evenement.impacts().forEach(impact -> paquets.put(impact.impactId(), impact.packageKey()));
        List<Destinataires.Destinataire> candidats = QuarkusTransaction.requiringNew()
                .call(() -> destinataires.membres(espaces));
        List<Plan> plans = new ArrayList<>();
        for (Destinataires.Destinataire destinataire : candidats) {
            plan(evenement, destinataire, paquets).ifPresent(plans::add);
        }
        boolean nouvelleAlerte = plans.stream().anyMatch(plan -> plan.immediates().keySet().stream()
                .anyMatch(canal -> !alerteExiste(evenement.signalId(), plan.destinataire().compteId(), canal)));
        String[] resume = nouvelleAlerte ? resumeIa(evenement, espaces) : new String[] {null, "SANS_OBJET"};

        List<UUID> creees = new ArrayList<>();
        int elements = 0;
        for (Plan plan : plans) {
            UUID compte = plan.destinataire().compteId();
            for (Map.Entry<String, Map<UUID, ImpactQueries.ImpactView>> canal : plan.immediates().entrySet()) {
                ContenuAlerte contenu = new ContenuAlerte(List.of(element(evenement.signalId(), evenement.signalTitle(),
                        evenement.signalSeverity(), evenement.signalSource(), evenement.signalExternalId(), canal.getValue().values(),
                        lienBase)),
                        resume[0], resume[1]);
                creerImmediate(evenement, compte, canal.getKey(), contenu, maintenant).ifPresent(id -> {
                    creees.add(id);
                    emises.fire(new AlerteEmise(id, compte, canal.getKey(), RegleAlerte.IMMEDIATE));
                });
            }
            if (!plan.resumes().isEmpty() && !alerteExiste(evenement.signalId(), compte, null)) {
                for (String cle : plan.resumes()) {
                    String[] frequenceCanal = cle.split(":");
                    elements += ajouterAuResume(evenement, compte, frequenceCanal[0], frequenceCanal[1], maintenant);
                }
            }
        }
        envoi.envoyer(creees, maintenant);
        return new Bilan(List.copyOf(creees), elements);
    }

    private Optional<Plan> plan(ImpactsRecorded evenement, Destinataires.Destinataire destinataire, Map<UUID, String> paquets) {
        Set<UUID> espacesDuCompte = QuarkusTransaction.requiringNew().call(() -> destinataires.espacesDe(destinataire.compteId()));
        List<ImpactQueries.ImpactView> visibles = QuarkusTransaction.requiringNew()
                .call(() -> impacts.forSignals(List.of(evenement.signalId()), destinataire.compteId(), espacesDuCompte));
        if (visibles.isEmpty()) {
            return Optional.empty();
        }
        List<RegleAlerte> regles = QuarkusTransaction.requiringNew()
                .call(() -> abonnements.activeFor(destinataire.compteId(), espacesDuCompte)).stream()
                .map(RegleAlerte::depuis).flatMap(Optional::stream).toList();
        Map<String, Map<UUID, ImpactQueries.ImpactView>> immediates = new LinkedHashMap<>();
        Set<String> resumes = new LinkedHashSet<>();
        for (RegleAlerte regle : regles) {
            List<ImpactQueries.ImpactView> concernes = visibles.stream()
                    .filter(impact -> regle.concerne(impact.projectId().toString(), cle(impact, paquets),
                            evenement.signalSource())).toList();
            if (concernes.isEmpty()) {
                continue;
            }
            List<ImpactQueries.ImpactView> etablis = concernes.stream()
                    .filter(impact -> !"A_VERIFIER".equals(impact.level())).toList();
            if (regle.immediate() && regle.seuilAtteint(evenement.signalSeverity()) && !etablis.isEmpty()) {
                Map<UUID, ImpactQueries.ImpactView> parImpact = immediates.computeIfAbsent(regle.canal(), canal -> new LinkedHashMap<>());
                concernes.forEach(impact -> parImpact.put(impact.id(), impact));
            } else {
                resumes.add(regle.frequenceResume() + ":" + regle.canal());
            }
        }
        if (immediates.isEmpty() && resumes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Plan(destinataire, immediates, resumes));
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

    /** Élément d'alerte ou de résumé : un projet cité une fois, avec le niveau d'impact le plus fort. */
    static ContenuAlerte.Element element(UUID signalId, String titre, String gravite, String source, String externe,
            java.util.Collection<ImpactQueries.ImpactView> vues, String lienBase) {
        Map<UUID, ContenuAlerte.Projet> projets = new LinkedHashMap<>();
        for (ImpactQueries.ImpactView vue : vues) {
            ContenuAlerte.Projet actuel = projets.get(vue.projectId());
            if (actuel == null || rangNiveau(vue.level()) < rangNiveau(actuel.niveau())) {
                projets.put(vue.projectId(), new ContenuAlerte.Projet(vue.projectId(), vue.projectName(), vue.level()));
            }
        }
        List<ContenuAlerte.Projet> tries = projets.values().stream()
                .sorted(Comparator.comparing(ContenuAlerte.Projet::nom)).toList();
        return new ContenuAlerte.Element(signalId, titre, gravite, tries,
                List.of(new ContenuAlerte.Source(source, externe, urlSource(source, externe))),
                lienBase.replaceAll("/+$", "") + "/signaux/" + signalId);
    }

    private static int rangNiveau(String niveau) {
        return switch (niveau) {
            case "EXACT" -> 0;
            case "PROBABLE" -> 1;
            default -> 2;
        };
    }

    private static String urlSource(String source, String externe) {
        return "OSV".equalsIgnoreCase(source) ? "https://osv.dev/vulnerability/" + externe : null;
    }

    /** Résumé IA, imputé à l'un des espaces concernés et demandé avec un délai borné : {résumé, état}. */
    private String[] resumeIa(ImpactsRecorded evenement, Set<UUID> espaces) {
        if (!resumeIa.isResolvable()) {
            return new String[] {null, "INDISPONIBLE"};
        }
        ResumeIa resumeur = resumeIa.get();
        Set<UUID> concernes = espaces.stream().filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        if (!resumeur.budgetDisponible(concernes)) {
            return new String[] {null, "PLAFOND_ATTEINT"};
        }
        Future<String> demande;
        try {
            demande = EXECUTEUR_IA.submit(() -> resumeur.resumer(evenement.signalId(), evenement.signalTitle(), concernes));
        } catch (RejectedExecutionException erreur) {
            LOG.infof("résumé IA indisponible pour le signal %s : file d'attente pleine", evenement.signalId());
            return new String[] {null, "INDISPONIBLE"};
        }
        try {
            String texte = demande.get(delaiResumeIa.toMillis(), TimeUnit.MILLISECONDS);
            return texte == null || texte.isBlank() ? new String[] {null, "REFUSE"} : new String[] {texte, "DISPONIBLE"};
        } catch (InterruptedException erreur) {
            demande.cancel(true);
            Thread.currentThread().interrupt();
            return new String[] {null, "INDISPONIBLE"};
        } catch (java.util.concurrent.ExecutionException erreur) {
            if (erreur.getCause() instanceof ResumeIa.PlafondAtteint) {
                return new String[] {null, "PLAFOND_ATTEINT"};
            }
            LOG.infof("résumé IA indisponible pour le signal %s : %s", evenement.signalId(), erreur.getCause());
            return new String[] {null, "INDISPONIBLE"};
        } catch (Exception erreur) {
            demande.cancel(true);
            LOG.infof("résumé IA indisponible pour le signal %s : %s", evenement.signalId(), erreur.toString());
            return new String[] {null, "INDISPONIBLE"};
        }
    }

    /** Vrai si une alerte immédiate existe pour ce signal et ce destinataire, sur ce canal ou sur l'un d'eux. */
    boolean alerteExiste(UUID signalId, UUID compteId, String canal) {
        return QuarkusTransaction.requiringNew().call(() -> {
            try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                    SELECT 1 FROM alerte WHERE nature = 'IMMEDIATE' AND signal_id = ? AND compte_id = ?
                      AND etat <> 'ANNULEE' AND (?::varchar IS NULL OR canal = ?::varchar)
                    """)) {
                requete.setObject(1, signalId);
                requete.setObject(2, compteId);
                requete.setString(3, canal);
                requete.setString(4, canal);
                try (ResultSet resultat = requete.executeQuery()) {
                    return resultat.next();
                }
            }
        });
    }

    private Optional<UUID> creerImmediate(ImpactsRecorded evenement, UUID compte, String canal, ContenuAlerte contenu,
            Instant maintenant) {
        return QuarkusTransaction.requiringNew().call(() -> {
            try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO alerte(id, nature, compte_id, canal, signal_id, titre, gravite, contenu, resume_ia,
                                       etat_resume_ia, etat, cree_le)
                    VALUES (?, 'IMMEDIATE', ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'EN_ATTENTE', ?)
                    ON CONFLICT (signal_id, compte_id, canal) WHERE nature = 'IMMEDIATE' DO UPDATE SET
                           titre = excluded.titre, gravite = excluded.gravite, contenu = excluded.contenu,
                           resume_ia = excluded.resume_ia, etat_resume_ia = excluded.etat_resume_ia, etat = 'EN_ATTENTE',
                           tentatives = 0, prochain_essai = NULL, derniere_erreur = NULL, envoye_le = NULL,
                           cree_le = excluded.cree_le
                        WHERE alerte.etat = 'ANNULEE'
                    RETURNING id
                    """)) {
                requete.setObject(1, UUID.randomUUID());
                requete.setObject(2, compte);
                requete.setString(3, canal);
                requete.setObject(4, evenement.signalId());
                requete.setString(5, evenement.signalTitle() == null ? evenement.signalExternalId() : evenement.signalTitle());
                requete.setString(6, evenement.signalSeverity());
                requete.setString(7, json(contenu));
                requete.setString(8, contenu.resumeIa());
                requete.setString(9, contenu.etatResumeIa());
                requete.setObject(10, maintenant.atOffset(ZoneOffset.UTC));
                try (ResultSet resultat = requete.executeQuery()) {
                    return resultat.next() ? Optional.of(resultat.getObject(1, UUID.class)) : Optional.<UUID>empty();
                }
            }
        });
    }

    private int ajouterAuResume(ImpactsRecorded evenement, UUID compte, String frequence, String canal, Instant maintenant) {
        return QuarkusTransaction.requiringNew().call(() -> {
            try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO resume_element(compte_id, signal_id, frequence, canal, titre, gravite, source,
                                               identifiant_externe, enregistre_le)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """)) {
                requete.setObject(1, compte);
                requete.setObject(2, evenement.signalId());
                requete.setString(3, frequence);
                requete.setString(4, canal);
                requete.setString(5, evenement.signalTitle() == null ? evenement.signalExternalId() : evenement.signalTitle());
                requete.setString(6, evenement.signalSeverity());
                requete.setString(7, evenement.signalSource());
                requete.setString(8, evenement.signalExternalId());
                requete.setObject(9, maintenant.atOffset(ZoneOffset.UTC));
                return requete.executeUpdate();
            }
        });
    }

    static String json(Object valeur) throws SQLException {
        try {
            return JSON.writeValueAsString(valeur);
        } catch (JsonProcessingException erreur) {
            throw new SQLException("sérialisation du contenu impossible", erreur);
        }
    }
}
