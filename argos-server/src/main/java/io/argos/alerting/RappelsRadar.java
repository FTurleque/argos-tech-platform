package io.argos.alerting;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;

import io.argos.horloge.LectureHeure;
import io.argos.impact.AnnonceStandard;
import io.argos.impact.RadarProjets;
import io.argos.observabilite.ErreursImprevues;
import io.argos.observabilite.TacheObservee;
import io.argos.regintel.EcheancesReglementaires;
import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;

/**
 * Rappels d'échéance du radar (RAD-04) et annonces de changement de standard (RAD-06).
 *
 * <p>Tâche quotidienne ({@link #executer}) : le radar est d'abord actualisé (synchronisation
 * endoflife.date, dettes de version), puis, pour chaque espace suivi et chaque jour de rappel de
 * l'espace (J-30, J-7 et J0 par défaut, paramétrables par espace : Q-1), chaque échéance qui touche au
 * moins un projet de l'espace produit un rappel pour chaque membre de l'espace. Une annonce de standard
 * est envoyée de même aux membres de l'espace dès qu'elle est publiée.
 *
 * <p>Rappels et annonces ne dépendent pas des abonnements : ils partent sur le canal {@code EMAIL}
 * quand le compte a une adresse, sinon sur le canal {@code DESKTOP}, et restent consultables dans
 * Argos. Leur clé d'idempotence ({@code cle_radar}) empêche tout second message pour le même rappel ou la même annonce.
 */
@ApplicationScoped
public class RappelsRadar {

    private static final Logger LOG = Logger.getLogger(RappelsRadar.class);

    /** Jours de rappel par défaut avant une échéance (Q-1). */
    public static final List<Integer> RAPPELS_PAR_DEFAUT = List.of(30, 7, 0);

    /** Longueur de la colonne {@code alerte.cle_radar}. */
    static final int CLE_MAX = 600;

    private static final String PARAMETRES_DE_L_ESPACE = "SELECT rappels_jours FROM radar_parametres WHERE espace_id = ?";

    /**
     * Bilan d'une exécution quotidienne, avec les erreurs imprévues retenues, que {@link #relancerErreurImprevue()}
     * relance.
     */
    public record Bilan(RadarProjets.Bilan radar, List<UUID> rappels, List<RuntimeException> erreursImprevues) {

        /** Relance la première erreur imprévue de l'exécution ; sans effet s'il n'y en a pas. */
        public void relancerErreurImprevue() {
            ErreursImprevues.relancer(erreursImprevues);
        }
    }

    @Inject
    Clock horloge;

    @Inject
    Jdbc jdbc;

    @Inject
    ContexteBase base;

    @Inject
    RadarProjets radar;

    @Inject
    Destinataires destinataires;

    @Inject
    EnvoiAlertes envoi;

    @Inject
    Event<AlerteEmise> emises;

    @ConfigProperty(name = "argos.alerting.lien-base")
    String lienBase;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @TacheObservee
    @Scheduled(identity = "argos-radar-quotidien", cron = "{argos.radar.tache.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        executer(LectureHeure.jour(horloge), horloge.instant()).relancerErreurImprevue();
    }

    /**
     * Tâche quotidienne du radar (DC-1) pour le jour donné. Une erreur imprévue de l'actualisation ou des rappels d'un
     * espace est journalisée avec sa cause et retenue dans le bilan ; les étapes et les espaces suivants sont traités.
     */
    public Bilan executer(LocalDate aujourdhui, Instant maintenant) {
        ErreursImprevues imprevues = new ErreursImprevues();
        RadarProjets.Bilan bilan = actualiserOuJournaliser(aujourdhui, maintenant, imprevues);
        List<UUID> crees = new ArrayList<>();
        for (UUID espace : espacesSuivisOuJournaliser()) {
            rappelerEspaceOuJournaliser(espace, aujourdhui, maintenant, crees, imprevues);
        }
        envoi.envoyer(crees, maintenant);
        return new Bilan(bilan, List.copyOf(crees), imprevues.retenues());
    }

