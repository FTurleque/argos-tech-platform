package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.commun.ContexteBase;
import io.argos.observabilite.ErreursImprevues;

import io.argos.catalog.standard.ReferentielStandardsEnBase;
import io.argos.catalog.standard.ReferentielStandardsEnBase.TechnologieSuivie;
import io.argos.catalog.standard.StandardModifie;
import io.argos.inventory.InventoryQueries;
import io.argos.releaseradar.CalendrierEndoflife;
import io.argos.releaseradar.CalendrierSorties;
import io.argos.releaseradar.CyclesDeVie;

/**
 * Radar appliqué aux projets (DC-1) : confronte le calendrier public aux versions réellement utilisées
 * dans les inventaires, sans IA (ADR-0013).
 *
 * <ul>
 *   <li>{@link #actualiser} synchronise les produits suivis puis crée les impacts « dette de version »
 *       (RAD-05) : composant d'un projet dont la version a atteint sa fin de support, avec la version
 *       standard de l'espace ;</li>
 *   <li>{@link #echeancesTouchant} donne les échéances d'un jour qui touchent au moins un projet de
 *       l'espace, pour les rappels (RAD-04) ;</li>
 *   <li>un changement de standard ({@link StandardModifie}) qui touche au moins un projet est relayé
 *       par {@link AnnonceStandard} (RAD-06).</li>
 * </ul>
 * Une technologie n'entre dans le radar que si le référentiel de l'espace la relie à un produit
 * endoflife.date ; seuls les projets de cet espace sont concernés.
 */
@ApplicationScoped
public class RadarProjets {

    private static final Logger LOG = Logger.getLogger(RadarProjets.class);

    /** Projet concerné et version utilisée de la technologie. */
    public record ProjetConcerne(UUID id, String nom, String version) {
    }

    /**
     * Échéance qui touche des projets d'un espace.
     *
     * @param nature {@code SORTIE} ou {@code FIN_DE_SUPPORT}
     * @param version version ou branche concernée par l'échéance
     */
    public record EcheanceProjets(UUID espaceId, String libelle, String cle, String version, String nature, LocalDate date,
            String source, List<ProjetConcerne> projets) {

        public EcheanceProjets {
            projets = List.copyOf(projets);
        }
    }

    /** Impact « dette de version » tel que le voit un membre de l'espace. */
    public record DetteVersion(UUID id, UUID projetId, String projet, String cle, String version, String cycle,
            LocalDate finSupport, String versionStandard, String source, Instant creeLe) {
    }

    /** Bilan d'une actualisation : état de chaque produit synchronisé et nombre de dettes créées. */
    public record Bilan(Map<String, String> produits, int dettesCreees) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    ReferentielStandardsEnBase referentiel;

    @Inject
    CalendrierSorties calendrier;

    @Inject
    CyclesDeVie cycles;

    @Inject
    InventoryQueries inventaires;

    @Inject
    Event<AnnonceStandard> annonces;

