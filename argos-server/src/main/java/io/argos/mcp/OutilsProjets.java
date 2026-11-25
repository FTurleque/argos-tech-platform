package io.argos.mcp;

import static io.argos.mcp.OutilsSecurite.parametres;
import static io.argos.mcp.OutilsSecurite.texte;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import io.argos.commun.securite.Caller;
import io.argos.impact.ImpactQueries;
import io.argos.inventory.InventoryQueries;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

/** Outil MCP {@code get_project_context} : contexte des projets de l'appelant, sous RLS (MCP-04). */
public class OutilsProjets {

    private static final int COMPOSANTS_MAX = 500;
    private static final int PROJETS_MAX = 50;

    /** Composant de l'inventaire de référence. */
    public record Composant(String purl, String version, Boolean transitive) {
    }

    /** Inventaire de référence d'un projet. */
    public record Inventaire(String origine, String dateSource, Long ageJours, boolean obsolete, int nombreComposants) {
    }

    /** Contexte d'un projet de l'appelant. */
    public record ContexteProjet(UUID projetId, String projet, UUID espaceId, Inventaire inventaire,
            Map<String, Long> alertesParNiveau, List<Composant> composants, boolean composantsTronques) {
    }

    /** Réponse de {@code get_project_context}. */
    public record ContexteProjets(int nombre, List<ContexteProjet> projets) {
    }

    @Inject
    GardeAppels garde;

    @Inject
    InventoryQueries inventaires;

    @Inject
    ImpactQueries impacts;

    @Tool(name = "get_project_context", structuredContent = true,
            annotations = @Tool.Annotations(title = "Contexte des projets", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = "Contexte des projets suivis par l'utilisateur : inventaire de référence (SBOM ou manifestes,"
                    + " âge, obsolescence) et alertes par niveau. Avec project_id, détaille un projet et ses composants ;"
                    + " sans, résume les projets visibles. Un projet hors du périmètre de l'utilisateur est signalé comme"
                    + " introuvable.")
    public ContexteProjets getProjectContext(
            @ToolArg(name = "project_id", description = "Identifiant du projet (UUID), facultatif.", required = false) String projet) {
        return garde.appeler("get_project_context", parametres("project_id", projet), appelant -> {
            Optional<UUID> id = Parametres.identifiantFacultatif("project_id", projet);
            if (id.isPresent()) {
                OutilsSecurite.exigerProjetVisible(appelant, id.get(), inventaires);
                List<ContexteProjet> projetDemande = ProjetsVisibles.duCompte(appelant.accountId(), inventaires)
                        .filter(visible -> visible.id().equals(id.get())).limit(1).map(visible -> detail(appelant, visible)).toList();
                return new ContexteProjets(projetDemande.size(), projetDemande);
            }
            List<ContexteProjet> projets = ProjetsVisibles.duCompte(appelant.accountId(), inventaires)
                    .filter(visible -> appelant.memberOf(visible.workspaceId())).limit(PROJETS_MAX)
                    .map(visible -> resume(appelant, visible)).toList();
            return new ContexteProjets(projets.size(), projets);
        });
    }

    /** Contexte d'un projet avec ses composants (au plus {@value #COMPOSANTS_MAX}). */
    private ContexteProjet detail(Caller appelant, InventoryQueries.ProjectItem projet) {
        Optional<InventoryQueries.InventoryView> inventaire = inventaires.inventory(projet.id(), appelant.accountId());
        List<InventoryQueries.ComponentView> composants = inventaire.map(InventoryQueries.InventoryView::components)
                .orElse(List.of());
        return new ContexteProjet(projet.id(), projet.name(), projet.workspaceId(),
                inventaire.map(vue -> new Inventaire(vue.origin(), texte(vue.sourceDate()), vue.ageDays(), vue.obsolete(),
                        vue.components().size())).orElse(null),
                alertesParNiveau(appelant, projet),
                composants.stream().limit(COMPOSANTS_MAX)
                        .map(composant -> new Composant(composant.purl(), composant.version(), composant.transitive())).toList(),
                composants.size() > COMPOSANTS_MAX);
    }

    /** Contexte d'un projet sans ses composants : synthèse de l'inventaire, sans lecture des composants (G5). */
    private ContexteProjet resume(Caller appelant, InventoryQueries.ProjectItem projet) {
        Optional<InventoryQueries.InventorySummary> synthese = inventaires.summary(projet.id(), appelant.accountId());
        return new ContexteProjet(projet.id(), projet.name(), projet.workspaceId(),
                synthese.map(bilan -> new Inventaire(bilan.origin(), texte(bilan.sourceDate()), bilan.ageDays(), bilan.obsolete(),
                        Math.toIntExact(bilan.componentCount()))).orElse(null),
                alertesParNiveau(appelant, projet), List.of(), false);
    }

    private Map<String, Long> alertesParNiveau(Caller appelant, InventoryQueries.ProjectItem projet) {
        return impacts.forProject(projet.id(), appelant.accountId(), appelant.memberWorkspaces()).stream()
                .collect(Collectors.groupingBy(ImpactQueries.ImpactView::level, TreeMap::new, Collectors.counting()));
    }
}
