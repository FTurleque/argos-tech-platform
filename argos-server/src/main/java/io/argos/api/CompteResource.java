package io.argos.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;
import io.argos.workspace.Compte;

/** Session et compte courants (ACC-01, ACC-02). */
@Path("/api/v1/moi")
@Tag(name = "Comptes et accès")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CompteResource {

    /** Compte courant, sans le sujet OIDC. */
    public record CompteCourant(UUID id, String nomAffiche, String email, boolean adminPlateforme,
            OffsetDateTime creeLe) {

        static CompteCourant de(Compte compte) {
            return new CompteCourant(compte.id(), compte.nomAffiche(), compte.email(), compte.adminPlateforme(), compte.creeLe());
        }
    }

    @Inject
    UtilisateurCourant utilisateur;

    @GET
    @Operation(summary = "Compte courant, créé à la première connexion")
    @APIResponse(responseCode = "200", description = "Compte de l'utilisateur connecté")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Inscription sur invitation : aucune invitation en attente")
    public CompteCourant moi() {
        return CompteCourant.de(utilisateur.compte());
    }
}
