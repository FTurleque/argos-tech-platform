package io.argos.workspace.preference;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

    public PreferencesUtilisateur lire(UUID compte) {
        return repository.lire(Objects.requireNonNull(compte));
    }

    /** Remplace la mise en page de l'utilisateur. */
    public PreferencesUtilisateur choisirMiseEnPage(UUID compte, String miseEnPage) {
        repository.enregistrerMiseEnPage(Objects.requireNonNull(compte), Objects.requireNonNull(miseEnPage));
        return repository.lire(compte);
    }

    /**
     * Remplace l'ensemble des profils actifs.
     *
     * @throws AucunProfilActifException si l'ensemble est vide (PRF-01-S2)
     */
    public PreferencesUtilisateur remplacerProfils(UUID compte, Set<String> profilsActifs) {
        Objects.requireNonNull(compte);
        if (profilsActifs == null || profilsActifs.isEmpty()) {
            throw new AucunProfilActifException();
        }
        repository.enregistrerProfils(compte, profilsActifs);
        return repository.lire(compte);
    }

    /**
     * Active des profils pour un compte qui n'en a encore enregistré aucun (profil par défaut à la
     * création du compte, PRF-01) ; sans effet si le compte a déjà choisi ses profils.
     */
    public void activerSiAucun(UUID compte, Set<String> profilsActifs) {
        Objects.requireNonNull(compte);
        if (profilsActifs == null || profilsActifs.isEmpty()) {
            throw new AucunProfilActifException();
        }
        repository.enregistrerProfilsSiAucun(compte, profilsActifs);
    }

    /** Parmi les comptes donnés, ceux qui ont activé le profil (lecture technique pour les alertes). */
    public Set<UUID> comptesAvecProfil(Collection<UUID> comptes, String profil) {
        return repository.comptesAvecProfil(Objects.requireNonNull(comptes), Objects.requireNonNull(profil));
    }
}
