package io.argos.api.preference;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.workspace.CompteCree;
import io.argos.workspace.preference.PreferencesService;

/**
 * Active le profil de veille par défaut d'un compte à sa création (Q-3, PRF-01, ACC-02) : observe
 * l'événement {@link CompteCree} émis par le module {@code workspace} et enregistre le profil dans
 * les préférences du compte, s'il est publié au catalogue et si le compte n'a encore aucun profil.
 *
 * <p>Cette activation vit dans {@code api} : seules {@code api} et {@code mcp} lisent les
 * préférences, qui ne participent à aucune décision d'autorisation (PreferencesIsolationTest).
 */
@ApplicationScoped
public class ActivationProfilParDefaut {

    private static final Logger LOG = Logger.getLogger(ActivationProfilParDefaut.class);

    @Inject
    CatalogueProfils catalogue;

    @Inject
    PreferencesService preferences;

    void activer(@Observes CompteCree evenement) {
        String profil = evenement.profilParDefaut();
        if (!catalogue.estPublie(profil)) {
            LOG.warnf("Profil par défaut « %s » absent du catalogue : aucun profil activé pour le compte %s",
                    profil, evenement.compte().id());
            return;
        }
        try {
            preferences.activerSiAucun(evenement.compte().id(), Set.of(profil));
        } catch (RuntimeException e) {
            // La création du compte n'échoue pas : les profils par défaut de la plateforme s'appliquent à la lecture.
            LOG.errorf(e, "Activation du profil « %s » impossible pour le compte %s", profil, evenement.compte().id());
        }
    }
}
