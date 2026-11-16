package io.argos.alerting;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;

import io.argos.impact.AnnonceStandard;
import io.argos.impact.RadarProjets;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.argos.workspace.DonneeInvalide;

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

    /** Bilan d'une exécution quotidienne. */
    public record Bilan(RadarProjets.Bilan radar, List<UUID> rappels) {
    }

    @Inject
    DataSource dataSource;

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

    @Scheduled(identity = "argos-radar-quotidien", cron = "{argos.radar.tache.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        executer(LocalDate.now(ZoneId.of(fuseau)), Instant.now());
    }

    /** Tâche quotidienne du radar (DC-1) pour le jour donné. */
    public Bilan executer(LocalDate aujourdhui, Instant maintenant) {
        RadarProjets.Bilan bilan;
        try {
            bilan = radar.actualiser(aujourdhui, maintenant);
        } catch (RuntimeException e) {
            // Les rappels partent quand même, sur le calendrier et les dettes déjà connus.
            LOG.errorf(e, "actualisation du radar en échec");
            bilan = new RadarProjets.Bilan(java.util.Map.of(), 0);
        }
        List<UUID> crees = new ArrayList<>();
        List<UUID> espaces;
        try {
            espaces = radar.espacesSuivis();
        } catch (RuntimeException e) {
            LOG.errorf(e, "lecture des espaces suivis par le radar impossible");
            espaces = List.of();
        }
        for (UUID espace : espaces) {
            try {
                for (int jours : parametresTechniques(espace)) {
                    for (RadarProjets.EcheanceProjets ep : radar.echeancesTouchant(espace, aujourdhui.plusDays(jours))) {
                        crees.addAll(rappeler(ep, jours, maintenant));
                    }
                }
            } catch (RuntimeException e) {
                LOG.errorf(e, "rappels du radar de l'espace %s en échec", espace);
            }
        }
        envoi.envoyer(crees, maintenant);
        return new Bilan(bilan, List.copyOf(crees));
    }

    /** Annonce un changement de standard aux membres de l'espace dont un projet utilise la technologie. */
    void surAnnonce(@Observes AnnonceStandard annonce) {
        try {
            Instant maintenant = Instant.now();
            AnnonceStandard ch = annonce;
            String titre = "Changement de standard : " + ch.libelle() + " passe de " + ch.ancienStatut()
                    + version(ch.ancienneVersion()) + " à " + ch.statut() + version(ch.versionRecommandee());
            String detail = titre + ". Justification : " + ch.justification();
            // L'entrée d'historique identifie le changement : relayé deux fois, il ne produit qu'un message.
            String cle = "ANNONCE|" + ch.espaceId() + "|" + ch.historiqueId();
            List<UUID> crees = creerPourMembres("ANNONCE", annonce.espaceId(), cle, titre,
                    contenu(detail, annonce.projets(), "Référentiel de l'espace", ch.cle(), annonce.espaceId()), maintenant);
            envoi.envoyer(crees, maintenant);
        } catch (RuntimeException e) {
            LOG.errorf(e, "annonce du changement de standard %s en échec", annonce.cle());
        }
    }

    // ------------------------------------------------------------------ paramètres

    /** Jours de rappel de l'espace, lus sous le contexte du compte (membre). */
    public List<Integer> parametres(UUID compteId, UUID espaceId) {
        return base.executer(new Contexte(compteId, null, espaceId), c -> lireParametres(c, espaceId));
    }

    /**
     * Remplace les jours de rappel de l'espace (administrateur, RLS) : de 1 à 10 valeurs distinctes
     * entre 0 (jour de l'échéance) et 365.
     */
    public List<Integer> modifierParametres(UUID compteId, UUID espaceId, List<Integer> jours) {
        if (jours == null || jours.isEmpty() || jours.size() > 10) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 1 à 10 jours de rappel.", "rappelsJours");
        }
        Set<Integer> distincts = new LinkedHashSet<>();
        for (Integer j : jours) {
            if (j == null || j < 0 || j > 365) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque jour de rappel est compris entre 0 et 365.",
                        "rappelsJours");
            }
            distincts.add(j);
        }
        Integer[] valeurs = distincts.stream().sorted((a, b) -> b - a).toArray(Integer[]::new);
        return base.executer(new Contexte(compteId, null, espaceId), c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO radar_parametres (espace_id, rappels_jours, modifie_par, modifie_le)
                    VALUES (?, ?, argos_compte_courant(), now())
                    ON CONFLICT (espace_id) DO UPDATE SET rappels_jours = excluded.rappels_jours,
                        modifie_par = excluded.modifie_par, modifie_le = excluded.modifie_le
                    """)) {
                ps.setObject(1, espaceId);
                ps.setArray(2, c.createArrayOf("integer", valeurs));
                ps.executeUpdate();
            }
            return lireParametres(c, espaceId);
        });
    }

    private List<Integer> parametresTechniques(UUID espaceId) {
        try (Connection c = dataSource.getConnection()) {
            return lireParametres(c, espaceId);
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des paramètres du radar impossible", e);
        }
    }

    private static List<Integer> lireParametres(Connection c, UUID espaceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT rappels_jours FROM radar_parametres WHERE espace_id = ?")) {
            ps.setObject(1, espaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return RAPPELS_PAR_DEFAUT;
                }
                Array tableau = rs.getArray(1);
                return Arrays.stream((Integer[]) tableau.getArray()).toList();
            }
        }
    }

    // ------------------------------------------------------------------ messages

    private List<UUID> rappeler(RadarProjets.EcheanceProjets ep, int jours, Instant maintenant) {
        RadarProjets.EcheanceProjets e = ep;
        String quoi = switch (e.nature()) {
            case "FIN_DE_SUPPORT" -> "Fin de support de ";
            case "SORTIE" -> "Sortie de ";
            default -> "Échéance de ";
        };
        String quand = jours == 0 ? "aujourd'hui (" + e.date() + ")" : "dans " + jours + " jours (" + e.date() + ")";
        String titre = quoi + ep.libelle() + " " + e.version() + " " + quand;
        String cle = "RAPPEL|" + ep.espaceId() + "|" + e.cle() + "|" + e.nature() + "|" + e.version() + "|" + e.date()
                + "|J-" + jours;
        return creerPourMembres("RAPPEL", ep.espaceId(), cle, titre,
                contenu(titre, ep.projets(), e.source(), e.cle() + " " + e.version(), ep.espaceId()), maintenant);
    }

    private ContenuAlerte contenu(String titre, List<RadarProjets.ProjetConcerne> projets, String source,
            String identifiant, UUID espaceId) {
        List<ContenuAlerte.Projet> cites = projets.stream()
                .map(p -> new ContenuAlerte.Projet(p.id(), p.nom(), p.version() == null ? "version inconnue"
                        : "version " + p.version()))
                .toList();
        String url = "endoflife.date".equals(source) ? "https://endoflife.date" : null;
        return new ContenuAlerte(List.of(new ContenuAlerte.Element(null, titre, null, cites,
                List.of(new ContenuAlerte.Source(source, identifiant, url)),
                lienBase.replaceAll("/+$", "") + "/espaces/" + espaceId + "/radar")), null, "SANS_OBJET");
    }

    /** Un message par membre de l'espace, sur son canal ; aucun doublon pour une même clé. */
    private List<UUID> creerPourMembres(String nature, UUID espaceId, String cle, String titre, ContenuAlerte contenu,
            Instant maintenant) {
        List<UUID> crees = new ArrayList<>();
        for (Destinataires.Destinataire d : destinataires.membres(Set.of(espaceId))) {
            String canal = d.email() == null || d.email().isBlank() ? "DESKTOP" : "EMAIL";
            creer(nature, d.compteId(), canal, cle, titre, contenu, maintenant).ifPresent(id -> {
                crees.add(id);
                emises.fire(new AlerteEmise(id, d.compteId(), canal, nature));
            });
        }
        return crees;
    }

    /**
     * Enregistre un rappel ou une annonce pour un destinataire et un canal ; vide si la clé
     * d'idempotence a déjà produit un message. Partagé avec les rappels de date limite
     * ({@link RappelsOpportunites}).
     */
    Optional<UUID> creer(String nature, UUID compte, String canal, String cle, String titre, ContenuAlerte contenu,
            Instant maintenant) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO alerte (id, nature, compte_id, canal, cle_radar, titre, contenu, etat_resume_ia, etat, cree_le)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'SANS_OBJET', 'EN_ATTENTE', ?)
                ON CONFLICT (compte_id, canal, cle_radar) WHERE nature IN ('RAPPEL', 'ANNONCE') DO NOTHING
                RETURNING id
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, nature);
            ps.setObject(3, compte);
            ps.setString(4, canal);
            ps.setString(5, cleBornee(cle));
            ps.setString(6, titre);
            ps.setString(7, MoteurAlertes.json(contenu));
            ps.setObject(8, maintenant.atOffset(ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement d'un message du radar impossible", e);
        }
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
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String version(String v) {
        return v == null ? "" : " (" + v + ")";
    }
}
