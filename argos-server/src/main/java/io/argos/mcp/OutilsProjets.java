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

import io.argos.api.security.Caller;
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
                List<ContexteProjet> un = inventaires.visibleProjects(appelant.accountId()).stream()
                        .filter(p -> p.id().equals(id.get())).map(p -> contexte(appelant, p, true)).toList();
                return new ContexteProjets(un.size(), un);
            }
            List<ContexteProjet> tous = inventaires.visibleProjects(appelant.accountId()).stream()
                    .filter(p -> appelant.memberOf(p.workspaceId())).limit(PROJETS_MAX)
                    .map(p -> contexte(appelant, p, false)).toList();
            return new ContexteProjets(tous.size(), tous);
        });
    }

    private ContexteProjet contexte(Caller appelant, InventoryQueries.ProjectSummary p, boolean avecComposants) {
        Optional<InventoryQueries.InventoryView> inventaire = inventaires.inventory(p.id(), appelant.accountId());
        Map<String, Long> niveaux = impacts.forProject(p.id(), appelant.accountId(), appelant.memberWorkspaces()).stream()
                .collect(Collectors.groupingBy(ImpactQueries.ImpactView::level, TreeMap::new, Collectors.counting()));
        List<InventoryQueries.ComponentView> composants = inventaire.map(InventoryQueries.InventoryView::components)
                .orElse(List.of());
        return new ContexteProjet(p.id(), p.name(), p.workspaceId(),
                inventaire.map(i -> new Inventaire(i.origin(), texte(i.sourceDate()), i.ageDays(), i.obsolete(),
                        i.components().size())).orElse(null),
                niveaux,
                avecComposants ? composants.stream().limit(COMPOSANTS_MAX)
                        .map(c -> new Composant(c.purl(), c.version(), c.transitive())).toList() : List.of(),
                avecComposants && composants.size() > COMPOSANTS_MAX);
    }
}
