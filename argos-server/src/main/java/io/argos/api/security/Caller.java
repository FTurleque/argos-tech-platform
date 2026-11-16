package io.argos.api.security;

import java.util.Set;
import java.util.UUID;

/**
 * Appelant authentifié et son périmètre autorisé, déterminé uniquement par ses appartenances aux
 * espaces et leurs rôles (ABO-05, ADR-0016).
 *
 * @param accountId identifiant stable du compte Argos, relié au sujet du jeton OIDC
 * @param memberWorkspaces espaces dont il est membre, quel que soit son rôle
 * @param adminWorkspaces espaces qu'il administre (sous-ensemble de {@code memberWorkspaces})
 * @param operator vrai pour l'administrateur plateforme (état des sources, contrôle d'intégrité)
 */
public record Caller(UUID accountId, Set<UUID> memberWorkspaces, Set<UUID> adminWorkspaces, boolean operator) {

    public Caller {
        memberWorkspaces = Set.copyOf(memberWorkspaces);
        adminWorkspaces = Set.copyOf(adminWorkspaces);
    }

    public boolean memberOf(UUID workspaceId) {
        return memberWorkspaces.contains(workspaceId);
    }

    public boolean adminOf(UUID workspaceId) {
        return adminWorkspaces.contains(workspaceId) && memberWorkspaces.contains(workspaceId);
    }
}
