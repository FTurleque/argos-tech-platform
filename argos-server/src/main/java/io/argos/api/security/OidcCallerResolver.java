package io.argos.api.security;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.api.UtilisateurCourant;
import io.argos.commun.securite.Caller;
import io.argos.commun.securite.CallerResolver;
import io.argos.workspace.Compte;
import io.argos.workspace.EspacesService;
import io.argos.workspace.Role;

/**
 * Appelant établi à partir du jeton OIDC validé par Quarkus (ADR-0008) : compte Argos relié au
 * sujet du jeton, créé à la première connexion (ACC-02), et appartenances relues à chaque requête
 * (une révocation s'applique dès la requête suivante, ACC-05-S2). L'administrateur plateforme a le
 * rôle d'exploitation, sans accès implicite aux espaces.
 */
@ApplicationScoped
public class OidcCallerResolver implements CallerResolver {

    @Inject
    UtilisateurCourant utilisateur;

    @Inject
    EspacesService espaces;

    @Override
    public Optional<Caller> current() {
        if (!utilisateur.authentifie()) {
            return Optional.empty();
        }
        Compte compte = utilisateur.compte();
        Set<UUID> membre = new HashSet<>();
        Set<UUID> admin = new HashSet<>();
        Set<UUID> contributeur = new HashSet<>();
        for (EspacesService.EspaceDeCompte espace : espaces.mesEspaces(compte)) {
            membre.add(espace.id());
            if (espace.role() == Role.ADMINISTRATEUR) {
                admin.add(espace.id());
            }
            if (espace.role() == Role.ADMINISTRATEUR || espace.role() == Role.CONTRIBUTEUR) {
                contributeur.add(espace.id());
            }
        }
        return Optional.of(new Caller(compte.id(), membre, admin, compte.adminPlateforme(), contributeur));
    }
}
