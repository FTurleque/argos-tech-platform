package io.argos.workspace.preference;

import java.util.List;
import java.util.Optional;

/**
 * Préférences enregistrées d'un utilisateur, telles que stockées (DC-1).
 *
 * <p>Les valeurs ne sont pas confrontées au catalogue : une mise en page ou un profil
 * retiré depuis le choix reste enregistré, et c'est l'appelant qui applique le défaut (INV-2).
 *
 * @param utilisateur   identifiant stable de l'utilisateur (sujet de son identité)
 * @param miseEnPage    mise en page choisie, vide si l'utilisateur n'a jamais choisi
 * @param profilsActifs profils actifs choisis, vide si l'utilisateur n'a jamais choisi
 */
public record PreferencesUtilisateur(String utilisateur, Optional<String> miseEnPage, List<String> profilsActifs) {

    public PreferencesUtilisateur {
        profilsActifs = List.copyOf(profilsActifs);
    }

    /** Préférences d'un utilisateur qui n'a encore rien choisi. */
    public static PreferencesUtilisateur aucune(String utilisateur) {
        return new PreferencesUtilisateur(utilisateur, Optional.empty(), List.of());
    }
}
