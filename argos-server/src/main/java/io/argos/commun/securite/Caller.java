package io.argos.commun.securite;

import java.util.Set;
import java.util.UUID;

/**
 * Appelant authentifié et son périmètre autorisé, déterminé uniquement par ses appartenances aux
 * espaces et leurs rôles (ABO-05, ADR-0016).
 *
 * @param accountId identifiant stable du compte Argos, relié au sujet du jeton OIDC
 * @param espacesMembre espaces dont il est membre, quel que soit son rôle
 * @param espacesAdministres espaces qu'il administre (sous-ensemble de {@code espacesMembre})
 * @param operator vrai pour l'administrateur plateforme (état des sources, contrôle d'intégrité)
 * @param espacesContributeur espaces où il est {@code CONTRIBUTEUR} ou {@code ADMINISTRATEUR} (sous-ensemble de
 *        {@code espacesMembre}) : il peut y changer l'état de traitement des impacts (COR-10, P-5)
 */
public record Caller(UUID accountId, Set<UUID> espacesMembre, Set<UUID> espacesAdministres, boolean operator,
        Set<UUID> espacesContributeur) {

    public Caller {
        espacesMembre = Set.copyOf(espacesMembre);
        espacesAdministres = Set.copyOf(espacesAdministres);
        espacesContributeur = Set.copyOf(espacesContributeur);
    }

    public boolean memberOf(UUID espaceId) {
        return espacesMembre.contains(espaceId);
    }

    public boolean adminOf(UUID espaceId) {
        return espacesAdministres.contains(espaceId) && espacesMembre.contains(espaceId);
    }

    /** Vrai si l'appelant est {@code CONTRIBUTEUR} ou {@code ADMINISTRATEUR} de l'espace. */
    public boolean contributorOf(UUID espaceId) {
        return espacesMembre.contains(espaceId)
                && (espacesContributeur.contains(espaceId) || espacesAdministres.contains(espaceId));
    }
}
