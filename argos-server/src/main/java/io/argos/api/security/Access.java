package io.argos.api.security;

import io.argos.commun.securite.Caller;
import io.argos.commun.securite.CallerResolver;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import io.argos.commun.AccesRefuse;
import io.argos.commun.MotifRefus;

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

    /**
     * Appelant authentifié et membre de l'espace donné, quel que soit son rôle ; sinon refus journalisé, réponse
     * « introuvable » ({@link #outOfScope()}).
     */
    public Caller member(UUID espaceId) {
        Caller caller = authenticated();
        if (!caller.memberOf(espaceId)) {
            throw outOfScope();
        }
        return caller;
    }

    /**
     * Appelant authentifié et administrateur de l'espace donné : un non-membre reçoit le refus de {@link #member(UUID)},
     * un membre d'un autre rôle le refus journalisé « interdit » ({@link #forbidden()}).
     */
    public Caller administrator(UUID espaceId) {
        Caller caller = member(espaceId);
        if (!caller.adminOf(espaceId)) {
            throw forbidden();
        }
        return caller;
    }

    /**
     * Appelant authentifié et membre de l'espace d'un projet désigné dans le chemin. L'espace n'est lu qu'après
     * l'authentification ; un projet inconnu (espace absent) reçoit 404 sans refus journalisé, un projet d'un autre
     * espace le refus journalisé de {@link #member(UUID)}, avec la même réponse « introuvable ».
     *
     * @param espaceDuProjet lecture de l'espace du projet, vide si le projet est inconnu
     */
    public Caller memberOfProject(Supplier<Optional<UUID>> espaceDuProjet) {
        Caller caller = authenticated();
        UUID espace = espaceDuProjet.get().orElseThrow(NotFoundException::new);
        if (!caller.memberOf(espace)) {
            throw outOfScope();
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
