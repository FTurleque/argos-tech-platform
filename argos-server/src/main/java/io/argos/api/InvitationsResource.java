package io.argos.api;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;
import io.argos.workspace.EspacesService;
import io.argos.workspace.EspacesService.EspaceDeCompte;
import io.argos.workspace.EspacesService.Invitation;

/** Invitations reçues par l'utilisateur, rapprochées de son e-mail vérifié (ACC-05). */
@Path("/api/v1/invitations")
@Tag(name = "Comptes et accès")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class InvitationsResource {

    @Inject
    UtilisateurCourant utilisateur;

    @Inject
    EspacesService espaces;

    @GET
    @Operation(summary = "Invitations en attente adressées à l'utilisateur")
    @APIResponse(responseCode = "200", description = "Invitations en attente")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public List<Invitation> mesInvitations() {
        return espaces.mesInvitations(utilisateur.compte(), utilisateur.emailVerifie());
    }

    @POST
    @Path("/{invitationId}/acceptation")
    @Operation(summary = "Accepter une invitation et rejoindre l'espace")
    @APIResponse(responseCode = "200", description = "Espace rejoint, avec le rôle de l'invitation")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Aucune invitation adressée à l'utilisateur avec cet identifiant (non journalisé)")
    @APIResponse(responseCode = "409", description = "Invitation déjà acceptée ou révoquée (INVITATION_INACTIVE), expirée (INVITATION_EXPIREE), ou utilisateur déjà membre (DEJA_MEMBRE)")
    public EspaceDeCompte accepter(@PathParam("invitationId") UUID invitationId) {
        return espaces.accepter(utilisateur.compte(), utilisateur.emailVerifie(), invitationId);
    }
}
