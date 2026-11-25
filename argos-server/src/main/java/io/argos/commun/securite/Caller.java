package io.argos.commun.securite;

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
 * @param contributorWorkspaces espaces où il est {@code CONTRIBUTEUR} ou {@code ADMINISTRATEUR} (sous-ensemble de
 *        {@code memberWorkspaces}) : il peut y changer l'état de traitement des impacts (COR-10, P-5)
 */
public record Caller(UUID accountId, Set<UUID> memberWorkspaces, Set<UUID> adminWorkspaces, boolean operator,
        Set<UUID> contributorWorkspaces) {

    public Caller {
        memberWorkspaces = Set.copyOf(memberWorkspaces);
        adminWorkspaces = Set.copyOf(adminWorkspaces);
        contributorWorkspaces = Set.copyOf(contributorWorkspaces);
    }

    public boolean memberOf(UUID workspaceId) {
        return memberWorkspaces.contains(workspaceId);
    }

    public boolean adminOf(UUID workspaceId) {
        return adminWorkspaces.contains(workspaceId) && memberWorkspaces.contains(workspaceId);
    }

    /** Vrai si l'appelant est {@code CONTRIBUTEUR} ou {@code ADMINISTRATEUR} de l'espace. */
    public boolean contributorOf(UUID workspaceId) {
        return memberWorkspaces.contains(workspaceId)
                && (contributorWorkspaces.contains(workspaceId) || adminWorkspaces.contains(workspaceId));
    }
}
