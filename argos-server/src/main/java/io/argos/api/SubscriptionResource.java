package io.argos.api;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.workspace.subscription.SubscriptionRequest;
import io.argos.workspace.subscription.SubscriptionService;

/**
 * Abonnements de veille (ABO-01 à ABO-04, ABO-06). Les règles d'autorisation sont appliquées ici,
 * jamais dans le module {@code workspace} (INV-D7) : un abonnement {@code PERSONAL} appartient à
 * l'appelant ; seul un administrateur de l'espace crée un abonnement {@code TEAM}.
 */
@Path("/api/v1/subscriptions")
@Tag(name = "Veille")
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionResource {

    /** Corps de création ; le propriétaire PERSONAL est toujours l'appelant. */
    public record CreateSubscription(String scope, UUID teamWorkspaceId, List<SubscriptionRequest.Filter> filters,
            String channel, String frequency, Boolean mandatory) {
    }

    @Inject
    Access access;

    @Inject
    SubscriptionService subscriptions;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Crée un abonnement TEAM (administrateur de l'espace) ou PERSONAL (appelant)")
    public Response create(CreateSubscription body) {
        Caller caller = access.authenticated();
        CreateSubscription b = body == null ? new CreateSubscription(null, null, null, null, null, null) : body;
        boolean team = b.scope() != null && "TEAM".equals(b.scope().strip().toUpperCase(Locale.ROOT));
        if (team && b.teamWorkspaceId() != null && !caller.adminOf(b.teamWorkspaceId())) {
            throw access.forbidden(caller, "WORKSPACE", b.teamWorkspaceId().toString(),
                    "création d'un abonnement TEAM réservée à l'administrateur de l'espace");
        }
        SubscriptionRequest request = new SubscriptionRequest(b.scope(), team ? b.teamWorkspaceId() : null,
                team ? null : caller.userId(), b.filters(), b.channel(), b.frequency(), b.mandatory());
        return Response.status(Response.Status.CREATED).entity(subscriptions.create(request)).build();
    }

    @GET
    @Operation(summary = "Abonnements qui s'appliquent à l'appelant : socle TEAM de ses espaces et abonnements PERSONAL")
    public List<SubscriptionService.Subscription> mine() {
        Caller caller = access.authenticated();
        return subscriptions.activeFor(caller.userId(), caller.memberWorkspaces());
    }

    @POST
    @Path("/{id}/leave")
    @Operation(summary = "Quitte un abonnement : refusé pour un abonnement TEAM obligatoire")
    public Response leave(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        return switch (subscriptions.leave(id, caller.userId(), caller.memberWorkspaces())) {
            case LEFT -> Response.noContent().build();
            case MANDATORY -> Response.status(Response.Status.CONFLICT)
                    .entity(new ApiError("abonnement TEAM obligatoire : il ne peut pas être quitté", "mandatory")).build();
            case NOT_FOUND -> throw new NotFoundException();
        };
    }
}
