package io.argos.api.recherche;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.impact.RechercheVeille;
import io.argos.workspace.DonneeInvalide;

/**
 * Recherche plein texte et filtres combinables sur la veille de l'appelant (RCH-01 à RCH-03) : signaux
 * de sécurité et actualités web de ses espaces et de son périmètre personnel, avec les seuls projets
 * qu'il voit. Le titre, l'extrait et le lien de chaque élément sont des données externes à rendre comme
 * texte ; seuls les liens http et https sont restitués.
 */
@Path("/api/v1/recherche")
@Tag(name = "Recherche")
@Produces(MediaType.APPLICATION_JSON)
public class RechercheResource {

    @Inject
    Access access;

    @Inject
    TraductionCriteres traduction;

    @GET
    @Operation(summary = "Recherche plein texte (français et anglais) et filtres combinables, du plus pertinent au moins pertinent")
    @APIResponse(responseCode = "200", description = "Page de résultats ; liste vide et message explicite si rien ne correspond")
    @APIResponse(responseCode = "400", description = "Filtre inconnu ou mal formé")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre")
    public RechercheVeille.Resultats rechercher(
            @Parameter(description = "Mots recherchés (syntaxe web : mots, \"expression\", or, -exclusion)") @QueryParam("q") String texte,
            @Parameter(description = "Identifiant de projet (répétable)") @QueryParam("projet") List<String> projets,
            @Parameter(description = "Technologie (répétable)") @QueryParam("technologie") List<String> technologies,
            @Parameter(description = "AVIS_SECURITE, ARTICLE ou CHANGEMENT_PAGE (répétable)") @QueryParam("type") List<String> types,
            @Parameter(description = "Gravité minimale : LOW, MODERATE, MEDIUM, HIGH, CRITICAL") @QueryParam("gravite") String gravite,
            @Parameter(description = "Éléments des N derniers jours (1 à 3 650)") @QueryParam("horizon") Integer horizon,
            @Parameter(description = "Profil de veille (répétable)") @QueryParam("profil") List<String> profils,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("taille") @DefaultValue("20") int taille) {
        Caller appelant = access.authenticated();
        CriteresRecherche criteres = new CriteresRecherche(texte, uuids(projets), nonVide(technologies), nonVide(types),
                gravite, horizon, nonVide(profils));
        return traduction.executer(criteres, appelant, true, page, taille);
    }

    private static List<String> nonVide(List<String> valeurs) {
        return valeurs == null || valeurs.isEmpty() ? null : valeurs;
    }

    private static List<UUID> uuids(List<String> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) {
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        for (String v : valeurs) {
            try {
                ids.add(UUID.fromString(v.strip()));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new DonneeInvalide("CRITERE_INVALIDE", "Identifiant de projet UUID attendu.", "projet");
            }
        }
        return ids;
    }
}
