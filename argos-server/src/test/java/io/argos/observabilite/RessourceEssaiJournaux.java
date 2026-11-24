package io.argos.observabilite;

import java.io.IOException;
import java.io.UncheckedIOException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.jboss.logging.Logger;

/**
 * Ressource REST des seuls tests d'observabilité : une requête journalise deux événements portant le marqueur reçu
 * ({@code /etapes}), une erreur provoquée par une exception et sa cause ({@code /erreur}), ou une erreur dont
 * l'exception et la cause citent le jeton de la requête ({@code /erreur-jeton}). Authentification exigée comme pour
 * toute l'API.
 */
@Path("/api/v1/essai-observabilite")
public class RessourceEssaiJournaux {

    private static final Logger LOG = Logger.getLogger(RessourceEssaiJournaux.class);

    @GET
    @Path("/etapes")
    public String etapes(@QueryParam("marqueur") String marqueur) {
        LOG.infof("essai %s : première étape", marqueur);
        LOG.infof("essai %s : seconde étape", marqueur);
        return marqueur;
    }

    @GET
    @Path("/erreur")
    public String erreur(@QueryParam("marqueur") String marqueur) {
        IllegalStateException erreur = new IllegalStateException("panne simulée " + marqueur,
                new UncheckedIOException(new IOException("cause simulée " + marqueur)));
        LOG.errorf(erreur, "essai %s : erreur journalisée", marqueur);
        return marqueur;
    }

    @GET
    @Path("/erreur-jeton")
    public String erreurCitantLeJeton(@QueryParam("marqueur") String marqueur, @HeaderParam("Authorization") String entete) {
        String jeton = entete.substring(entete.indexOf(' ') + 1);
        IllegalStateException erreur = new IllegalStateException("jeton refusé " + jeton,
                new UncheckedIOException(new IOException("cause citant " + jeton)));
        LOG.errorf(erreur, "essai %s : erreur citant le jeton", marqueur);
        return marqueur;
    }
}
