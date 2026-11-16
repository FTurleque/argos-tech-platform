package io.argos.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.argos.workspace.subscription.SubscriptionRejectedException;

/** Demande d'abonnement refusée : 400 avec l'attribut manquant ou invalide (ABO-01-S1, ABO-02-S2). */
@Provider
public class SubscriptionRejectedMapper implements ExceptionMapper<SubscriptionRejectedException> {

    @Override
    public Response toResponse(SubscriptionRejectedException e) {
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(e.getMessage(), e.attribute())).build();
    }
}
