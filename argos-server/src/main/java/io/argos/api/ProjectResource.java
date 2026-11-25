package io.argos.api;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.commun.securite.Caller;
import io.argos.impact.ImpactQueries;
import io.argos.inventory.InventoryQueries;

/**
 * Projets visibles par l'appelant (INV-06 ; décision F1 du porteur du projet, 2026-11-19) : liste paginée des
 * projets des espaces dont il est membre, lue sous la RLS de {@code project} (ADR-0016).
 */
@Path("/api/v1/projects")
@Tag(name = "Inventaires")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    Access access;

    @Inject
    InventoryQueries inventories;

    @Inject
    ImpactQueries impacts;

    @GET
    @Operation(summary = "Projets des espaces de l'appelant, triés par nom de projet, nom d'espace puis identifiant (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page de projets ; liste vide si l'appelant n'est membre d'aucun espace qui en contient")
    @APIResponse(responseCode = "400", description = "Paramètre page ou size non entier")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public InventoryQueries.ProjectPage projects(
            @Parameter(description = "Numéro de page, à partir de 0 ; une valeur négative vaut 0 ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0")) @QueryParam("page") String page,
            @Parameter(description = "Taille de page, de 1 à " + InventoryQueries.MAX_PAGE_SIZE
                    + " ; une valeur hors bornes est ramenée à la borne la plus proche ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + InventoryQueries.DEFAULT_PAGE_SIZE))
            @QueryParam("size") String size) {
        Caller caller = access.authenticated();
        return inventories.projects(caller.accountId(), ParametresPagination.entier(page, "page", 0),
                ParametresPagination.entier(size, "size", InventoryQueries.DEFAULT_PAGE_SIZE));
    }

    @GET
    @Path("/{projectId}/impacts")
    @Operation(summary = "Impacts du projet filtrés par état de traitement (COR-12-S2), triés par date de création")
    @APIResponse(responseCode = "200", description = "Impacts du projet aux états demandés ; par défaut OUVERT et CONFIRME")
    @APIResponse(responseCode = "400", description = "État inconnu (DONNEE_INVALIDE)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public List<ImpactQueries.ImpactView> impacts(@PathParam("projectId") UUID projectId,
            @Parameter(description = "États de traitement retenus, paramètre répétable (OUVERT, CONFIRME, ECARTE, RESOLU) ;"
                    + " absent : OUVERT et CONFIRME ; une valeur inconnue donne 400")
            @QueryParam("etat") List<String> etat) {
        Caller caller = access.memberOfProject(() -> inventories.workspaceOf(projectId));
        return impacts.forProject(projectId, caller.accountId(), caller.memberWorkspaces(), SignalResource.etats(etat));
    }
}
