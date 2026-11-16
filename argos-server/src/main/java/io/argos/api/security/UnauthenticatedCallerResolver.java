package io.argos.api.security;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

/**
 * Résolution par défaut, fermée : aucun appelant n'est authentifié, donc toute consultation de
 * données de veille est refusée (401) tant qu'aucune authentification n'est branchée.
 */
@DefaultBean
@ApplicationScoped
public class UnauthenticatedCallerResolver implements CallerResolver {

    @Override
    public Optional<Caller> current() {
        return Optional.empty();
    }
}
