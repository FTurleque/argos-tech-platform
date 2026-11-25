package io.argos.api;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.alerting.ConsultationAlertes;
import io.argos.api.security.Access;
import io.argos.commun.securite.Caller;
import io.argos.commun.Introuvable;
import io.argos.commun.RegleViolee;

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
    @APIResponse(responseCode = "200", description = "Alertes et résumés de l'appelant")
    @APIResponse(responseCode = "400", description = "Paramètre limite non entier")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public List<ConsultationAlertes.AlerteVue> lister(@QueryParam("canal") String canal, @QueryParam("etat") String etat,
            @Parameter(description = "Nombre maximal d'alertes, de 1 à 200 ; une valeur hors bornes est ramenée à la borne la plus"
                    + " proche ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "50")) @QueryParam("limite") String limite) {
        Caller caller = access.authenticated();
        return alertes.lister(caller.accountId(), canal, etat, ParametresPagination.entier(limite, "limite", 50));
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
            case CANAL_NON_DESKTOP -> throw new RegleViolee("CANAL_NON_DESKTOP",
                    "seule une alerte du canal DESKTOP est remise au client desktop");
            case INTROUVABLE -> throw introuvable(id);
        };
    }

    /** Alerte d'un autre compte : refus journalisé, réponse « introuvable » ; sinon simple 404. */
    private RuntimeException introuvable(UUID id) {
        return alertes.existe(id) ? access.outOfScope() : new Introuvable("alerte inconnue");
    }
}
