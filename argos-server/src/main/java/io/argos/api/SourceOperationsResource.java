package io.argos.api;

import java.util.List;

import jakarta.inject.Inject;
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

import org.jboss.resteasy.reactive.ResponseStatus;

import io.argos.api.security.Access;
import io.argos.commun.Page;
import io.argos.commun.Pagination;
import io.argos.signal.SourceOperations;

/** Indicateurs d'exploitation des sources d'avis (COL-07-S1, COL-08), réservés au rôle d'exploitation. */
@Path("/api/v1/ops/sources")
@Tag(name = "Exploitation")
@Produces(MediaType.APPLICATION_JSON)
public class SourceOperationsResource {

    /** Statut HTTP d'une demande acceptée, traitée en tâche de fond. */
    private static final int ACCEPTE = 202;

    @Inject
    Access access;

    @Inject
    SourceOperations operations;

    @GET
    @Operation(summary = "État de chaque source : NOMINAL, FAILING ou DEGRADED")
    public List<SourceOperations.SourceStatus> sources() {
        access.operator();
        return operations.statuses();
    }

    @GET
    @Path("/{id}/rejected")
    @Operation(summary = "Avis mis à l'écart par la source, avec leur motif, du plus récent au plus ancien (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page d'avis mis à l'écart")
    @APIResponse(responseCode = "400", description = "Paramètre page ou size non entier")
    public Page<SourceOperations.RejectedAdvisory> rejected(@PathParam("id") String id,
            @Parameter(description = ParametresPagination.DESCRIPTION_PAGE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam(ParametresPagination.PAGE) String page,
            @Parameter(description = ParametresPagination.DESCRIPTION_TAILLE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + Pagination.TAILLE_DEFAUT))
            @QueryParam(ParametresPagination.TAILLE) String size) {
        Pagination pagination = ParametresPagination.pagination(page, size);
        access.operator();
        requireSource(id);
        return operations.rejected(id, pagination);
    }

    @GET
    @Path("/{id}/events")
    @Operation(summary = "Événements d'exploitation datés de la source, du plus récent au plus ancien (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page d'événements d'exploitation")
    @APIResponse(responseCode = "400", description = "Paramètre page ou size non entier")
    public Page<SourceOperations.OperationEvent> events(@PathParam("id") String id,
            @Parameter(description = ParametresPagination.DESCRIPTION_PAGE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam(ParametresPagination.PAGE) String page,
            @Parameter(description = ParametresPagination.DESCRIPTION_TAILLE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + Pagination.TAILLE_DEFAUT))
            @QueryParam(ParametresPagination.TAILLE) String size) {
        Pagination pagination = ParametresPagination.pagination(page, size);
        access.operator();
        requireSource(id);
        return operations.events(id, pagination);
    }

    @POST
    @Path("/{id}/synchronizations")
    @ResponseStatus(ACCEPTE)
    @Operation(summary = "Lance une synchronisation de la source, poursuivie en tâche de fond (COL-12)")
    @APIResponse(responseCode = "202", description = "Traitement démarré : identifiant du traitement")
    @APIResponse(responseCode = "409", description = "SYNCHRONISATION_EN_COURS ou LANCEMENT_SATURE")
    public SourceOperations.SyncLaunch synchronize(@PathParam("id") String id) {
        access.operator();
        requireSource(id);
        return operations.lancer(id);
    }

    private void requireSource(String id) {
        if (operations.statuses().stream().noneMatch(status -> status.sourceId().equals(id))) {
            throw new NotFoundException();
        }
    }
}
