package io.argos.api;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.alerting.ConsultationAlertes;
import io.argos.api.security.Access;
import io.argos.api.security.Caller;

/**
 * Alertes et résumés de l'appelant (ALR-06). Contrat du client desktop ({@code notifier-sur-le-poste}) :
 * il lit les alertes {@code DESKTOP} {@code EN_ATTENTE}, les affiche, puis confirme leur remise.
 * Une alerte en échec d'envoi reste consultable ici (ALR-06-S2). Une alerte d'un autre compte est
 * introuvable ; la tentative est journalisée (ACC-06).
 */
@Path("/api/v1/alertes")
@Tag(name = "Alertes")
@Produces(MediaType.APPLICATION_JSON)
public class AlertesResource {

    @Inject
    Access access;

    @Inject
    ConsultationAlertes alertes;

    @GET
    @Operation(summary = "Alertes et résumés de l'appelant, des plus récents aux plus anciens (filtres : canal, état)")
    public List<ConsultationAlertes.AlerteVue> lister(@QueryParam("canal") String canal, @QueryParam("etat") String etat,
            @QueryParam("limite") @DefaultValue("50") int limite) {
        Caller caller = access.authenticated();
        return alertes.lister(caller.accountId(), canal, etat, limite);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Alerte ou résumé de l'appelant")
    public ConsultationAlertes.AlerteVue obtenir(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        return alertes.obtenir(caller.accountId(), id).orElseThrow(() -> introuvable(id));
    }

    @POST
    @Path("/{id}/remise")
    @Operation(summary = "Confirme la remise d'une alerte DESKTOP au client desktop")
    public Response remettre(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        return switch (alertes.remettre(caller.accountId(), id)) {
            case REMISE -> Response.ok(alertes.obtenir(caller.accountId(), id).orElseThrow()).build();
            case CANAL_NON_DESKTOP -> Response.status(Response.Status.CONFLICT)
                    .entity(new Erreur("CANAL_NON_DESKTOP", "seule une alerte du canal DESKTOP est remise au client desktop",
                            "canal")).build();
            case INTROUVABLE -> throw introuvable(id);
        };
    }

    /** Alerte d'un autre compte : refus journalisé, réponse « introuvable » ; sinon simple 404. */
    private RuntimeException introuvable(UUID id) {
        return alertes.existe(id) ? access.outOfScope() : new NotFoundException();
    }
}
