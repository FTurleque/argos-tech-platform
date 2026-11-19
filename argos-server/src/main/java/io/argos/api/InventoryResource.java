package io.argos.api;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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
 * Inventaire d'un projet : composants, origine et âge (INV-02, INV-03), composants inventoriés paginés (INV-06,
 * décision F5 du 2026-11-19), imports refusés (INV-05).
 */
@Path("/api/v1/projects/{projectId}/inventory")
@Tag(name = "Inventaires")
@Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {

    /**
     * Inventaire d'un projet. {@code status} vaut {@code AVAILABLE} ou {@code NO_SBOM} (aucun inventaire,
     * INV-03-S2) ; {@code inventory} est absent dans le second cas.
     */
    public record ProjectInventory(UUID projectId, String status, InventoryQueries.InventoryView inventory) {
    }

    @Inject
    Access access;

    @Inject
    InventoryQueries inventories;

    @GET
    @Operation(summary = "Inventaire de référence du projet, son origine et l'âge de la SBOM")
    public ProjectInventory inventory(@PathParam("projectId") UUID projectId) {
        Caller caller = authorize(projectId);
        return inventories.inventory(projectId, caller.accountId())
                .map(i -> new ProjectInventory(projectId, "AVAILABLE", i))
                .orElseGet(() -> new ProjectInventory(projectId, "NO_SBOM", null));
    }

    @GET
    @Path("/components")
    @Operation(summary = "Composants inventoriés du projet, triés par nom, version puis identifiant (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page de composants : nom, version, PURL, direct ou transitif ; page vide si le projet n'a pas d'inventaire")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public InventoryQueries.ComponentPage components(@PathParam("projectId") UUID projectId,
            @Parameter(description = "Numéro de page, à partir de 0 ; une valeur négative vaut 0") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Taille de page, de 1 à " + InventoryQueries.MAX_PAGE_SIZE + " ; une valeur hors bornes est ramenée à la borne la plus proche")
            @QueryParam("size") @DefaultValue("" + InventoryQueries.DEFAULT_PAGE_SIZE) int size) {
        Caller caller = authorize(projectId);
        return inventories.components(projectId, caller.accountId(), page, size);
    }

    @GET
    @Path("/failures")
    @Operation(summary = "Imports de SBOM ou de manifestes refusés, avec leur motif")
    public List<InventoryQueries.ImportFailure> failures(@PathParam("projectId") UUID projectId) {
        Caller caller = authorize(projectId);
        return inventories.failures(projectId, caller.accountId());
    }

    private Caller authorize(UUID projectId) {
        Caller caller = access.authenticated();
        UUID workspace = inventories.workspaceOf(projectId).orElseThrow(NotFoundException::new);
        if (!caller.memberOf(workspace)) {
            throw access.outOfScope();
        }
        return caller;
    }
}
