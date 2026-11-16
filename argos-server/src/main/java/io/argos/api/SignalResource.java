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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.impact.ImpactQueries;
import io.argos.signal.SignalQueries;
import io.argos.vulnintel.Enricher;

/** Consultation d'un signal de sécurité, de sa provenance (COL-02-S1) et de ses impacts (COR-08-S1). */
@Path("/api/v1/signals")
@Tag(name = "Signaux de sécurité")
@Produces(MediaType.APPLICATION_JSON)
public class SignalResource {

    /** Signal, provenance, historique et enrichissements. */
    public record SignalDetail(SignalQueries.SignalView signal, List<SignalQueries.SignalVersionView> versions,
            List<Enricher.EnrichmentView> enrichments, boolean exploited) {
    }

    @Inject
    Access access;

    @Inject
    SignalQueries signals;

    @Inject
    Enricher enricher;

    @Inject
    ImpactQueries impacts;

    @GET
    @Path("/{id}")
    @Operation(summary = "Signal, provenance, historique des versions et enrichissements")
    public SignalDetail signal(@PathParam("id") UUID id) {
        access.authenticated();
        SignalQueries.SignalView view = signals.find(id).orElseThrow(NotFoundException::new);
        return new SignalDetail(view, signals.versions(id), enricher.forSignal(id), enricher.exploited(id));
    }

    @GET
    @Path("/{id}/versions/{versionId}/raw")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Contenu brut reçu de la source pour une version du signal")
    public String raw(@PathParam("id") UUID id, @PathParam("versionId") UUID versionId) {
        access.authenticated();
        return signals.rawContent(id, versionId).orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/{id}/impacts")
    @Operation(summary = "Impacts du signal limités aux espaces de l'appelant (liste paginée)")
    public ImpactQueries.Page impacts(@PathParam("id") UUID id, @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        Caller caller = access.authenticated();
        signals.find(id).orElseThrow(NotFoundException::new);
        return impacts.forSignal(id, caller.accountId(), caller.memberWorkspaces(), page, size);
    }
}
