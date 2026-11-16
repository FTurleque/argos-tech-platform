package io.argos.workspace.preference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Préférences enregistrées d'un utilisateur, telles que stockées (DC-1).
 *
 * <p>Les valeurs ne sont pas confrontées au catalogue : une mise en page ou un profil
 * retiré depuis le choix reste enregistré, et c'est l'appelant qui applique le défaut (INV-2).
 *
 * @param compte        identifiant stable du compte Argos (relié au sujet OIDC)
 * @param miseEnPage    mise en page choisie, vide si l'utilisateur n'a jamais choisi
 * @param profilsActifs profils actifs choisis, vide si l'utilisateur n'a jamais choisi
 */
public record PreferencesUtilisateur(UUID compte, Optional<String> miseEnPage, List<String> profilsActifs) {

    public PreferencesUtilisateur {
        profilsActifs = List.copyOf(profilsActifs);
    }

    /** Préférences d'un utilisateur qui n'a encore rien choisi. */
    public static PreferencesUtilisateur aucune(UUID compte) {
        return new PreferencesUtilisateur(compte, Optional.empty(), List.of());
    }
}
