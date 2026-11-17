package io.argos.api.preference;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.argos.api.UtilisateurCourant;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.catalog.profil.Rubrique;
import io.argos.workspace.preference.PreferencesService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Rubriques de la veille de l'utilisateur courant : union des rubriques de ses profils actifs, dans
 * l'ordre du catalogue puis de chaque manifeste (ADR-0024 point 4, PRF-02, ART-01-S1). Activer un
 * profil ajoute ses rubriques sans retirer celles des autres profils.
 */
@Path("/api/v1/preferences/rubriques")
@Tag(name = "Préférences")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class RubriquesResource {

    @Schema(name = "RubriqueVeille")
    public record RubriqueVeille(String profil, String identifiant, String libelle) {
    }

    @Inject
    UtilisateurCourant utilisateur;

    @Inject
    PreferencesService preferences;

    @Inject
    ResolutionPreferences resolution;

    @Inject
    CatalogueProfils catalogue;

    @GET
    @Operation(summary = "Rubriques de la veille de l'utilisateur, issues de ses profils actifs")
    public List<RubriqueVeille> rubriques() {
        List<String> actifs = resolution.resoudre(preferences.lire(utilisateur.compte().id())).profilsActifs();
        List<RubriqueVeille> rubriques = new ArrayList<>();
        for (ManifesteProfil m : catalogue.publies()) {
            if (!actifs.contains(m.identifiant())) {
                continue;
            }
            for (Rubrique r : m.rubriques()) {
                rubriques.add(new RubriqueVeille(m.identifiant(), r.identifiant(), r.libelle()));
            }
        }
        return rubriques;
    }
}
