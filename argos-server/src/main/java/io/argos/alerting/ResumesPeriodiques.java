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
            UUID compte = boite.compte();
            String canal = boite.canal();
            try {
                composerPour(compte, canal, frequence, echeance).ifPresent(id -> {
                    crees.add(id);
                    emises.fire(new AlerteEmise(id, compte, canal, frequence));
                });
            } catch (RuntimeException erreur) {
                LOG.errorf(erreur, "résumé %s du compte %s sur %s en échec", frequence, compte, canal);
            }
        }
        envoi.envoyer(crees, echeance);
        return crees;
    }

    private java.util.Optional<UUID> composerPour(UUID compte, String canal, String frequence, Instant echeance) {
        // Éléments en attente, hors signaux déjà alertés immédiatement au destinataire.
        List<Element> elements = QuarkusTransaction.requiringNew().call(() -> {
            try (Connection connexion = dataSource.getConnection()) {
                try (PreparedStatement requete = connexion.prepareStatement("""
                        DELETE FROM resume_element e WHERE e.compte_id = ? AND e.canal = ? AND e.frequence = ?
                          AND e.alerte_id IS NULL AND EXISTS (SELECT 1 FROM alerte a WHERE a.nature = 'IMMEDIATE'
                              AND a.etat <> 'ANNULEE' AND a.signal_id = e.signal_id AND a.compte_id = e.compte_id)
                        """)) {
                    requete.setObject(1, compte);
                    requete.setString(2, canal);
                    requete.setString(3, frequence);
                    requete.executeUpdate();
                }
                List<Element> found = new ArrayList<>();
                try (PreparedStatement requete = connexion.prepareStatement("""
                        SELECT signal_id, titre, gravite, source, identifiant_externe FROM resume_element
                        WHERE compte_id = ? AND canal = ? AND frequence = ? AND alerte_id IS NULL AND enregistre_le <= ?
                        ORDER BY enregistre_le, signal_id
                        """)) {
                    requete.setObject(1, compte);
                    requete.setString(2, canal);
                    requete.setString(3, frequence);
                    requete.setObject(4, echeance.atOffset(ZoneOffset.UTC));
                    try (ResultSet resultat = requete.executeQuery()) {
                        while (resultat.next()) {
                            found.add(new Element(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                                    resultat.getString(4), resultat.getString(5)));
                        }
                    }
                }
                return found;
            }
        });
        if (elements.isEmpty()) {
            return java.util.Optional.empty();
        }
        Set<UUID> espaces = QuarkusTransaction.requiringNew().call(() -> destinataires.espacesDe(compte));
        Map<UUID, List<ImpactQueries.ImpactView>> visibles = QuarkusTransaction.requiringNew()
                .call(() -> impacts.forSignals(elements.stream().map(Element::signalId).toList(), compte, espaces))
                .stream().collect(Collectors.groupingBy(ImpactQueries.ImpactView::signalId, LinkedHashMap::new,
                        Collectors.toList()));
        List<ContenuAlerte.Element> contenus = new ArrayList<>();
        List<UUID> retenus = new ArrayList<>();
        List<UUID> retires = new ArrayList<>();
        for (Element element : elements) {
            List<ImpactQueries.ImpactView> vues = visibles.get(element.signalId());
            if (vues == null) {
                retires.add(element.signalId());
                continue;
            }
            retenus.add(element.signalId());
            contenus.add(MoteurAlertes.element(element.signalId(), element.titre(), element.gravite(), element.source(),
                    element.externe(), vues, lienBase));
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            try (Connection connexion = dataSource.getConnection()) {
                supprimer(connexion, compte, canal, frequence, retires);
                if (contenus.isEmpty()) {
                    return java.util.Optional.<UUID>empty();
                }
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
                    requete.setObject(3, compte);
                    requete.setString(4, canal);
                    requete.setObject(5, echeance.atOffset(ZoneOffset.UTC));
                    requete.setString(6, titre);
                    requete.setString(7, MoteurAlertes.json(new ContenuAlerte(contenus, null, "SANS_OBJET")));
                    requete.setObject(8, echeance.atOffset(ZoneOffset.UTC));
                    try (ResultSet resultat = requete.executeQuery()) {
                        if (!resultat.next()) {
                            return java.util.Optional.<UUID>empty();
                        }
                    }
                }
                try (PreparedStatement requete = connexion.prepareStatement("""
                        UPDATE resume_element SET alerte_id = ?
                        WHERE compte_id = ? AND canal = ? AND frequence = ? AND signal_id = ANY (?) AND alerte_id IS NULL
                        """)) {
                    requete.setObject(1, id);
                    requete.setObject(2, compte);
                    requete.setString(3, canal);
                    requete.setString(4, frequence);
                    requete.setArray(5, connexion.createArrayOf("uuid", retenus.toArray()));
                    requete.executeUpdate();
                }
                return java.util.Optional.of(id);
            }
        });
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
