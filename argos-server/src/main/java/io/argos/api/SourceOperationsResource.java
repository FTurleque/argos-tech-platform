package io.argos.api;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.argos.api.security.Access;
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
    @Operation(summary = "Avis mis à l'écart par la source, avec leur motif")
    public List<SourceOperations.RejectedAdvisory> rejected(@PathParam("id") String id) {
        access.operator();
        requireSource(id);
        return operations.rejected(id);
    }

    @GET
    @Path("/{id}/events")
    @Operation(summary = "Événements d'exploitation datés de la source")
    public List<SourceOperations.OperationEvent> events(@PathParam("id") String id) {
        access.operator();
        requireSource(id);
        return operations.events(id);
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
