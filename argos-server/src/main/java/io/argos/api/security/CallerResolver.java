package io.argos.api.security;

import java.util.Optional;

/**
 * Détermine l'appelant de la requête en cours et son périmètre. L'implémentation reliée à
 * l'authentification OIDC et aux rôles par espace relève du changement gerer-comptes-et-acces.
 */
public interface CallerResolver {

    /** Appelant authentifié, ou vide si la requête n'est pas authentifiée. */
    Optional<Caller> current();
}