    /**
     * Point d'isolation de la tâche planifiée : une panne d'accès aux données pendant l'actualisation est journalisée
     * avec sa cause et les
     * rappels partent quand même, sur le calendrier et les dettes déjà connus.
     */
    private RadarProjets.Bilan actualiserOuJournaliser(LocalDate aujourdhui, Instant maintenant,
            ErreursImprevues imprevues) {
        try {
            return radar.actualiser(aujourdhui, maintenant);
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "actualisation du radar en échec");
            return new RadarProjets.Bilan(Map.of(), 0);
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "actualisation du radar interrompue par une erreur imprévue");
            imprevues.retenir(imprevue);
            return new RadarProjets.Bilan(Map.of(), 0);
        }
    }

    /** Point d'isolation de la tâche planifiée : sans espaces lisibles, l'exécution s'achève sans rappel. */
    private List<UUID> espacesSuivisOuJournaliser() {
        try {
            return radar.espacesSuivis();
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "lecture des espaces suivis par le radar impossible");
            return List.of();
        }
    }

    /**
     * Point d'isolation de la tâche planifiée : une panne d'accès aux données pour un espace est journalisée avec sa
     * cause et n'interrompt pas les rappels des autres.
     */
    private void rappelerEspaceOuJournaliser(UUID espace, LocalDate aujourdhui, Instant maintenant, List<UUID> crees,
            ErreursImprevues imprevues) {
        try {
            for (int jours : parametresTechniques(espace)) {
                for (RadarProjets.EcheanceProjets echeance : radar.echeancesTouchant(espace, aujourdhui.plusDays(jours))) {
                    crees.addAll(rappeler(echeance, jours, maintenant));
                }
            }
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "rappels du radar de l'espace %s en échec", espace);
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "rappels du radar de l'espace %s interrompus par une erreur imprévue", espace);
            imprevues.retenir(imprevue);
        }
    }

    /** Annonce un changement de standard aux membres de l'espace dont un projet utilise la technologie. */
    void surAnnonce(@Observes AnnonceStandard annonce) {
        annoncerOuJournaliser(annonce);
    }

    /**
     * Observateur synchrone du relais d'un changement de standard : une panne d'accès aux données pendant l'annonce
     * est journalisée avec sa cause et ne remonte pas à la modification du référentiel qui l'a déclenchée.
     */
    private void annoncerOuJournaliser(AnnonceStandard annonce) {
        try {
            annoncer(annonce, horloge.instant());
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "annonce du changement de standard %s en échec", annonce.cle());
        }
    }

    private void annoncer(AnnonceStandard changement, Instant maintenant) {
        String titre = "Changement de standard : " + changement.libelle() + " passe de " + changement.ancienStatut()
                + version(changement.ancienneVersion()) + " à " + changement.statut() + version(changement.versionRecommandee());
        String detail = titre + ". Justification : " + changement.justification();
        // L'entrée d'historique identifie le changement : relayé deux fois, il ne produit qu'un message.
        String cle = "ANNONCE|" + changement.espaceId() + "|" + changement.historiqueId();
        ContenuAlerte contenu = contenu(detail, changement.projets(), "Référentiel de l'espace", changement.cle(),
                changement.espaceId());
        List<UUID> crees = creerPourMembres(new Message("ANNONCE", cle, titre, contenu), changement.espaceId(), maintenant);
        envoi.envoyer(crees, maintenant);
    }

    // ------------------------------------------------------------------ paramètres

    /** Jours de rappel de l'espace, lus sous le contexte du compte (membre). */
    public List<Integer> parametres(UUID compteId, UUID espaceId) {
        return base.executer(new Contexte(compteId, null, espaceId), connexion -> lireParametres(connexion, espaceId));
    }

    /**
     * Remplace les jours de rappel de l'espace (administrateur, RLS) : de 1 à 10 valeurs distinctes
     * entre 0 (jour de l'échéance) et 365.
     */
    public List<Integer> modifierParametres(UUID compteId, UUID espaceId, List<Integer> jours) {
        Integer[] valeurs = EcheancesReglementaires.valider(jours).toArray(Integer[]::new);
        return base.executer(new Contexte(compteId, null, espaceId), connexion -> {
            Jdbc.modifier(connexion, """
                    INSERT INTO radar_parametres (espace_id, rappels_jours, modifie_par, modifie_le)
                    VALUES (?, ?, argos_compte_courant(), now())
                    ON CONFLICT (espace_id) DO UPDATE SET rappels_jours = excluded.rappels_jours,
                        modifie_par = excluded.modifie_par, modifie_le = excluded.modifie_le
                    """, requete -> {
                        requete.setObject(1, espaceId);
                        requete.setArray(2, connexion.createArrayOf("integer", valeurs));
                    });
            return lireParametres(connexion, espaceId);
        });
    }

    private List<Integer> parametresTechniques(UUID espaceId) {
        return jdbc.unique(PARAMETRES_DE_L_ESPACE, Jdbc.parametres(espaceId), RappelsRadar::joursDeRappel)
                .orElse(RAPPELS_PAR_DEFAUT);
    }

    private static List<Integer> lireParametres(Connection connexion, UUID espaceId) {
        return Jdbc.unique(connexion, PARAMETRES_DE_L_ESPACE, Jdbc.parametres(espaceId), RappelsRadar::joursDeRappel)
                .orElse(RAPPELS_PAR_DEFAUT);
    }

    private static List<Integer> joursDeRappel(ResultSet resultat) throws SQLException {
        Array joursDeRappel = resultat.getArray(1);
        return Arrays.stream((Integer[]) joursDeRappel.getArray()).toList();
    }

    // ------------------------------------------------------------------ messages

    private List<UUID> rappeler(RadarProjets.EcheanceProjets echeanceProjets, int jours, Instant maintenant) {
        RadarProjets.EcheanceProjets echeance = echeanceProjets;
        String quoi = switch (echeance.nature()) {
            case "FIN_DE_SUPPORT" -> "Fin de support de ";
            case "SORTIE" -> "Sortie de ";
            default -> "Échéance de ";
        };
        String quand = jours == 0 ? "aujourd'hui (" + echeance.date() + ")" : "dans " + jours + " jours (" + echeance.date() + ")";
        String titre = quoi + echeanceProjets.libelle() + " " + echeance.version() + " " + quand;
        String cle = "RAPPEL|" + echeanceProjets.espaceId() + "|" + echeance.cle() + "|" + echeance.nature() + "|"
                + echeance.version() + "|" + echeance.date()
                + "|J-" + jours;
        ContenuAlerte contenu = contenu(titre, echeanceProjets.projets(), echeance.source(),
                echeance.cle() + " " + echeance.version(), echeanceProjets.espaceId());
        return creerPourMembres(new Message("RAPPEL", cle, titre, contenu), echeanceProjets.espaceId(), maintenant);
    }

    private ContenuAlerte contenu(String titre, List<RadarProjets.ProjetConcerne> projets, String source,
            String identifiant, UUID espaceId) {
        List<ContenuAlerte.Projet> cites = projets.stream()
                .map(projet -> ContenuAlerte.Projet.radar(projet.id(), projet.nom(), projet.version() == null ? "version inconnue"
                        : "version " + projet.version()))
                .toList();
        String url = "endoflife.date".equals(source) ? "https://endoflife.date" : null;
        return new ContenuAlerte(List.of(new ContenuAlerte.Element(null, titre, null, cites,
                List.of(new ContenuAlerte.Source(source, identifiant, url)),
                lienBase.replaceAll("/+$", "") + "/espaces/" + espaceId + "/radar")), null, "SANS_OBJET");
    }

    /**
     * Rappel ou annonce à enregistrer pour chaque destinataire.
     *
     * @param nature {@code RAPPEL} ou {@code ANNONCE}
     * @param cle clé d'idempotence : une même clé ne produit qu'un message par destinataire et canal
     */
    record Message(String nature, String cle, String titre, ContenuAlerte contenu) {
    }

    /** Un message par membre de l'espace, sur son canal ; aucun doublon pour une même clé. */
    private List<UUID> creerPourMembres(Message message, UUID espaceId, Instant maintenant) {
        List<UUID> crees = new ArrayList<>();
        for (Destinataires.Destinataire destinataire : destinataires.membres(Set.of(espaceId))) {
            String canal = destinataire.email() == null || destinataire.email().isBlank() ? "DESKTOP" : "EMAIL";
            creer(message, destinataire.compteId(), canal, maintenant).ifPresent(id -> {
                crees.add(id);
                emises.fire(new AlerteEmise(id, destinataire.compteId(), canal, message.nature()));
            });
        }
        return crees;
    }

    /**
     * Enregistre un rappel ou une annonce pour un destinataire et un canal ; vide si la clé
     * d'idempotence a déjà produit un message. Partagé avec les rappels de date limite
     * ({@link RappelsOpportunites}) et les rappels réglementaires ({@link RappelsReglementaires}).
     */
    Optional<UUID> creer(Message message, UUID compte, String canal, Instant maintenant) {
        return jdbc.unique("""
                INSERT INTO alerte (id, nature, compte_id, canal, cle_radar, titre, contenu, etat_resume_ia, etat, cree_le)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'SANS_OBJET', 'EN_ATTENTE', ?)
                ON CONFLICT (compte_id, canal, cle_radar) WHERE nature IN ('RAPPEL', 'ANNONCE') DO NOTHING
                RETURNING id
                """, requete -> Jdbc.parametres(UUID.randomUUID(), message.nature(), compte, canal, cleBornee(message.cle()),
                        message.titre(), MoteurAlertes.json(message.contenu()), Conversions.utc(maintenant)).lier(requete),
                resultat -> resultat.getObject(1, UUID.class));
    }

    /**
     * Clé d'idempotence tenue dans 600 caractères sans collision : au-delà, elle est remplacée par son
     * début suivi de l'empreinte SHA-256 de la clé complète.
     */
    static String cleBornee(String cle) {
        if (cle.length() <= CLE_MAX) {
            return cle;
        }
        try {
            byte[] empreinte = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(cle.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hex = java.util.HexFormat.of().formatHex(empreinte);
            return cle.substring(0, CLE_MAX - hex.length() - 1) + "#" + hex;
        } catch (java.security.NoSuchAlgorithmException erreur) {
            throw new IllegalStateException("SHA-256 indisponible", erreur);
        }
    }

    private static String version(String valeur) {
        return valeur == null ? "" : " (" + valeur + ")";
    }
}
