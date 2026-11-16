package io.argos.api.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import io.argos.audit.AccessRefusalLog;

/**
 * Contrôles d'accès communs aux ressources REST. Un accès à une ressource d'un autre espace est
 * refusé comme une ressource inexistante (404), sans en révéler le contenu ni l'existence, et
 * journalisé (COR-08-S2).
 */
@ApplicationScoped
public class Access {

    @Inject
    CallerResolver resolver;

    @Inject
    AccessRefusalLog refusals;

    public Caller authenticated() {
        return resolver.current().orElseThrow(() -> new NotAuthorizedException("Bearer"));
    }

    public Caller operator() {
        Caller caller = authenticated();
        if (!caller.operator()) {
            refusals.record(caller.userId(), "OPERATIONS", "ops", "rôle d'exploitation requis");
            throw new ForbiddenException();
        }
        return caller;
    }

    /** Journalise le refus puis répond comme pour une ressource inexistante. */
    public NotFoundException outOfScope(Caller caller, String resourceType, String resourceId) {
        refusals.record(caller.userId(), resourceType, resourceId, "hors du périmètre autorisé");
        return new NotFoundException();
    }

    /** Refus explicite d'une action (droit insuffisant sur une ressource visible). */
    public ForbiddenException forbidden(Caller caller, String resourceType, String resourceId, String reason) {
        refusals.record(caller.userId(), resourceType, resourceId, reason);
        return new ForbiddenException();
    }
}
