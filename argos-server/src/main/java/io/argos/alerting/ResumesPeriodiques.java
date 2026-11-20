package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;

import io.argos.impact.ImpactQueries;

/**
 * Résumés quotidiens et hebdomadaires (ALR-04) : quotidien à 08:00, hebdomadaire le lundi à 08:00,
 * fuseau Europe/Paris, paramétrables (décision du 2026-11-16, Q-3).
 *
 * <p>Un résumé reprend, pour un destinataire et un canal, les éléments en attente de cette fréquence,
 * hors ceux qui lui ont été alertés immédiatement. Les projets cités sont relus sous le contexte du
 * destinataire au moment de la composition : un élément qu'il ne peut plus voir, ou dont tous les
 * impacts ont été écartés ou résolus depuis (ALR-07-S2), est retiré. Sans
 * élément restant, aucun résumé n'est envoyé (ALR-04-S2). Un élément entre une seule fois dans un
 * résumé d'une fréquence et d'un canal donnés.
 */
@ApplicationScoped
public class ResumesPeriodiques {

    private static final Logger LOG = Logger.getLogger(ResumesPeriodiques.class);

    @Inject
    DataSource dataSource;

    @Inject
    Destinataires destinataires;

    @Inject
    ImpactQueries impacts;

    @Inject
    EnvoiAlertes envoi;

    @Inject
    Event<AlerteEmise> emises;

    @ConfigProperty(name = "argos.alerting.lien-base")
    String lienBase;

    private record Element(UUID signalId, String titre, String gravite, String source, String externe) {
    }

    /** Destinataire et canal d'un résumé. */
    private record Boite(UUID compte, String canal) {
    }

    @Scheduled(identity = "argos-resume-quotidien", cron = "{argos.alerting.resume-quotidien.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        composer(RegleAlerte.DAILY, Instant.now());
    }

    @Scheduled(identity = "argos-resume-hebdomadaire", cron = "{argos.alerting.resume-hebdomadaire.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void hebdomadaire() {
        composer(RegleAlerte.WEEKLY, Instant.now());
    }

    /** Compose et diffuse les résumés d'une fréquence à l'échéance donnée ; renvoie les résumés créés. */
    public List<UUID> composer(String frequence, Instant echeance) {
        List<Boite> boites = new ArrayList<>();
        QuarkusTransaction.requiringNew().run(() -> {
            try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                    SELECT DISTINCT compte_id, canal FROM resume_element
                    WHERE frequence = ? AND alerte_id IS NULL AND enregistre_le <= ? ORDER BY compte_id, canal
                    """)) {
                requete.setString(1, frequence);
                requete.setObject(2, echeance.atOffset(ZoneOffset.UTC));
                try (ResultSet resultat = requete.executeQuery()) {
                    while (resultat.next()) {
                        boites.add(new Boite(resultat.getObject(1, UUID.class), resultat.getString(2)));
                    }
                }
            } catch (java.sql.SQLException erreur) {
                throw new IllegalStateException("lecture des éléments de résumé impossible", erreur);
            }
        });
        List<UUID> crees = new ArrayList<>();
        for (Boite boite : boites) {
            composerSansPropager(boite, frequence, echeance, crees);
        }
        envoi.envoyer(crees, echeance);
        return crees;
    }

    /**
     * Point d'isolation de la tâche planifiée : l'échec du résumé d'un destinataire est journalisé avec sa cause
     * et n'empêche pas la composition des résumés des autres destinataires.
     */
    private void composerSansPropager(Boite boite, String frequence, Instant echeance, List<UUID> crees) {
        try {
            composerPour(boite.compte(), boite.canal(), frequence, echeance).ifPresent(id -> {
                crees.add(id);
                emises.fire(new AlerteEmise(id, boite.compte(), boite.canal(), frequence));
            });
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "résumé %s du compte %s sur %s en échec", frequence, boite.compte(), boite.canal());
        }
    }

    private java.util.Optional<UUID> composerPour(UUID compte, String canal, String frequence, Instant echeance) {
        Boite boite = new Boite(compte, canal);
        List<Element> elements = QuarkusTransaction.requiringNew()
                .call(() -> lireElementsEnAttente(boite, frequence, echeance));
        if (elements.isEmpty()) {
            return java.util.Optional.empty();
        }
        TriElements tri = trierSelonVisibilite(compte, elements);
        return QuarkusTransaction.requiringNew().call(() -> enregistrerResume(boite, frequence, echeance, tri));
    }

    /** Éléments en attente du résumé, hors signaux déjà alertés immédiatement au destinataire. */
    private List<Element> lireElementsEnAttente(Boite boite, String frequence, Instant echeance)
            throws java.sql.SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            retirerDejaAlertes(connexion, boite, frequence);
            return lireEnAttente(connexion, boite, frequence, echeance);
        }
    }

    private static void retirerDejaAlertes(Connection connexion, Boite boite, String frequence)
            throws java.sql.SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                DELETE FROM resume_element e WHERE e.compte_id = ? AND e.canal = ? AND e.frequence = ?
                  AND e.alerte_id IS NULL AND EXISTS (SELECT 1 FROM alerte a WHERE a.nature = 'IMMEDIATE'
                      AND a.etat <> 'ANNULEE' AND a.signal_id = e.signal_id AND a.compte_id = e.compte_id)
                """)) {
            requete.setObject(1, boite.compte());
            requete.setString(2, boite.canal());
            requete.setString(3, frequence);
            requete.executeUpdate();
        }
    }

