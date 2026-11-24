package io.argos.api.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.argos.workspace.Compte;
import io.argos.workspace.ComptesService;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

/**
 * Politique HTTP {@value #NOM} : n'admet que l'administrateur plateforme, c'est-à-dire un appelant authentifié par
 * un jeton OIDC dont le compte Argos porte {@code admin_plateforme} (décision Q-1 du porteur du projet, 2026-11-24,
 * pour {@code /q/metrics}, EXP-14-S2, ADR-0036). Un appelant anonyme reçoit 401, un autre compte 403. Le compte est
 * relu sur un fil de travail : la lecture de la base ne bloque pas la boucle d'événements.
 */
@ApplicationScoped
public class PolitiqueAdminPlateforme implements HttpSecurityPolicy {

    /** Nom de la politique dans {@code quarkus.http.auth.permission.<nom>.policy}. */
    public static final String NOM = "admin-plateforme";

    @Inject
    ComptesService comptes;

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext requete, Uni<SecurityIdentity> identite,
            AuthorizationRequestContext contexte) {
        return contexte.runBlocking(requete, identite, (routage, appelant) -> admis(appelant)
                ? CheckResult.PERMIT : CheckResult.DENY);
    }

    @Override
    public String name() {
        return NOM;
    }

    private boolean admis(SecurityIdentity appelant) {
        if (appelant.isAnonymous() || !(appelant.getPrincipal() instanceof JsonWebToken jeton)
                || jeton.getSubject() == null) {
            return false;
        }
        return comptes.parSujet(jeton.getSubject()).map(Compte::adminPlateforme).orElse(false);
    }
}
