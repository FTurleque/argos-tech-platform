package io.argos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Propriété des tables et lectures croisées (invariant NC-5, ADR-0037, arc42 §5.3) : chaque table et chaque vue
 * des migrations a un module propriétaire ; le SQL d'un module ne cite que ses tables, les tables d'un module
 * dont il peut dépendre ({@link ModuleBoundariesTest#ALLOWED}) ou une vue publiée par leur propriétaire. Les
 * préférences d'un utilisateur ne se lisent que par le service de {@code workspace}.
 *
 * <p>ArchUnit ne lit pas le SQL : le test parcourt les sources de production et repère les noms de table après
 * {@code FROM}, {@code JOIN}, {@code INTO} et {@code UPDATE}. Un SQL construit dynamiquement lui échappe.
 */
class ProprieteTablesTest {

    private static final Path SOURCES = Path.of("src/main/java/io/argos");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /** Module propriétaire → tables et vues qu'il crée (migrations V1 à V23). */
    static final Map<String, Set<String>> PROPRIETAIRES = Map.ofEntries(
            Map.entry("workspace", Set.of("appartenance", "compte", "espace", "invitation", "subscription",
                    "subscription_opt_out", "suivi_profil", "suivi_rappels", "workspace_preference_utilisateur",
                    "workspace_vue", "compte_nom_public")),
            Map.entry("catalog", Set.of("domaine_metier", "expert_domaine", "regle_dictionnaire", "standard_historique",
                    "standard_suggestion", "standard_technologie")),
            Map.entry("inventory", Set.of("component", "component_occurrence", "inventory", "inventory_import_failure",
                    "project", "project_repository", "repository_inventory")),
            Map.entry("signal", Set.of("actualite", "actualite_source", "opportunite", "signal", "signal_alias",
                    "signal_package", "signal_version", "source_profil", "source_web")),
            Map.entry("vulnintel", Set.of("enrichment")),
            Map.entry("releaseradar", Set.of("cycle_vie", "cycle_vie_produit")),
            Map.entry("regintel", Set.of("fiche_domaine", "fiche_historique", "fiche_reglementaire", "reg_collecte_jour",
                    "reg_parametres", "reg_source", "texte_officiel")),
            Map.entry("impact", Set.of("actualite_impact", "dette_version", "project_impact", "project_impact_transition")),
            Map.entry("alerting", Set.of("alerte", "radar_parametres", "resume_element")),
            Map.entry("projectintel", Set.of("activite_collecte", "activite_compteur", "activite_jalon")),
            Map.entry("aigateway", Set.of("ia_prompt", "ia_quota_jour")),
            Map.entry("audit", Set.of("ia_appel", "mcp_appel", "refus_acces")),
            Map.entry("ingestion", Set.of("ingestion_failure", "ops_event", "source", "sync_attempt", "sync_cursor",
                    "sync_run")));

    /** Vues publiées par leur propriétaire, que tout module peut lire. */
    static final Set<String> VUES_PUBLIEES = Set.of("compte_nom_public");

    /** Tables que seul leur propriétaire cite : les autres modules passent par son service de lecture. */
    static final Set<String> LECTURE_PAR_SERVICE = Set.of("workspace_preference_utilisateur");

    private static final Pattern CITATION = Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE)\\s+(?:ONLY\\s+)?([a-z_]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATION = Pattern.compile("^CREATE\\s+(?:TABLE|VIEW)\\s+(?:IF NOT EXISTS\\s+)?([a-z_0-9]+)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static Map<String, String> proprietaireDe() {
        Map<String, String> proprietaire = new HashMap<>();
        PROPRIETAIRES.forEach((module, tables) -> tables.forEach(table -> proprietaire.put(table, module)));
        return proprietaire;
    }

    /** Citations interdites : « module cite table (propriétaire) » pour chaque fichier Java de {@code racine}. */
    static List<String> citationsInterdites(Path racine, Map<String, Set<String>> dependances) throws IOException {
        Map<String, String> proprietaire = proprietaireDe();
        List<String> interdites = new ArrayList<>();
        try (Stream<Path> fichiers = Files.walk(racine)) {
            for (Path fichier : fichiers.filter(chemin -> chemin.toString().endsWith(".java")).toList()) {
                String module = racine.relativize(fichier).getName(0).toString();
                Matcher citation = CITATION.matcher(lire(fichier));
                while (citation.find()) {
                    String table = citation.group(1).toLowerCase(java.util.Locale.ROOT);
                    String possesseur = proprietaire.get(table);
                    if (possesseur != null && !module.equals(possesseur)
                            && !lectureAutorisee(module, table, possesseur, dependances)) {
                        interdites.add(module + " cite " + table + " (" + possesseur + ") dans " + fichier.getFileName());
                    }
                }
            }
        }
        return interdites.stream().distinct().sorted().toList();
    }

    private static boolean lectureAutorisee(String module, String table, String possesseur,
            Map<String, Set<String>> dependances) {
        if (LECTURE_PAR_SERVICE.contains(table)) {
            return false;
        }
        return VUES_PUBLIEES.contains(table) || dependances.getOrDefault(module, Set.of()).contains(possesseur)
                || ModuleBoundariesTest.TRANSVERSES.contains(possesseur);
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    @Test
    void chaqueModuleNeCiteQueSesTablesOuCellesDeSesDependances() throws IOException {
        assertEquals(List.of(), citationsInterdites(SOURCES, ModuleBoundariesTest.ALLOWED),
                "NC-5 : lecture d'une table dont le module n'est ni propriétaire ni dépendant du propriétaire");
    }

    @Test
    void chaqueTableOuVueDesMigrationsAUnProprietaire() throws IOException {
        Set<String> inconnues = new TreeSet<>();
        Map<String, String> proprietaire = proprietaireDe();
        try (Stream<Path> migrations = Files.list(MIGRATIONS)) {
            for (Path migration : migrations.filter(chemin -> chemin.toString().endsWith(".sql")).toList()) {
                Matcher creation = CREATION.matcher(lire(migration));
                while (creation.find()) {
                    if (!proprietaire.containsKey(creation.group(1).toLowerCase(java.util.Locale.ROOT))) {
                        inconnues.add(creation.group(1));
                    }
                }
            }
        }
        assertEquals(Set.of(), inconnues, "NC-5 : table ou vue sans module propriétaire dans PROPRIETAIRES");
    }

    @Test
    void chaqueProprietaireEstUnModuleDeclare() {
        assertTrue(ModuleBoundariesTest.ALLOWED.keySet().containsAll(PROPRIETAIRES.keySet()));
    }

    /** La règle repère la lecture d'une table étrangère hors dépendance et celle d'une préférence. */
    @Test
    void laRegleDetecteUneCitationEtrangere(@TempDir Path racine) throws IOException {
        Path module = Files.createDirectories(racine.resolve("signal"));
        Files.writeString(module.resolve("Lecteur.java"),
                "String sql = \"SELECT 1 FROM project_impact JOIN workspace_preference_utilisateur p ON true\";");
        Files.writeString(Files.createDirectories(racine.resolve("signal")).resolve("Permise.java"),
                "String sql = \"SELECT 1 FROM compte_nom_public\";");
        assertEquals(List.of("signal cite project_impact (impact) dans Lecteur.java",
                "signal cite workspace_preference_utilisateur (workspace) dans Lecteur.java"),
                citationsInterdites(racine, Map.of("signal", Set.of())));
    }
}