    private static List<Element> lireEnAttente(Connection connexion, Boite boite, String frequence, Instant echeance)
            throws java.sql.SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT signal_id, titre, gravite, source, identifiant_externe FROM resume_element
                WHERE compte_id = ? AND canal = ? AND frequence = ? AND alerte_id IS NULL AND enregistre_le <= ?
                ORDER BY enregistre_le, signal_id
                """)) {
            requete.setObject(1, boite.compte());
            requete.setString(2, boite.canal());
            requete.setString(3, frequence);
            requete.setObject(4, echeance.atOffset(ZoneOffset.UTC));
            try (ResultSet resultat = requete.executeQuery()) {
                List<Element> enAttente = new ArrayList<>();
                while (resultat.next()) {
                    enAttente.add(new Element(resultat.getObject(1, UUID.class), resultat.getString(2),
                            resultat.getString(3), resultat.getString(4), resultat.getString(5)));
                }
                return enAttente;
            }
        }
    }

    /**
     * Éléments répartis selon ce que le destinataire voit au moment de la composition : contenus des éléments
     * retenus, signaux retenus et signaux retirés (plus aucun impact visible ouvert ou confirmé, ALR-07-S2).
     */
    private record TriElements(List<ContenuAlerte.Element> contenus, List<UUID> retenus, List<UUID> retires) {
    }

    private TriElements trierSelonVisibilite(UUID compte, List<Element> elements) {
        Set<UUID> espaces = QuarkusTransaction.requiringNew().call(() -> destinataires.espacesDe(compte));
        Map<UUID, List<ImpactQueries.ImpactView>> visibles = QuarkusTransaction.requiringNew()
                .call(() -> impacts.forSignals(elements.stream().map(Element::signalId).toList(), compte, espaces))
                .stream().collect(Collectors.groupingBy(ImpactQueries.ImpactView::signalId, LinkedHashMap::new,
                        Collectors.toList()));
        TriElements tri = new TriElements(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (Element element : elements) {
            List<ImpactQueries.ImpactView> vues = visibles.get(element.signalId());
            if (vues == null) {
                tri.retires().add(element.signalId());
                continue;
            }
            tri.retenus().add(element.signalId());
            tri.contenus().add(MoteurAlertes.element(new MoteurAlertes.SignalCite(element.signalId(), element.titre(),
                    element.gravite(), element.source(), element.externe()), vues, lienBase));
        }
        return tri;
    }

    /** Retire les éléments sans objet, crée l'alerte de résumé et y rattache les éléments retenus. */
    private java.util.Optional<UUID> enregistrerResume(Boite boite, String frequence, Instant echeance,
            TriElements tri) throws java.sql.SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            supprimer(connexion, boite.compte(), boite.canal(), frequence, tri.retires());
            if (tri.contenus().isEmpty()) {
                return java.util.Optional.<UUID>empty();
            }
            java.util.Optional<UUID> alerte = creerAlerteResume(connexion, boite, frequence, echeance, tri.contenus());
            if (alerte.isPresent()) {
                rattacher(connexion, alerte.get(), boite, frequence, tri.retenus());
            }
            return alerte;
        }
    }

    /** Crée l'alerte de résumé de la période et renvoie son identifiant ; vide si elle existe déjà (ALR-03). */
    private static java.util.Optional<UUID> creerAlerteResume(Connection connexion, Boite boite, String frequence,
            Instant echeance, List<ContenuAlerte.Element> contenus) throws java.sql.SQLException {
        UUID id = UUID.randomUUID();
        String titre = contenus.size() + (contenus.size() == 1 ? " élément" : " éléments")
                + (RegleAlerte.WEEKLY.equals(frequence) ? " cette semaine" : " depuis le dernier résumé");
        try (PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO alerte(id, nature, compte_id, canal, fin_periode, titre, gravite, contenu, resume_ia,
                                   etat_resume_ia, etat, cree_le)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?::jsonb, NULL, 'SANS_OBJET', 'EN_ATTENTE', ?)
                ON CONFLICT (compte_id, canal, nature, fin_periode) WHERE nature <> 'IMMEDIATE' DO NOTHING
                RETURNING id
                """)) {
            requete.setObject(1, id);
            requete.setString(2, frequence);
            requete.setObject(3, boite.compte());
            requete.setString(4, boite.canal());
            requete.setObject(5, echeance.atOffset(ZoneOffset.UTC));
            requete.setString(6, titre);
            requete.setString(7, MoteurAlertes.json(new ContenuAlerte(contenus, null, "SANS_OBJET")));
            requete.setObject(8, echeance.atOffset(ZoneOffset.UTC));
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? java.util.Optional.of(id) : java.util.Optional.<UUID>empty();
            }
        }
    }

    /** Rattache les éléments retenus à l'alerte de résumé créée. */
    private static void rattacher(Connection connexion, UUID alerteId, Boite boite, String frequence,
            List<UUID> retenus) throws java.sql.SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                UPDATE resume_element SET alerte_id = ?
                WHERE compte_id = ? AND canal = ? AND frequence = ? AND signal_id = ANY (?) AND alerte_id IS NULL
                """)) {
            requete.setObject(1, alerteId);
            requete.setObject(2, boite.compte());
            requete.setString(3, boite.canal());
            requete.setString(4, frequence);
            requete.setArray(5, connexion.createArrayOf("uuid", retenus.toArray()));
            requete.executeUpdate();
        }
    }

    private static void supprimer(Connection connexion, UUID compte, String canal, String frequence, List<UUID> signaux)
            throws java.sql.SQLException {
        if (signaux.isEmpty()) {
            return;
        }
        try (PreparedStatement requete = connexion.prepareStatement("""
                DELETE FROM resume_element
                WHERE compte_id = ? AND canal = ? AND frequence = ? AND signal_id = ANY (?) AND alerte_id IS NULL
                """)) {
            requete.setObject(1, compte);
            requete.setString(2, canal);
            requete.setString(3, frequence);
            requete.setArray(4, connexion.createArrayOf("uuid", signaux.toArray()));
            requete.executeUpdate();
        }
    }
}