    /**
     * Synchronise le calendrier des produits suivis puis enregistre les dettes de version du jour. Une erreur imprévue
     * de la synchronisation n'empêche pas l'enregistrement des dettes ; elle est relancée ensuite (arc42 §8.5).
     */
    public Bilan actualiser(LocalDate aujourdhui, Instant maintenant) {
        Map<String, String> produits;
        ErreursImprevues imprevues = new ErreursImprevues();
        try {
            produits = cycles.synchroniser(referentiel.produitsSuivis(), maintenant);
        } catch (IllegalStateException erreur) {
            // La dette se calcule alors sur le calendrier déjà connu.
            LOG.errorf(erreur, "synchronisation du calendrier en échec");
            produits = Map.of();
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "synchronisation du calendrier interrompue par une erreur imprévue");
            produits = Map.of();
            imprevues.retenir(imprevue);
        }
        Bilan bilan = new Bilan(produits, enregistrerDettes(aujourdhui, maintenant));
        imprevues.relancer();
        return bilan;
    }

    /**
     * Crée les impacts « dette de version » manquants ; un impact existant n'est pas dupliqué. Une erreur
     * sur un espace est journalisée avec sa cause et n'interrompt pas les autres espaces ; la première erreur imprévue
     * est relancée une fois tous les espaces traités.
     */
    public int enregistrerDettes(LocalDate aujourdhui, Instant maintenant) {
        int creees = 0;
        List<UUID> espaces;
        try {
            espaces = referentiel.espacesSuivis();
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "lecture des espaces suivis par le radar impossible");
            return 0;
        }
        ErreursImprevues imprevues = new ErreursImprevues();
        for (UUID espace : espaces) {
            try {
                creees += enregistrerDettes(espace, aujourdhui, maintenant);
            } catch (IllegalStateException erreur) {
                LOG.errorf(erreur, "dettes de version de l'espace %s en échec", espace);
            } catch (RuntimeException imprevue) {
                LOG.errorf(imprevue, "dettes de version de l'espace %s interrompues par une erreur imprévue", espace);
                imprevues.retenir(imprevue);
            }
        }
        imprevues.relancer();
        return creees;
    }

    private int enregistrerDettes(UUID espace, LocalDate aujourdhui, Instant maintenant) {
        int creees = 0;
        Map<String, TechnologieSuivie> parCle = technologies(espace);
        for (InventoryQueries.ComponentMatch composant : composants(espace, parCle)) {
            if (composant.version() == null) {
                continue;
            }
            Optional<CalendrierSorties.Echeance> fin = calendrier.finDeSupport(composant.packageKey(), composant.version(), Set.of(espace));
            if (fin.isPresent() && !fin.get().date().isAfter(aujourdhui)) {
                creees += enregistrerDette(composant, fin.get(), parCle.get(composant.packageKey()).versionRecommandee(), maintenant);
            }
        }
        return creees;
    }

    /** Échéances (sortie, fin de support) datées du jour donné qui touchent au moins un projet de l'espace. */
    public List<EcheanceProjets> echeancesTouchant(UUID espace, LocalDate jour) {
        Map<String, TechnologieSuivie> parCle = technologies(espace);
        if (parCle.isEmpty()) {
            return List.of();
        }
        List<CalendrierSorties.Echeance> echeances = calendrier.echeances(parCle.keySet(), Set.of(espace), jour, jour)
                .stream().filter(echeance -> echeance.nature() != CalendrierSorties.Nature.CORRECTIF).toList();
        if (echeances.isEmpty()) {
            return List.of();
        }
        List<InventoryQueries.ComponentMatch> composants = composants(espace, parCle);
        Map<UUID, String> noms = nomsProjets(composants);
        List<EcheanceProjets> resultat = new ArrayList<>();
        for (CalendrierSorties.Echeance echeance : echeances) {
            List<ProjetConcerne> projets = concernes(composants, noms, echeance.cle(),
                    echeance.nature() == CalendrierSorties.Nature.FIN_DE_SUPPORT ? echeance.version() : null);
            if (!projets.isEmpty()) {
                resultat.add(new EcheanceProjets(espace, parCle.get(echeance.cle()).libelle(), echeance.cle(), echeance.version(),
                        echeance.nature().name(), echeance.date(), echeance.source(), projets));
            }
        }
        return resultat;
    }

    /** Espaces dont le référentiel relie au moins une technologie au calendrier. */
    public List<UUID> espacesSuivis() {
        return referentiel.espacesSuivis();
    }

    /** Relaie un changement de standard aux projets de l'espace qui utilisent la technologie (RAD-06). */
    void surStandardModifie(@Observes StandardModifie changement) {
        try {
            List<InventoryQueries.ComponentMatch> composants = inventaires
                    .componentsMatching(List.of(changement.cle()), Optional.empty()).stream()
                    .filter(composant -> changement.espaceId().equals(composant.workspaceId())).toList();
            List<ProjetConcerne> projets = concernes(composants, nomsProjets(composants), changement.cle(), null);
            if (!projets.isEmpty()) {
                annonces.fire(new AnnonceStandard(changement.espaceId(), changement.cle(), changement.libelle(),
                        changement.ancienStatut().name(), changement.ancienneVersion(), changement.statut().name(),
                        changement.versionRecommandee(), changement.justification(), projets, changement.historiqueId()));
            }
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "annonce du changement de standard %s en échec", changement.cle());
        }
    }

    /** Dettes de version de l'espace visibles du compte (RLS, ADR-0016). */
    public List<DetteVersion> dettes(UUID compteId, UUID espaceId) {
        try (Connection connexion = dataSource.getConnection()) {
            boolean autoCommit = connexion.getAutoCommit();
            connexion.setAutoCommit(false);
            try {
                ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(compteId));
                List<DetteVersion> liste = new ArrayList<>();
                try (PreparedStatement requete = connexion.prepareStatement("""
                        SELECT d.id, d.project_id, p.name, d.cle, d.version, d.cycle, d.fin_support_le, d.version_standard,
                               d.source, d.cree_le
                        FROM dette_version d JOIN project p ON p.id = d.project_id
                        WHERE d.workspace_id = ? ORDER BY d.fin_support_le, p.name, d.cle
                        """)) {
                    requete.setObject(1, espaceId);
                    try (ResultSet resultat = requete.executeQuery()) {
                        while (resultat.next()) {
                            liste.add(new DetteVersion(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                                    resultat.getString(3), resultat.getString(4), resultat.getString(5), resultat.getString(6),
                                    resultat.getObject(7, LocalDate.class), resultat.getString(8), resultat.getString(9),
                                    resultat.getObject(10, OffsetDateTime.class).toInstant()));
                        }
                    }
                }
                connexion.commit();
                return liste;
            } catch (SQLException | RuntimeException erreur) {
                connexion.rollback();
                throw erreur;
            } finally {
                connexion.setAutoCommit(autoCommit);
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des dettes de version impossible", erreur);
        }
    }

    // ------------------------------------------------------------------ interne

    private Map<String, TechnologieSuivie> technologies(UUID espace) {
        Map<String, TechnologieSuivie> parCle = new LinkedHashMap<>();
        referentiel.technologies(List.of(espace), true).forEach(technologie -> parCle.put(technologie.cle(), technologie));
        return parCle;
    }

    private List<InventoryQueries.ComponentMatch> composants(UUID espace, Map<String, TechnologieSuivie> parCle) {
        if (parCle.isEmpty()) {
            return List.of();
        }
        return inventaires.componentsMatching(parCle.keySet(), Optional.empty()).stream()
                .filter(composant -> espace.equals(composant.workspaceId())).toList();
    }

    /** Projets qui utilisent la clé ; pour une fin de support, seulement ceux dont la version est du cycle. */
    private static List<ProjetConcerne> concernes(List<InventoryQueries.ComponentMatch> composants, Map<UUID, String> noms,
            String cle, String cycle) {
        Map<UUID, ProjetConcerne> parProjet = new LinkedHashMap<>();
        for (InventoryQueries.ComponentMatch composant : composants) {
            if (!composant.packageKey().equals(cle)) {
                continue;
            }
            if (cycle != null && (composant.version() == null || !CalendrierEndoflife.appartient(composant.version(), cycle))) {
                continue;
            }
            parProjet.putIfAbsent(composant.projectId(), new ProjetConcerne(composant.projectId(), 
                    noms.getOrDefault(composant.projectId(), "?"), composant.version()));
        }
        return parProjet.values().stream().sorted(Comparator.comparing(ProjetConcerne::nom)).toList();
    }

    private Map<UUID, String> nomsProjets(List<InventoryQueries.ComponentMatch> composants) {
        Map<UUID, String> noms = new HashMap<>();
        List<UUID> ids = composants.stream().map(InventoryQueries.ComponentMatch::projectId).distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return noms;
        }
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT id, name FROM project WHERE id = ANY (?)")) {
            requete.setArray(1, connexion.createArrayOf("uuid", ids.toArray()));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    noms.put(resultat.getObject(1, UUID.class), resultat.getString(2));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des projets impossible", erreur);
        }
        return noms;
    }

    private int enregistrerDette(InventoryQueries.ComponentMatch composant, CalendrierSorties.Echeance fin, String versionStandard,
            Instant maintenant) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO dette_version (id, project_id, workspace_id, component_id, cle, version, cycle, fin_support_le,
                    version_standard, source, cree_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (project_id, component_id) DO NOTHING
                """)) {
            requete.setObject(1, UUID.randomUUID());
            requete.setObject(2, composant.projectId());
            requete.setObject(3, composant.workspaceId());
            requete.setObject(4, composant.componentId());
            requete.setString(5, composant.packageKey());
            requete.setString(6, composant.version());
            requete.setString(7, fin.version());
            requete.setObject(8, fin.date());
            requete.setString(9, versionStandard);
            requete.setString(10, fin.source());
            requete.setObject(11, maintenant.atOffset(ZoneOffset.UTC));
            return requete.executeUpdate();
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement de la dette de version impossible", erreur);
        }
    }
}
