package io.argos.api;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.commun.securite.Caller;
import io.argos.workspace.subscription.SubscriptionRequest;
import io.argos.workspace.subscription.SubscriptionService;

/**
 * Abonnements de veille (ABO-01 à ABO-04, ABO-06 à ABO-08). Les règles d'autorisation sont appliquées ici,
 * jamais dans le module {@code workspace} (INV-D7) : un abonnement {@code PERSONAL} appartient à
 * l'appelant ; seul un administrateur de l'espace crée, suspend, réactive ou supprime un abonnement
 * {@code TEAM}. La politique RLS de {@code subscription} applique la même règle en base (V18).
 */
@Path("/api/v1/subscriptions")
@Tag(name = "Veille")
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionResource {

    /** Corps de création ; le propriétaire PERSONAL est toujours l'appelant. */
    public record CreateSubscription(String scope, @JsonProperty("teamWorkspaceId") UUID espaceEquipeId,
            List<SubscriptionRequest.Filter> filters,
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
        CreateSubscription payload = body == null ? new CreateSubscription(null, null, null, null, null, null) : body;
        boolean team = payload.scope() != null && "TEAM".equals(payload.scope().strip().toUpperCase(Locale.ROOT));
        if (team && payload.espaceEquipeId() != null && !caller.adminOf(payload.espaceEquipeId())) {
            // Création d'un abonnement TEAM réservée à l'administrateur de l'espace (ABO-06).
            throw access.forbidden();
        }
        SubscriptionRequest request = new SubscriptionRequest(payload.scope(), team ? payload.espaceEquipeId() : null,
                team ? null : caller.accountId(), payload.filters(), payload.channel(), payload.frequency(), payload.mandatory());
        return Response.status(Response.Status.CREATED).entity(subscriptions.create(request, caller.accountId())).build();
    }

    @GET
    @Operation(summary = "Abonnements visibles de l'appelant, actifs ou suspendus : socle TEAM de ses espaces et "
            + "abonnements PERSONAL ; chacun porte son état (state : ACTIVE ou SUSPENDED)")
    public List<SubscriptionService.Subscription> mine() {
        Caller caller = access.authenticated();
        return subscriptions.listFor(caller.accountId(), caller.espacesMembre());
    }

    @POST
    @Path("/{id}/suspension")
    @Operation(summary = "Suspend un abonnement : critères conservés, plus aucune notification (idempotent)")
    @APIResponse(responseCode = "200", description = "Abonnement suspendu (state = SUSPENDED)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Abonnement TEAM : l'appelant n'administre pas l'espace (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Abonnement inconnu ou invisible de l'appelant")
    public SubscriptionService.Subscription suspend(@PathParam("id") UUID id) {
        Caller caller = authorizedManager(id);
        return subscriptions.suspend(id, caller.accountId()).orElseThrow(NotFoundException::new);
    }

    @POST
    @Path("/{id}/reactivation")
    @Operation(summary = "Réactive un abonnement suspendu, sans rejouer la période de suspension (idempotent)")
    @APIResponse(responseCode = "200", description = "Abonnement actif (state = ACTIVE)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Abonnement TEAM : l'appelant n'administre pas l'espace (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Abonnement inconnu, invisible ou supprimé")
    public SubscriptionService.Subscription reactivate(@PathParam("id") UUID id) {
        Caller caller = authorizedManager(id);
        return subscriptions.reactivate(id, caller.accountId()).orElseThrow(NotFoundException::new);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Supprime définitivement un abonnement, actif ou suspendu, avec ses critères et les retraits")
    @APIResponse(responseCode = "204", description = "Abonnement supprimé")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Abonnement TEAM : l'appelant n'administre pas l'espace (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Abonnement inconnu, invisible ou déjà supprimé")
    public Response delete(@PathParam("id") UUID id) {
        Caller caller = authorizedManager(id);
        if (!subscriptions.delete(id, caller.accountId())) {
            throw new NotFoundException();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/leave")
    @Operation(summary = "Quitte un abonnement : refusé pour un abonnement TEAM obligatoire ; suspend un abonnement PERSONAL")
    public Response leave(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        return switch (subscriptions.leave(id, caller.accountId(), caller.espacesMembre())) {
            case LEFT -> Response.noContent().build();
            case MANDATORY -> Response.status(Response.Status.CONFLICT)
                    .entity(new Erreur("ABONNEMENT_OBLIGATOIRE", "abonnement TEAM obligatoire : il ne peut pas être quitté", "mandatory")).build();
            case NOT_FOUND -> throw new NotFoundException();
        };
    }

    /**
     * Appelant autorisé à gérer l'abonnement (ABO-07, ABO-08, proposition P-8) : propriétaire d'un
     * {@code PERSONAL}, administrateur de l'espace d'un {@code TEAM}. Un abonnement inconnu ou invisible
     * (RLS : {@code PERSONAL} d'un autre compte, {@code TEAM} d'un espace dont l'appelant n'est pas membre)
     * répond 404 sans le révéler ; un membre non administrateur reçoit le refus journalisé 403.
     */
    private Caller authorizedManager(UUID id) {
        Caller caller = access.authenticated();
        SubscriptionService.Subscription subscription = subscriptions.find(id, caller.accountId())
                .orElseThrow(NotFoundException::new);
        if ("PERSONAL".equals(subscription.scope())) {
            if (!caller.accountId().equals(subscription.ownerUserId())) {
                throw new NotFoundException();
            }
            return caller;
        }
        if (!caller.memberOf(subscription.espaceProprietaireId())) {
            throw access.outOfScope();
        }
        if (!caller.adminOf(subscription.espaceProprietaireId())) {
            throw access.forbidden();
        }
        return caller;
    }
}
