package io.argos.api.preference;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.catalog.miseenpage.CatalogueMisesEnPage;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.workspace.preference.PreferencesUtilisateur;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Confronte les préférences enregistrées au catalogue (INV-2, AFF-04, DF-AFF-1).
 *
 * <p>Une mise en page absente ou retirée laisse place à {@code classique} ; un profil retiré
 * est ignoré. Si aucun profil enregistré n'est publié, ou si l'utilisateur n'a jamais choisi,
 * les profils par défaut de la plateforme ({@code argos.preferences.profils-par-defaut})
 * s'appliquent, ce qui garantit INV-1.
 */
@ApplicationScoped
public class ResolutionPreferences {

    static final String MESSAGE_MISE_EN_PAGE_RETIREE =
            "Votre mise en page « %s » n'est plus disponible : la mise en page « %s » s'applique.";

    private final CatalogueMisesEnPage misesEnPage;
    private final CatalogueProfils profils;
    private final List<String> profilsParDefaut;

    @Inject
    public ResolutionPreferences(CatalogueMisesEnPage misesEnPage, CatalogueProfils profils,
            @ConfigProperty(name = "argos.preferences.profils-par-defaut") List<String> profilsParDefaut) {
        this.misesEnPage = misesEnPage;
        this.profils = profils;
        this.profilsParDefaut = profilsParDefaut.stream().filter(profils::estPublie).toList();
        if (this.profilsParDefaut.isEmpty()) {
            throw new IllegalStateException(
                    "argos.preferences.profils-par-defaut doit désigner au moins un profil publié : " + profilsParDefaut);
        }
    }

    public PreferencesEffectives resoudre(PreferencesUtilisateur enregistrees) {
        String choisie = enregistrees.miseEnPage().orElse(null);
        boolean indisponible = choisie != null && !misesEnPage.estPubliee(choisie);
        String appliquee = choisie != null && !indisponible ? choisie : CatalogueMisesEnPage.PAR_DEFAUT;
        String message = indisponible
                ? MESSAGE_MISE_EN_PAGE_RETIREE.formatted(choisie, CatalogueMisesEnPage.PAR_DEFAUT)
                : null;

        List<String> publies = new ArrayList<>();
        List<String> ignores = new ArrayList<>();
        for (String profil : enregistrees.profilsActifs()) {
            (profils.estPublie(profil) ? publies : ignores).add(profil);
        }
        boolean parDefaut = publies.isEmpty();
        return new PreferencesEffectives(appliquee, choisie, indisponible, message,
                parDefaut ? profilsParDefaut : List.copyOf(publies), parDefaut, List.copyOf(ignores));
    }
}
