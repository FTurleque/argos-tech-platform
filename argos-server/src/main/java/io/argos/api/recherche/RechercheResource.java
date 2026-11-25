package io.argos.api.recherche;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.Erreur;
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

    /** Description OpenAPI du paramètre {@code page} des résultats, partagée avec {@link VuesResource}. */
    static final String PAGE = "Numéro de page, à partir de 0 ; une valeur négative ou non entière donne 400";

    /** Description OpenAPI du paramètre {@code taille} des résultats, partagée avec {@link VuesResource}. */
    static final String TAILLE = "Taille de page, de 1 à 100 ; une valeur hors bornes ou non entière donne 400";

    /** Taille de page des résultats quand {@code taille} est absent. */
    static final int TAILLE_PAR_DEFAUT = 20;

    @Inject
    Access access;

    @Inject
    TraductionCriteres traduction;

    @Inject
    LimiteRecherche limite;

    @GET
    @Operation(summary = "Recherche plein texte (français et anglais) et filtres combinables, du plus pertinent au moins pertinent")
    @APIResponse(responseCode = "200", description = "Page de résultats ; liste vide et message explicite si rien ne correspond")
    @APIResponse(responseCode = "400", description = "Filtre inconnu ou mal formé, paramètre page ou taille non entier ou hors bornes")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte, en-tête Retry-After",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "404", description = "Projet filtré inconnu ou d'un espace dont l'appelant n'est pas membre"
            + " (même réponse, second refus journalisé), y compris avec un paramètre page ou taille non entier ou hors bornes :"
            + " les projets filtrés sont contrôlés avant la lecture de la pagination")
    public RechercheVeille.Resultats rechercher(@BeanParam ParametresRecherche parametres) {
        Caller appelant = access.authenticated();
        limite.admettre(appelant.accountId());
        CriteresRecherche criteres = new CriteresRecherche(parametres.texte, uuids(parametres.projets),
                nonVide(parametres.technologies), nonVide(parametres.types), parametres.gravite, parametres.horizon,
                nonVide(parametres.profils));
        return traduction.executer(criteres, appelant, true, parametres.page, parametres.taille);
    }

    private static List<String> nonVide(List<String> valeurs) {
        return valeurs == null || valeurs.isEmpty() ? null : valeurs;
    }

    private static List<UUID> uuids(List<String> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) {
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        for (String valeur : valeurs) {
            try {
                ids.add(UUID.fromString(valeur.strip()));
            } catch (IllegalArgumentException | NullPointerException malforme) {
                throw new DonneeInvalide("CRITERE_INVALIDE", "Identifiant de projet UUID attendu.", "projet");
            }
        }
        return ids;
    }
}
