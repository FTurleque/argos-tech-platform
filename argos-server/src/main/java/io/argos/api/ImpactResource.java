package io.argos.api;

import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.impact.ImpactQueries;

/** Consultation d'un impact et de sa provenance (COR-03-S1), limitée au périmètre autorisé (COR-08-S2). */
@Path("/api/v1")
@Tag(name = "Impacts")
@Produces(MediaType.APPLICATION_JSON)
public class ImpactResource {

    @Inject
    Access access;

    @Inject
    ImpactQueries impacts;

    @GET
    @Path("/impacts/{id}")
    @Operation(summary = "Impact, signal source, projet, composant, inventaire utilisé et méthode d'analyse")
    public ImpactQueries.ImpactView impact(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        Optional<ImpactQueries.ImpactView> view = impacts.find(id, caller.memberWorkspaces());
        if (view.isPresent()) {
            return view.get();
        }
        if (impacts.workspaceOf(id).isPresent()) {
            throw access.outOfScope(caller, "PROJECT_IMPACT", id.toString());
        }
        throw new NotFoundException();
    }

    @GET
    @Path("/ops/impacts/integrity")
    @Operation(summary = "Contrôle d'intégrité : aucun impact sans signal, projet, composant, méthode ou provenance")
    public ImpactQueries.IntegrityReport integrity() {
        access.operator();
        return impacts.integrity();
    }
}
