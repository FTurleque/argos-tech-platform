package io.argos.workspace.preference;

import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Règles propres aux préférences d'un utilisateur (DC-1).
 *
 * <p>Applique INV-1 (au moins un profil actif). La confrontation au catalogue (INV-2)
 * est faite par l'appelant avant l'appel.
 */
@ApplicationScoped
public class PreferencesService {

    private final PreferencesRepository repository;

    @Inject
    public PreferencesService(PreferencesRepository repository) {
        this.repository = repository;
    }

    public PreferencesUtilisateur lire(String utilisateur) {
        return repository.lire(Objects.requireNonNull(utilisateur));
    }

    /** Remplace la mise en page de l'utilisateur. */
    public PreferencesUtilisateur choisirMiseEnPage(String utilisateur, String miseEnPage) {
        repository.enregistrerMiseEnPage(Objects.requireNonNull(utilisateur), Objects.requireNonNull(miseEnPage));
        return repository.lire(utilisateur);
    }

    /**
     * Remplace l'ensemble des profils actifs.
     *
     * @throws AucunProfilActifException si l'ensemble est vide (PRF-01-S2)
     */
    public PreferencesUtilisateur remplacerProfils(String utilisateur, Set<String> profilsActifs) {
        Objects.requireNonNull(utilisateur);
        if (profilsActifs == null || profilsActifs.isEmpty()) {
            throw new AucunProfilActifException();
        }
        repository.enregistrerProfils(utilisateur, profilsActifs);
        return repository.lire(utilisateur);
    }
}
