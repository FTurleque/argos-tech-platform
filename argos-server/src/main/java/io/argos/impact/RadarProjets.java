package io.argos.impact;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.commun.ContexteBase;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;
import io.argos.execution.PublicationAsynchrone;
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
 *   <li>{@link #echeancesTouchant} donne les échéances des jours de rappel qui touchent au moins un projet
 *       de l'espace (RAD-04) ;</li>
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
    Jdbc jdbc;

    @Inject
    ContexteBase contexteBase;

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

    @Inject
    PublicationAsynchrone publication;

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

    /**
     * Dettes de version d'un espace, en un nombre fixe de requêtes (RAD-10) : technologies suivies, composants de
     * l'espace qui les utilisent, calendrier de leurs produits, puis une insertion pour toutes les dettes du jour.
     */
    private int enregistrerDettes(UUID espace, LocalDate aujourdhui, Instant maintenant) {
        Map<String, TechnologieSuivie> parCle = technologies(espace);
        List<InventoryQueries.ComponentMatch> composants = composants(espace, parCle);
        if (composants.isEmpty()) {
            return 0;
        }
        CalendrierSorties.FinsDeSupport fins = calendrier.finsDeSupport(parCle.keySet(), Set.of(espace));
        List<Dette> dettes = new ArrayList<>();
        for (InventoryQueries.ComponentMatch composant : composants) {
            if (composant.version() == null) {
                continue;
            }
            Optional<CalendrierSorties.Echeance> fin = fins.pour(composant.packageKey(), composant.version());
            if (fin.isPresent() && !fin.get().date().isAfter(aujourdhui)) {
                dettes.add(new Dette(composant, fin.get(), parCle.get(composant.packageKey()).versionRecommandee()));
            }
        }
        return enregistrerDettes(dettes, maintenant);
    }

    /**
     * Échéances (sortie, fin de support) datées de chacun des jours donnés qui touchent au moins un projet de l'espace,
     * en une seule lecture du calendrier et des composants de l'espace (RAD-10-S2).
     *
     * @return échéances par jour ; un jour sans échéance est absent
     */
    public Map<LocalDate, List<EcheanceProjets>> echeancesTouchant(UUID espace, Collection<LocalDate> jours) {
        Map<String, TechnologieSuivie> parCle = technologies(espace);
        if (parCle.isEmpty() || jours.isEmpty()) {
            return Map.of();
        }
        Set<LocalDate> demandes = new HashSet<>(jours);
        LocalDate depuis = demandes.stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate jusqua = demandes.stream().max(Comparator.naturalOrder()).orElseThrow();
        List<CalendrierSorties.Echeance> echeances = calendrier.echeances(parCle.keySet(), Set.of(espace), depuis, jusqua)
                .stream().filter(echeance -> echeance.nature() != CalendrierSorties.Nature.CORRECTIF)
                .filter(echeance -> demandes.contains(echeance.date())).toList();
        if (echeances.isEmpty()) {
            return Map.of();
        }
        List<InventoryQueries.ComponentMatch> composants = composants(espace, parCle);
        Map<UUID, String> noms = nomsProjets(composants);
        Map<LocalDate, List<EcheanceProjets>> resultat = new LinkedHashMap<>();
        for (CalendrierSorties.Echeance echeance : echeances) {
            List<ProjetConcerne> projets = concernes(composants, noms, echeance.cle(),
                    echeance.nature() == CalendrierSorties.Nature.FIN_DE_SUPPORT ? echeance.version() : null);
            if (!projets.isEmpty()) {
                resultat.computeIfAbsent(echeance.date(), date -> new ArrayList<>()).add(new EcheanceProjets(espace,
                        parCle.get(echeance.cle()).libelle(), echeance.cle(), echeance.version(), echeance.nature().name(),
                        echeance.date(), echeance.source(), projets));
            }
        }
        return resultat;
    }

    /** Espaces dont le référentiel relie au moins une technologie au calendrier. */
    public List<UUID> espacesSuivis() {
        return referentiel.espacesSuivis();
    }

    /**
     * Relaie un changement de standard aux projets de l'espace qui utilisent la technologie (RAD-06), hors de la
     * requête qui l'a enregistré (RAD-07-S4, ADR-0039).
     */
    void surStandardModifie(@ObservesAsync StandardModifie changement) {
        try {
            List<InventoryQueries.ComponentMatch> composants = inventaires
                    .componentsMatching(List.of(changement.cle()), Optional.empty(), Optional.of(changement.espaceId()));
            List<ProjetConcerne> projets = concernes(composants, nomsProjets(composants), changement.cle(), null);
            if (!projets.isEmpty()) {
                publication.publier(annonces, new AnnonceStandard(changement.espaceId(), changement.cle(), changement.libelle(),
                        changement.ancienStatut().name(), changement.ancienneVersion(), changement.statut().name(),
                        changement.versionRecommandee(), changement.justification(), projets, changement.historiqueId()));
            }
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "annonce du changement de standard %s en échec", changement.cle());
        }
    }

    /** Dettes de version de l'espace visibles du compte (RLS, ADR-0016). */
    public List<DetteVersion> dettes(UUID compteId, UUID espaceId) {
        return contexteBase.executer(ContexteBase.Contexte.duCompte(compteId), connexion -> Jdbc.lister(connexion, """
                SELECT d.id, d.project_id, p.name, d.cle, d.version, d.cycle, d.fin_support_le, d.version_standard,
                       d.source, d.cree_le
                FROM dette_version d JOIN project p ON p.id = d.project_id
                WHERE d.workspace_id = ? ORDER BY d.fin_support_le, p.name, d.cle
                """, Jdbc.parametres(espaceId), resultat -> new DetteVersion(resultat.getObject(1, UUID.class),
                resultat.getObject(2, UUID.class), resultat.getString(3), resultat.getString(4), resultat.getString(5),
                resultat.getString(6), resultat.getObject(7, LocalDate.class), resultat.getString(8),
                resultat.getString(9), Conversions.instant(resultat, 10))));
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
        // Filtre d'espace en base : les composants des autres espaces ne sont jamais lus (RAD-10-S1).
        return inventaires.componentsMatching(parCle.keySet(), Optional.empty(), Optional.of(espace));
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
        jdbc.lister("SELECT id, name FROM project WHERE id = ANY (?)",
                requete -> requete.setArray(1, requete.getConnection().createArrayOf("uuid", ids.toArray())),
                resultat -> Map.entry(resultat.getObject(1, UUID.class), resultat.getString(2)))
                .forEach(ligne -> noms.put(ligne.getKey(), ligne.getValue()));
        return noms;
    }

    /** Dette de version à enregistrer : composant, fin de support atteinte et version standard de l'espace. */
    private record Dette(InventoryQueries.ComponentMatch composant, CalendrierSorties.Echeance fin, String versionStandard) {
    }

    /** Enregistre les dettes en une instruction ; une dette existante n'est pas dupliquée. */
    private int enregistrerDettes(List<Dette> dettes, Instant maintenant) {
        if (dettes.isEmpty()) {
            return 0;
        }
        return jdbc.modifier("""
                INSERT INTO dette_version (id, project_id, workspace_id, component_id, cle, version, cycle, fin_support_le,
                    version_standard, source, cree_le)
                SELECT gen_random_uuid(), d.project_id, d.workspace_id, d.component_id, d.cle, d.version, d.cycle,
                       d.fin_support_le::date, d.version_standard, d.source, ?
                FROM unnest(?::uuid[], ?::uuid[], ?::uuid[], ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[],
                            ?::varchar[], ?::varchar[])
                     AS d(project_id, workspace_id, component_id, cle, version, cycle, fin_support_le, version_standard, source)
                ON CONFLICT (project_id, component_id) DO NOTHING
                """, requete -> {
                    requete.setObject(1, Conversions.utc(maintenant));
                    requete.setArray(2, tableau(requete, "uuid", dettes, dette -> dette.composant().projectId()));
                    requete.setArray(3, tableau(requete, "uuid", dettes, dette -> dette.composant().workspaceId()));
                    requete.setArray(4, tableau(requete, "uuid", dettes, dette -> dette.composant().componentId()));
                    requete.setArray(5, tableau(requete, "varchar", dettes, dette -> dette.composant().packageKey()));
                    requete.setArray(6, tableau(requete, "varchar", dettes, dette -> dette.composant().version()));
                    requete.setArray(7, tableau(requete, "varchar", dettes, dette -> dette.fin().version()));
                    requete.setArray(8, tableau(requete, "varchar", dettes, dette -> dette.fin().date().toString()));
                    requete.setArray(9, tableau(requete, "varchar", dettes, Dette::versionStandard));
                    requete.setArray(10, tableau(requete, "varchar", dettes, dette -> dette.fin().source()));
                });
    }

    private static Array tableau(PreparedStatement requete, String type, List<Dette> dettes,
            Function<Dette, Object> valeur) throws SQLException {
        return requete.getConnection().createArrayOf(type, dettes.stream().map(valeur).toArray());
    }
}
