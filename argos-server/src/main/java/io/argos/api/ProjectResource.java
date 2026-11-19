package io.argos.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
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

    @GET
    @Operation(summary = "Projets des espaces de l'appelant, triés par nom de projet, nom d'espace puis identifiant (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page de projets ; liste vide si l'appelant n'est membre d'aucun espace qui en contient")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public InventoryQueries.ProjectPage projects(
            @Parameter(description = "Numéro de page, à partir de 0 ; une valeur négative vaut 0") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Taille de page, de 1 à " + InventoryQueries.MAX_PAGE_SIZE + " ; une valeur hors bornes est ramenée à la borne la plus proche")
            @QueryParam("size") @DefaultValue("" + InventoryQueries.DEFAULT_PAGE_SIZE) int size) {
        Caller caller = access.authenticated();
        return inventories.projects(caller.accountId(), page, size);
    }
}
