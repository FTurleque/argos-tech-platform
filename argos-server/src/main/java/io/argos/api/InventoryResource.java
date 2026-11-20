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
import io.argos.api.security.Caller;
import io.argos.inventory.InventoryQueries;

/**
 * Inventaire d'un projet : composants, origine et âge (INV-02, INV-03), synthèse sans composants (INV-06, décision
 * G5 du 2026-11-19), composants inventoriés paginés (INV-06, décision F5 du 2026-11-19), imports refusés (INV-05) ;
 * dépôts du projet dans la synthèse et dans chaque composant (INV-10, décision J6 du 2026-11-19). Lecture seule :
 * aucune route n'importe un inventaire ni ne reçoit de notification de forge (INV-07-S3).
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

    /**
     * Synthèse de l'inventaire d'un projet, sans la liste des composants (décision G5 du porteur du projet,
     * 2026-11-19). {@code status} vaut {@code AVAILABLE} ou {@code NO_SBOM} ; {@code summary} est absent dans le
     * second cas.
     */
    public record ProjectInventorySummary(UUID projectId, String status, InventoryQueries.InventorySummary summary) {
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
                .map(inventaire -> new ProjectInventory(projectId, "AVAILABLE", inventaire))
                .orElseGet(() -> new ProjectInventory(projectId, "NO_SBOM", null));
    }

    @GET
    @Path("/summary")
    @Operation(summary = "Synthèse de l'inventaire de référence du projet, sans la liste des composants")
    @APIResponse(responseCode = "200", description = "Origine, dates, âge, obsolescence, nombre de composants inventoriés,"
            + " composants ignorés faute de PURL et dépôts du projet avec leur dernier import et leur dernier échec"
            + " (repositories, INV-10) ; statut NO_SBOM sans synthèse si le projet n'a pas d'inventaire")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public ProjectInventorySummary summary(@PathParam("projectId") UUID projectId) {
        Caller caller = authorize(projectId);
        return inventories.summary(projectId, caller.accountId())
                .map(synthese -> new ProjectInventorySummary(projectId, "AVAILABLE", synthese))
                .orElseGet(() -> new ProjectInventorySummary(projectId, "NO_SBOM", null));
    }

    @GET
    @Path("/components")
    @Operation(summary = "Composants inventoriés du projet, triés par nom, version puis identifiant (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page de composants : nom, version, PURL, direct ou transitif, dépôts qui"
            + " le contiennent (repositories, INV-10) ; page vide si le projet n'a pas d'inventaire")
    @APIResponse(responseCode = "400", description = "Paramètre page ou size non entier")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public InventoryQueries.ComponentPage components(@PathParam("projectId") UUID projectId,
            @Parameter(description = "Numéro de page, à partir de 0 ; une valeur négative vaut 0 ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0")) @QueryParam("page") String page,
            @Parameter(description = "Taille de page, de 1 à " + InventoryQueries.MAX_PAGE_SIZE
                    + " ; une valeur hors bornes est ramenée à la borne la plus proche ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + InventoryQueries.DEFAULT_PAGE_SIZE))
            @QueryParam("size") String size) {
        Caller caller = authorize(projectId);
        return inventories.components(projectId, caller.accountId(), ParametresPagination.entier(page, "page", 0),
                ParametresPagination.entier(size, "size", InventoryQueries.DEFAULT_PAGE_SIZE));
    }

    @GET
    @Path("/failures")
    @Operation(summary = "Imports de SBOM ou de manifestes refusés, avec leur motif")
    public List<InventoryQueries.ImportFailure> failures(@PathParam("projectId") UUID projectId) {
        Caller caller = authorize(projectId);
        return inventories.failures(projectId, caller.accountId());
    }

    private Caller authorize(UUID projectId) {
        return access.memberOfProject(() -> inventories.workspaceOf(projectId));
    }
}
