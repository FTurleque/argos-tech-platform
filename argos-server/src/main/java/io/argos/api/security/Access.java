package io.argos.api.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;

import io.argos.workspace.AccesRefuse;
import io.argos.workspace.MotifRefus;

/**
 * Contrôles d'accès communs aux ressources REST de la veille. Les refus sont des
 * {@link AccesRefuse} : la couche d'exposition les journalise dans le journal unique des refus
 * (module {@code audit}) et répond 404 pour une ressource d'un autre espace, sans en révéler le
 * contenu ni l'existence (COR-08-S2, ACC-06), ou 403 pour un rôle insuffisant.
 */
@ApplicationScoped
public class Access {

    @Inject
    CallerResolver resolver;

    public Caller authenticated() {
        return resolver.current().orElseThrow(() -> new NotAuthorizedException("Bearer"));
    }

    /** Administrateur plateforme (exploitation) ; refus journalisé sinon. */
    public Caller operator() {
        Caller caller = authenticated();
        if (!caller.operator()) {
            throw new AccesRefuse(MotifRefus.ROLE_INSUFFISANT);
        }
        return caller;
    }

    /** Ressource existante hors du périmètre de l'appelant : refus journalisé, réponse « introuvable ». */
    public AccesRefuse outOfScope() {
        return new AccesRefuse(MotifRefus.NON_MEMBRE);
    }

    /** Action interdite sur une ressource visible : refus journalisé, réponse « interdit ». */
    public AccesRefuse forbidden() {
        return new AccesRefuse(MotifRefus.ROLE_INSUFFISANT);
    }
}
