package io.argos.workspace;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.commun.DonneeInvalide;

/**
 * Saisies refusées par le service des espaces avant tout accès à la base (ACC-03, ACC-05) : nom d'espace vide, blanc
 * ou trop long, adresse d'invitation invalide, rôle absent. Le service est construit sans base : une saisie qui
 * l'atteindrait échouerait autrement que par {@link DonneeInvalide}.
 */
class EspacesServiceUnitaireTest {

    private static final int LONGUEUR_MAX_NOM = 120;

    private final EspacesService espaces = new EspacesService();
    private final Compte alice = new Compte(UUID.randomUUID(), "alice", "alice", "alice@argos.test", false, null);
    private final UUID espace = UUID.randomUUID();

    @Test
    void nomDEspaceVideBlancOuTropLongRefuse() {
        for (String nom : new String[] {null, "", "   ", "n".repeat(LONGUEUR_MAX_NOM + 1)}) {
            assertThrows(DonneeInvalide.class, () -> espaces.creer(alice, nom), String.valueOf(nom));
        }
    }

    @Test
    void invitationAdresseInvalideOuSansRoleRefusee() {
        for (String adresse : new String[] {null, "", "sans-arobase", "deux@@argos.test", "a@b"}) {
            assertThrows(DonneeInvalide.class, () -> espaces.inviter(alice, espace, adresse, Role.LECTEUR), String.valueOf(adresse));
        }
        assertThrows(DonneeInvalide.class, () -> espaces.inviter(alice, espace, "bob@argos.test", null));
    }

    @Test
    void changementDeRoleSansRoleRefuse() {
        assertThrows(DonneeInvalide.class, () -> espaces.changerRole(alice, espace, alice.id(), null));
    }
}
