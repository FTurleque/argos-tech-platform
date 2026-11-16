package io.argos.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import io.quarkus.security.Authenticated;
import io.argos.workspace.EspacesService;
import io.argos.workspace.EspacesService.EspaceDeCompte;
import io.argos.workspace.EspacesService.Invitation;
import io.argos.workspace.EspacesService.Membre;
import io.argos.workspace.Role;

/**
 * Espaces, membres et invitations émises (ACC-03, ACC-04, ACC-05).
 *
 * <p>Ne contient aucune règle : l'appartenance, les rôles et les invariants sont vérifiés par
 * le module {@code workspace}. Un non-membre reçoit 404 (Q-4), un rôle insuffisant 403.
 */
@Path("/api/v1/espaces")
@Tag(name = "Comptes et accès")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class EspacesResource {

    public record NouvelEspace(String nom) {
    }

    public record ChangementRole(Role role) {
    }

    public record NouvelleInvitation(String email, Role role) {
    }

    @Inject
    UtilisateurCourant utilisateur;

    @Inject
    EspacesService espaces;

    @GET
    @Operation(summary = "Espaces dont l'utilisateur est membre")
    @APIResponse(responseCode = "200", description = "Espaces et rôle de l'utilisateur dans chacun")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public List<EspaceDeCompte> mesEspaces() {
        return espaces.mesEspaces(utilisateur.compte());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Créer un espace dont l'utilisateur devient administrateur")
    @APIResponse(responseCode = "201", description = "Espace créé")
    @APIResponse(responseCode = "400", description = "Nom invalide")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public RestResponse<EspaceDeCompte> creer(NouvelEspace demande) {
        EspaceDeCompte espace = espaces.creer(utilisateur.compte(), demande == null ? null : demande.nom());
        return RestResponse.ResponseBuilder.<EspaceDeCompte>created(URI.create("/api/v1/espaces/" + espace.id()))
                .entity(espace).build();
    }

    @GET
    @Path("/{espaceId}")
    @Operation(summary = "Espace dont l'utilisateur est membre")
    @APIResponse(responseCode = "200", description = "Espace")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace introuvable ou utilisateur non membre")
    public EspaceDeCompte obtenir(@PathParam("espaceId") UUID espaceId) {
        return espaces.obtenir(utilisateur.compte(), espaceId);
    }

    @GET
    @Path("/{espaceId}/membres")
    @Operation(summary = "Membres de l'espace et leurs rôles")
    @APIResponse(responseCode = "200", description = "Membres")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace introuvable ou utilisateur non membre")
    public List<Membre> membres(@PathParam("espaceId") UUID espaceId) {
        return espaces.membres(utilisateur.compte(), espaceId);
    }

    @PUT
    @Path("/{espaceId}/membres/{compteId}/role")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Changer le rôle d'un membre (administrateurs de l'espace)")
    @APIResponse(responseCode = "200", description = "Membre avec son nouveau rôle")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Rôle insuffisant")
    @APIResponse(responseCode = "404", description = "Espace introuvable ou utilisateur non membre")
    @APIResponse(responseCode = "409", description = "Dernier administrateur ou membre inconnu")
    public Membre changerRole(@PathParam("espaceId") UUID espaceId, @PathParam("compteId") UUID compteId,
            ChangementRole demande) {
        return espaces.changerRole(utilisateur.compte(), espaceId, compteId, demande == null ? null : demande.role());
    }

    @DELETE
    @Path("/{espaceId}/membres/{compteId}")
    @Operation(summary = "Révoquer l'accès d'un membre (administrateurs de l'espace)")
    @APIResponse(responseCode = "204", description = "Accès révoqué")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Rôle insuffisant")
    @APIResponse(responseCode = "404", description = "Espace introuvable ou utilisateur non membre")
    @APIResponse(responseCode = "409", description = "Dernier administrateur ou membre inconnu")
    public void revoquer(@PathParam("espaceId") UUID espaceId, @PathParam("compteId") UUID compteId) {
        espaces.revoquer(utilisateur.compte(), espaceId, compteId);
    }

    @POST
    @Path("/{espaceId}/invitations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Inviter une adresse e-mail avec un rôle (administrateurs de l'espace)")
    @APIResponse(responseCode = "201", description = "Invitation créée")
    @APIResponse(responseCode = "400", description = "Adresse ou rôle invalide")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Rôle insuffisant")
    @APIResponse(responseCode = "404", description = "Espace introuvable ou utilisateur non membre")
    public RestResponse<Invitation> inviter(@PathParam("espaceId") UUID espaceId, NouvelleInvitation demande) {
        Invitation invitation = espaces.inviter(utilisateur.compte(), espaceId,
                demande == null ? null : demande.email(), demande == null ? null : demande.role());
        return RestResponse.status(RestResponse.Status.CREATED, invitation);
    }

    @GET
    @Path("/{espaceId}/invitations")
    @Operation(summary = "Invitations de l'espace (administrateurs de l'espace)")
    @APIResponse(responseCode = "200", description = "Invitations")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Rôle insuffisant")
    @APIResponse(responseCode = "404", description = "Espace introuvable ou utilisateur non membre")
    public List<Invitation> invitations(@PathParam("espaceId") UUID espaceId) {
        return espaces.invitations(utilisateur.compte(), espaceId);
    }

    @DELETE
    @Path("/{espaceId}/invitations/{invitationId}")
    @Operation(summary = "Révoquer une invitation en attente (administrateurs de l'espace)")
    @APIResponse(responseCode = "204", description = "Invitation révoquée")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Rôle insuffisant")
    @APIResponse(responseCode = "404", description = "Espace introuvable ou utilisateur non membre")
    @APIResponse(responseCode = "409", description = "Aucune invitation en attente avec cet identifiant")
    public void revoquerInvitation(@PathParam("espaceId") UUID espaceId,
            @PathParam("invitationId") UUID invitationId) {
        espaces.revoquerInvitation(utilisateur.compte(), espaceId, invitationId);
    }
}
