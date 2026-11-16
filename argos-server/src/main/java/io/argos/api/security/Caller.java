package io.argos.api.security;

import java.util.Set;
import java.util.UUID;

/**
 * Appelant authentifié et son périmètre autorisé, déterminé uniquement par ses rôles (ABO-05).
 *
 * @param userId identifiant stable de l'utilisateur
 * @param memberWorkspaces espaces dont il est membre, quel que soit son rôle
 * @param adminWorkspaces espaces qu'il administre (sous-ensemble de {@code memberWorkspaces})
 * @param operator vrai pour le rôle d'exploitation (état des sources, contrôle d'intégrité)
 */
public record Caller(String userId, Set<UUID> memberWorkspaces, Set<UUID> adminWorkspaces, boolean operator) {

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
