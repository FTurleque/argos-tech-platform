package io.argos.api;

import java.util.Optional;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.argos.workspace.Compte;
import io.argos.workspace.ComptesService;
import io.argos.workspace.IdentiteOidc;

/**
 * Utilisateur de la requête courante, établi à partir du jeton OIDC déjà validé par Quarkus
 * (signature, émetteur, audience, expiration). Le compte Argos est créé à la première
 * connexion (ACC-02).
 */
@RequestScoped
public class UtilisateurCourant {

    @Inject
    JsonWebToken jeton;

    @Inject
    ComptesService comptes;

    private Compte compte;

    /** Vrai si la requête porte un jeton validé avec un sujet. */
    public boolean authentifie() {
        String sujet = jeton == null ? null : jeton.getSubject();
        return sujet != null && !sujet.isBlank();
    }

    public IdentiteOidc identite() {
        String nom = premier(jeton.getClaim("name"), jeton.getClaim("preferred_username"));
        Object verifie = jeton.getClaim("email_verified");
        boolean emailVerifie = verifie instanceof Boolean booleen ? booleen : Boolean.parseBoolean(String.valueOf(verifie));
        Object email = jeton.getClaim("email");
        return new IdentiteOidc(jeton.getSubject(), nom, email == null ? null : String.valueOf(email), emailVerifie);
    }

    public Compte compte() {
        if (compte == null) {
            compte = comptes.obtenirOuCreer(identite());
        }
        return compte;
    }

    /** Compte déjà résolu pendant la requête, sans en créer. */
    public Optional<Compte> compteResolu() {
        return Optional.ofNullable(compte);
    }

    public String sujet() {
        return jeton.getSubject();
    }

    public String emailVerifie() {
        return identite().emailVerifieNormalise();
    }

    private static String premier(Object... valeurs) {
        for (Object valeur : valeurs) {
            if (valeur != null && !String.valueOf(valeur).isBlank()) {
                return String.valueOf(valeur);
            }
        }
        return null;
    }
}
