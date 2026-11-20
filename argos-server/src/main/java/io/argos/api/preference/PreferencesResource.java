package io.argos.api.preference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.argos.api.Erreur;
import io.argos.api.UtilisateurCourant;
import io.argos.catalog.miseenpage.CatalogueMisesEnPage;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.workspace.preference.AucunProfilActifException;
import io.argos.workspace.preference.PreferencesService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Préférences de l'utilisateur courant : mise en page (AFF-01, AFF-03, AFF-04) et profils de
 * veille actifs (PRF-01).
 *
 * <p>Les préférences sont enregistrées côté serveur : le portail web et le client desktop lisent
 * la même valeur. Elles ne modifient aucun droit (PRF-05).
 */
@Path("/api/v1/preferences")
@Tag(name = "Préférences")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PreferencesResource {

    @Schema(name = "ChoixMiseEnPage")
    public record ChoixMiseEnPage(String miseEnPage) {
    }

    @Schema(name = "ChoixProfils")
    public record ChoixProfils(List<String> profilsActifs) {
    }

    static final String MISE_EN_PAGE_INCONNUE = "MISE_EN_PAGE_INCONNUE";
    static final String PROFIL_INCONNU = "PROFIL_INCONNU";
    static final String AU_MOINS_UN_PROFIL = "AU_MOINS_UN_PROFIL";

    private final UtilisateurCourant utilisateur;
    private final PreferencesService preferences;
    private final ResolutionPreferences resolution;
    private final CatalogueMisesEnPage misesEnPage;
    private final CatalogueProfils profils;

    @Inject
    public PreferencesResource(UtilisateurCourant utilisateur, PreferencesService preferences,
            ResolutionPreferences resolution, CatalogueMisesEnPage misesEnPage, CatalogueProfils profils) {
        this.utilisateur = utilisateur;
        this.preferences = preferences;
        this.resolution = resolution;
        this.misesEnPage = misesEnPage;
        this.profils = profils;
    }

    @GET
    @Operation(summary = "Préférences appliquées à l'utilisateur courant")
    public PreferencesEffectives lire() {
        return resolution.resoudre(preferences.lire(compte()));
    }

    @PUT
    @Path("/mise-en-page")
    @Operation(summary = "Choisir sa mise en page parmi celles publiées au catalogue")
    @APIResponse(responseCode = "200", description = "Mise en page enregistrée",
            content = @Content(schema = @Schema(implementation = PreferencesEffectives.class)))
    @APIResponse(responseCode = "400", description = "Mise en page absente du catalogue",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public PreferencesEffectives choisirMiseEnPage(ChoixMiseEnPage choix) {
        String demandee = choix == null ? null : choix.miseEnPage();
        if (!misesEnPage.estPubliee(demandee)) {
            throw Erreur.demandeRefusee(MISE_EN_PAGE_INCONNUE,
                    "La mise en page « " + demandee + " » n'existe pas dans le catalogue.");
        }
        return resolution.resoudre(preferences.choisirMiseEnPage(compte(), demandee));
    }

    @PUT
    @Path("/profils")
    @Operation(summary = "Remplacer l'ensemble de ses profils de veille actifs")
    @APIResponse(responseCode = "200", description = "Profils actifs enregistrés",
            content = @Content(schema = @Schema(implementation = PreferencesEffectives.class)))
    @APIResponse(responseCode = "400", description = "Profil absent du catalogue, ou aucun profil actif",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public PreferencesEffectives remplacerProfils(ChoixProfils choix) {
        Set<String> demandes = new LinkedHashSet<>(choix == null || choix.profilsActifs() == null
                ? List.of() : choix.profilsActifs());
        for (String profil : demandes) {
            if (!profils.estPublie(profil)) {
                throw Erreur.demandeRefusee(PROFIL_INCONNU,
                        "Le profil « " + profil + " » n'existe pas dans le catalogue.");
            }
        }
        try {
            return resolution.resoudre(preferences.remplacerProfils(compte(), demandes));
        } catch (AucunProfilActifException refus) {
            throw Erreur.demandeRefusee(AU_MOINS_UN_PROFIL, refus.getMessage());
        }
    }

    /** Clé des préférences : identifiant stable du compte Argos, relié au sujet OIDC (jamais le nom du principal). */
    private UUID compte() {
        return utilisateur.compte().id();
    }
}
