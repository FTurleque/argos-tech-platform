package io.argos.api;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Corps de réponse d'une demande refusée.
 *
 * @param code    code stable, exploitable par l'IHM
 * @param message explication lisible par l'utilisateur
 */
@Schema(name = "Erreur", description = "Demande refusée")
public record Erreur(String code, String message) {

    /** Exception JAX-RS portant une réponse 400 avec ce corps. */
    public static WebApplicationException demandeRefusee(String code, String message) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Erreur(code, message))
                .build());
    }
}
