package io.argos.api;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
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
import org.jboss.resteasy.reactive.RestResponse;

import io.argos.api.security.Access;
import io.argos.commun.Pagination;
import io.argos.commun.securite.Caller;
import io.argos.impact.ImpactQueries;
import io.argos.inventory.DepotProjet;
import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;

/**
 * Projets visibles par l'appelant (INV-06 ; décision F1 du porteur du projet, 2026-11-19) : liste paginée des
 * projets des espaces dont il est membre, lue sous la RLS de {@code project} (ADR-0016). Création d'un projet,
 * rattachement et détachement de ses dépôts par l'administrateur de l'espace (INV-12 ; décision du porteur du
 * projet, 2026-11-26) : le rôle est contrôlé ici, l'écriture est faite par le module {@code inventory}, comme
 * l'import planifié.
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

    @Inject
    ProjectDirectory projets;

    @Inject
    InventoryService inventaire;

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
    @Operation(summary = "Impacts du projet filtrés par état de traitement (COR-12-S2), triés par date de création puis"
            + " identifiant (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page d'impacts du projet aux états demandés ; par défaut OUVERT et CONFIRME")
    @APIResponse(responseCode = "400", description = "État inconnu, ou paramètre page ou size non entier (DONNEE_INVALIDE)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public ImpactQueries.Page impacts(@PathParam("projectId") UUID projectId,
            @Parameter(description = "États de traitement retenus, paramètre répétable (OUVERT, CONFIRME, ECARTE, RESOLU) ;"
                    + " absent : OUVERT et CONFIRME ; une valeur inconnue donne 400")
            @QueryParam("etat") List<String> etat,
            @Parameter(description = ParametresPagination.DESCRIPTION_PAGE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam(ParametresPagination.PAGE) String page,
            @Parameter(description = ParametresPagination.DESCRIPTION_TAILLE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + Pagination.TAILLE_DEFAUT))
            @QueryParam(ParametresPagination.TAILLE) String size) {
        Pagination pagination = ParametresPagination.pagination(page, size);
        Caller caller = access.memberOfProject(() -> inventories.workspaceOf(projectId));
        return impacts.forProject(projectId, caller.accountId(), caller.memberWorkspaces(), SignalResource.etats(etat), pagination);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Créer un projet dans un espace, avec son premier dépôt facultatif (INV-12)")
    @APIResponse(responseCode = "201", description = "Projet créé ; le dépôt donné devient son dépôt principal")
    @APIResponse(responseCode = "400", description = "Saisie invalide (DONNEE_INVALIDE, attribut en cause)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Appelant non administrateur de l'espace (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Espace dont l'appelant n'est pas membre (refus journalisé)")
    public RestResponse<SaisieProjet.ProjetCree> creer(SaisieProjet.NouveauProjet demande) {
        UUID espace = SaisieProjet.espace(demande);
        access.administrator(espace);
        String nom = SaisieProjet.nom(demande);
        Optional<DepotProjet> depot = Optional.ofNullable(demande.depot()).map(SaisieProjet::depot);
        UUID id = projets.creer(espace, nom, depot);
        return RestResponse.ResponseBuilder.<SaisieProjet.ProjetCree>created(URI.create("/api/v1/projects/" + id))
                .entity(new SaisieProjet.ProjetCree(id, espace, nom)).build();
    }

    @POST
    @Path("/{projectId}/repositories")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rattacher un dépôt GitHub ou GitLab au projet (INV-12)")
    @APIResponse(responseCode = "201", description = "Dépôt rattaché ; principal si le projet n'en avait pas")
    @APIResponse(responseCode = "400", description = "Saisie invalide (DONNEE_INVALIDE, attribut en cause)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Appelant non administrateur de l'espace du projet (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre")
    @APIResponse(responseCode = "409", description = "Dépôt déjà rattaché au projet (DEPOT_DEJA_RATTACHE)")
    public RestResponse<SaisieProjet.DepotRattache> rattacher(@PathParam("projectId") UUID projectId, SaisieProjet.Depot depot) {
        access.administrator(espaceDuProjet(projectId));
        ProjectDirectory.Rattachement rattachement = projets.rattacher(projectId, SaisieProjet.depot(depot));
        return RestResponse.ResponseBuilder.<SaisieProjet.DepotRattache>created(
                URI.create("/api/v1/projects/" + projectId + "/repositories/" + rattachement.id()))
                .entity(new SaisieProjet.DepotRattache(rattachement.id(), rattachement.principal())).build();
    }

    @DELETE
    @Path("/{projectId}/repositories/{repositoryId}")
    @Operation(summary = "Détacher un dépôt du projet et reconstituer l'inventaire du projet (INV-12, INV-10-S3)")
    @APIResponse(responseCode = "204", description = "Dépôt détaché")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Appelant non administrateur de l'espace du projet (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Projet ou dépôt inconnu, ou projet d'un espace dont l'appelant n'est pas membre")
    public void detacher(@PathParam("projectId") UUID projectId, @PathParam("repositoryId") UUID repositoryId) {
        access.administrator(espaceDuProjet(projectId));
        if (!inventaire.detacherDepot(projectId, repositoryId)) {
            throw new NotFoundException();
        }
    }

    /** Espace du projet ; 404 sans refus journalisé pour un projet inconnu, comme {@code Access.memberOfProject}. */
    private UUID espaceDuProjet(UUID projectId) {
        access.authenticated();
        return inventories.workspaceOf(projectId).orElseThrow(NotFoundException::new);
    }
}
