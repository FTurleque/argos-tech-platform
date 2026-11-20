package io.argos.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response.Status;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.argos.api.security.LimiteDebitAtteinte;
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
 *   <li>limite de débit atteinte : 429 avec l'en-tête {@code Retry-After} ;</li>
 *   <li>chaque refus d'accès et chaque refus pour limite de débit est journalisé dans {@code audit} (ACC-06).</li>
 * </ul>
 */
public class GestionErreurs {

    @Inject
    UtilisateurCourant utilisateur;

    @Inject
    JournalRefus journal;

    @ServerExceptionMapper
    public RestResponse<Erreur> accesRefuse(AccesRefuse erreur, ContainerRequestContext requete) {
        journal.enregistrer(utilisateur.compteResolu().map(Compte::id).orElse(null), utilisateur.sujet(),
                ressource(requete), erreur.motif().name());
        if (erreur.motif() == MotifRefus.NON_MEMBRE) {
            return RestResponse.status(Status.NOT_FOUND, new Erreur("INTROUVABLE", erreur.getMessage()));
        }
        return RestResponse.status(Status.FORBIDDEN, new Erreur("INTERDIT", erreur.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<Erreur> inscriptionRefusee(InscriptionRefusee erreur, ContainerRequestContext requete) {
        journal.enregistrer(null, utilisateur.sujet(), ressource(requete), "INSCRIPTION_SUR_INVITATION");
        return RestResponse.status(Status.FORBIDDEN, new Erreur("INSCRIPTION_SUR_INVITATION", erreur.getMessage()));
    }

    /** Ressource absente pour le compte courant, sans refus d'accès à un espace : non journalisée. */
    @ServerExceptionMapper
    public RestResponse<Erreur> introuvable(Introuvable erreur) {
        return RestResponse.status(Status.NOT_FOUND, new Erreur("INTROUVABLE", erreur.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<Erreur> regleViolee(RegleViolee erreur) {
        return RestResponse.status(Status.CONFLICT, new Erreur(erreur.code(), erreur.getMessage()));
    }

    /** Demande d'abonnement refusée : attribut manquant ou invalide (ABO-01-S1, ABO-02-S2). */
    @ServerExceptionMapper
    public RestResponse<Erreur> abonnementRefuse(SubscriptionRejectedException erreur) {
        return RestResponse.status(Status.BAD_REQUEST, new Erreur("ABONNEMENT_INVALIDE", erreur.getMessage(), erreur.attribute()));
    }

    /** Donnée invalide, y compris une adresse de veille web refusée (WEB-01-S2, WEB-03-S1). */
    @ServerExceptionMapper
    public RestResponse<Erreur> donneeInvalide(DonneeInvalide erreur) {
        return RestResponse.status(Status.BAD_REQUEST, new Erreur(erreur.code(), erreur.getMessage(), erreur.attribut()));
    }

    /** Limite de débit atteinte : refus journalisé (motif {@code LIMITE_DEBIT}), réponse 429. */
    @ServerExceptionMapper
    public RestResponse<Erreur> limiteDebit(LimiteDebitAtteinte erreur, ContainerRequestContext requete) {
        journal.enregistrer(utilisateur.compteResolu().map(Compte::id).orElse(null), utilisateur.sujet(),
                ressource(requete), "LIMITE_DEBIT");
        return RestResponse.ResponseBuilder.create(Status.TOO_MANY_REQUESTS, new Erreur("LIMITE_DEBIT", erreur.getMessage()))
                .header("Retry-After", "60").build();
    }

    /** Méthode et chemin seulement : ni paramètre de requête ni corps (ACC-06). */
    private static String ressource(ContainerRequestContext requete) {
        return requete.getMethod() + " " + requete.getUriInfo().getPath();
    }
}
