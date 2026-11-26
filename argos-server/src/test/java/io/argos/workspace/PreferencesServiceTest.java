package io.argos.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.argos.workspace.preference.AucunProfilActifException;
import io.argos.workspace.preference.PreferencesService;
import io.argos.workspace.preference.PreferencesUtilisateur;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Préférences d'un utilisateur (DC-1, PRF-01) : aucune préférence avant tout choix, ensemble de profils
 * vide refusé, profil par défaut posé une seule fois, mise en page et profils indépendants, lecture
 * technique des comptes ayant activé un profil.
 */
@QuarkusTest
class PreferencesServiceTest {

    private static final String UTILISATEUR = "lina";

    @Inject
    PreferencesService preferences;

    @Inject
    TestDb baseDeTest;

    private UUID compte;

    @BeforeEach
    void preparer() {
        compte = Api.compte(UTILISATEUR);
        effacer();
    }

    @AfterEach
    void effacer() {
        baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = ?::uuid", compte.toString());
    }

    @Test
    void aucunePreferenceAvantToutChoix() {
        assertEquals(PreferencesUtilisateur.aucune(compte), preferences.lire(compte));
    }

    @Test
    void ensembleDeProfilsVideOuAbsentRefuse() {
        assertThrows(AucunProfilActifException.class, () -> preferences.remplacerProfils(compte, Set.of()));
        assertThrows(AucunProfilActifException.class, () -> preferences.remplacerProfils(compte, null));
        assertThrows(AucunProfilActifException.class, () -> preferences.activerSiAucun(compte, Set.of()));
        assertThrows(AucunProfilActifException.class, () -> preferences.activerSiAucun(compte, null));
        assertThrows(NullPointerException.class, () -> preferences.lire(null));
    }

    @Test
    void profilParDefautPoseUneSeuleFois() {
        preferences.activerSiAucun(compte, Set.of("developpeur"));
        preferences.activerSiAucun(compte, Set.of("artiste"));
        assertEquals(List.of("developpeur"), preferences.lire(compte).profilsActifs());

        PreferencesUtilisateur remplacees = preferences.remplacerProfils(compte, Set.of("reglementaire", "artiste"));
        assertEquals(List.of("artiste", "reglementaire"), remplacees.profilsActifs(), "profils triés");
    }

    @Test
    void miseEnPageEtProfilsEnregistresIndependamment() {
        preferences.choisirMiseEnPage(compte, "compacte");
        assertEquals(new PreferencesUtilisateur(compte, Optional.of("compacte"), List.of()), preferences.lire(compte));

        preferences.activerSiAucun(compte, Set.of("developpeur"));
        PreferencesUtilisateur apres = preferences.choisirMiseEnPage(compte, "detaillee");
        assertEquals(new PreferencesUtilisateur(compte, Optional.of("detaillee"), List.of("developpeur")), apres);
    }

    @Test
    void comptesAvecProfilParmiLesComptesDemandes() {
        preferences.remplacerProfils(compte, Set.of("artiste"));

        assertEquals(Set.of(compte), preferences.comptesAvecProfil(List.of(compte, UUID.randomUUID()), "artiste"));
        assertEquals(Set.of(), preferences.comptesAvecProfil(List.of(compte), "reglementaire"));
        assertEquals(Set.of(), preferences.comptesAvecProfil(List.of(), "artiste"));
    }
}
