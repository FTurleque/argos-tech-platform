package io.argos.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response.Status;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.argos.audit.JournalRefus;
import io.argos.workspace.AccesRefuse;
import io.argos.workspace.Compte;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.InscriptionRefusee;
import io.argos.workspace.Introuvable;
import io.argos.workspace.MotifRefus;
import io.argos.workspace.RegleViolee;
import io.argos.workspace.subscription.SubscriptionRejectedException;

/**
 * Traduction des refus et erreurs du domaine en réponses HTTP.
 *
 * <ul>
 *   <li>non-membre ou ressource inexistante : 404, qui ne révèle pas l'existence de la ressource (Q-4) ;</li>
 *   <li>membre au rôle insuffisant : 403 (ACC-03-S2) ;</li>
 *   <li>chaque refus d'accès est journalisé dans {@code audit} (ACC-06).</li>
 * </ul>
 */
public class GestionErreurs {

    @Inject
    UtilisateurCourant utilisateur;

    @Inject
    JournalRefus journal;

    @ServerExceptionMapper
    public RestResponse<Erreur> accesRefuse(AccesRefuse e, ContainerRequestContext requete) {
        journal.enregistrer(utilisateur.compteResolu().map(Compte::id).orElse(null), utilisateur.sujet(),
                ressource(requete), e.motif().name());
        if (e.motif() == MotifRefus.NON_MEMBRE) {
            return RestResponse.status(Status.NOT_FOUND, new Erreur("INTROUVABLE", e.getMessage()));
        }
        return RestResponse.status(Status.FORBIDDEN, new Erreur("INTERDIT", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<Erreur> inscriptionRefusee(InscriptionRefusee e, ContainerRequestContext requete) {
        journal.enregistrer(null, utilisateur.sujet(), ressource(requete), "INSCRIPTION_SUR_INVITATION");
        return RestResponse.status(Status.FORBIDDEN, new Erreur("INSCRIPTION_SUR_INVITATION", e.getMessage()));
    }

    /** Ressource absente pour le compte courant, sans refus d'accès à un espace : non journalisée. */
    @ServerExceptionMapper
    public RestResponse<Erreur> introuvable(Introuvable e) {
        return RestResponse.status(Status.NOT_FOUND, new Erreur("INTROUVABLE", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<Erreur> regleViolee(RegleViolee e) {
        return RestResponse.status(Status.CONFLICT, new Erreur(e.code(), e.getMessage()));
    }

    /** Demande d'abonnement refusée : attribut manquant ou invalide (ABO-01-S1, ABO-02-S2). */
    @ServerExceptionMapper
    public RestResponse<Erreur> abonnementRefuse(SubscriptionRejectedException e) {
        return RestResponse.status(Status.BAD_REQUEST, new Erreur("ABONNEMENT_INVALIDE", e.getMessage(), e.attribute()));
    }

    @ServerExceptionMapper
    public RestResponse<Erreur> donneeInvalide(DonneeInvalide e) {
        return RestResponse.status(Status.BAD_REQUEST, new Erreur("DONNEE_INVALIDE", e.getMessage()));
    }

    /** Méthode et chemin seulement : ni paramètre de requête ni corps (ACC-06). */
    private static String ressource(ContainerRequestContext requete) {
        return requete.getMethod() + " " + requete.getUriInfo().getPath();
    }
}
