package io.argos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.argos.conservation.RegleConservation;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Complétude de la conservation des données (EXP-17, ADR-0041) : chaque table du schéma est classée, soit parmi les
 * tables de journal, d'échec ou d'historique, avec la règle qui la purge ou la raison de son exclusion, soit parmi les
 * données de référence ou de travail. Une table ajoutée par une migration sans être classée fait échouer le test.
 */
@QuarkusTest
class ConservationCompleteTest {

    private static final String EXCLUE = "exclue : ";

    /** Tables de journal, d'échec ou d'historique : règle de conservation, ou raison de l'exclusion. */
    static final Map<String, String> JOURNAUX_ET_HISTORIQUES = Map.ofEntries(
            Map.entry("refus_acces", "refus-acces"),
            Map.entry("mcp_appel", "appels-mcp"),
            Map.entry("ia_appel", "appels-ia"),
            Map.entry("ia_quota_jour", "quotas-ia"),
            Map.entry("sync_run", "synchronisations"),
            Map.entry("sync_attempt", "synchronisations"),
            Map.entry("ingestion_failure", "avis-mis-a-l-ecart"),
            Map.entry("ops_event", "evenements-exploitation"),
            Map.entry("inventory_import_failure", "echecs-import"),
            Map.entry("actualite", "actualites"),
            Map.entry("actualite_source", "actualites"),
            Map.entry("actualite_impact", "actualites"),
            Map.entry("opportunite", "opportunites"),
            Map.entry("activite_compteur", EXCLUE + "purgée par PurgeActivite (13 mois)"),
            Map.entry("activite_jalon", EXCLUE + "purgée par PurgeActivite (13 mois)"),
            Map.entry("signal_version", EXCLUE + "COL-03-S2 : la version précédente reste consultable"),
            Map.entry("alerte", EXCLUE + "la ligne porte l'idempotence d'une alerte immédiate (alerte_immediate_uk)"),
            Map.entry("resume_element", EXCLUE + "élément en attente ou lié à une alerte conservée"),
            Map.entry("project_impact_transition", EXCLUE + "historique métier supprimé avec son impact"),
            Map.entry("standard_historique", EXCLUE + "historique métier supprimé avec son standard"),
            Map.entry("fiche_historique", EXCLUE + "historique métier supprimé avec sa fiche"),
            Map.entry("reg_collecte_jour", EXCLUE + "état de collecte d'un jour, rejouable par l'administrateur"));

    /** Données de référence, de configuration ou de travail : leur taille ne croît pas avec l'historique seul. */
    static final Set<String> DONNEES_DE_REFERENCE = Set.of("flyway_schema_history", "compte", "espace", "appartenance",
            "invitation", "source", "sync_cursor", "signal", "signal_alias", "signal_package", "enrichment",
            "enrichissement_attente", "project", "inventory", "component", "component_occurrence", "project_repository",
            "repository_inventory", "project_impact", "subscription", "subscription_opt_out",
            "workspace_preference_utilisateur", "workspace_vue", "source_web", "standard_technologie",
            "standard_suggestion", "radar_parametres", "cycle_vie_produit", "cycle_vie", "dette_version", "ia_prompt",
            "activite_collecte", "suivi_profil", "suivi_rappels", "source_profil", "domaine_metier", "expert_domaine",
            "regle_dictionnaire", "reg_parametres", "texte_officiel", "reg_source", "fiche_reglementaire", "fiche_domaine");

    @Inject
    TestDb baseDeTest;

    @Inject
    @Any
    Instance<RegleConservation> regles;

    @Test
    void chaqueTableDuSchemaEstClassee() {
        Set<String> classees = new TreeSet<>(JOURNAUX_ET_HISTORIQUES.keySet());
        classees.addAll(DONNEES_DE_REFERENCE);
        assertEquals(new TreeSet<>(baseDeTest.tablesDuSchema()), classees,
                "table ni classée parmi les journaux et historiques, ni parmi les données de référence (ou classée sans exister)");
        Set<String> communes = new HashSet<>(JOURNAUX_ET_HISTORIQUES.keySet());
        communes.retainAll(DONNEES_DE_REFERENCE);
        assertEquals(Set.of(), communes, "table classée deux fois");
    }

    @Test
    void chaqueRegleCiteeEstDeclareeEtChaqueRegleDeclareeEstCitee() {
        Set<String> declarees = new TreeSet<>();
        regles.forEach(regle -> declarees.add(regle.nom()));
        Set<String> citees = new TreeSet<>();
        JOURNAUX_ET_HISTORIQUES.values().stream().filter(valeur -> !valeur.startsWith(EXCLUE)).forEach(citees::add);
        assertEquals(declarees, citees);
    }
}
