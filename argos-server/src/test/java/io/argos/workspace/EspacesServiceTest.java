package io.argos.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.commun.AccesRefuse;
import io.argos.commun.DonneeInvalide;
import io.argos.commun.Introuvable;
import io.argos.commun.MotifRefus;
import io.argos.commun.RegleViolee;
import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Cas limites des espaces, rôles et invitations, au niveau du service (ACC-03, ACC-05, INV-1) : nom
 * d'espace, dernier administrateur, membre inconnu, invitation invalide, révoquée, expirée ou adressée
 * à une autre personne, compte déjà membre.
 */
@QuarkusTest
class EspacesServiceTest {

    private static final int LONGUEUR_MAX_NOM = 120;
    private static final String EMAIL_BOB = "bob@argos.test";
    private static final String EMAIL_CARLA = "carla@argos.test";

    @Inject
    EspacesService espaces;

    @Inject
    TestDb baseDeTest;

    private Compte alice;
    private Compte bob;
    private Compte carla;

    @BeforeEach
    void preparer() {
        alice = compte("alice");
        bob = compte("bob");
        carla = compte("carla");
    }

    @Test
    void nomDEspaceVideBlancOuTropLongRefuse() {
        for (String nom : new String[] {null, "", "   ", "n".repeat(LONGUEUR_MAX_NOM + 1)}) {
            assertThrows(DonneeInvalide.class, () -> espaces.creer(alice, nom), String.valueOf(nom));
        }
        EspacesService.EspaceDeCompte espace = espaces.creer(alice, "  Espace rogné  ");
        assertEquals("Espace rogné", espace.nom());
        assertEquals(Role.ADMINISTRATEUR, espace.role());
    }

    @Test
    void dernierAdministrateurNiRetrogradeNiRetire() {
        UUID espace = espaces.creer(alice, "Dernier administrateur").id();

        RegleViolee retrogradation = assertThrows(RegleViolee.class,
                () -> espaces.changerRole(alice, espace, alice.id(), Role.CONTRIBUTEUR));
        assertEquals("DERNIER_ADMINISTRATEUR", retrogradation.code());
        RegleViolee retrait = assertThrows(RegleViolee.class, () -> espaces.revoquer(alice, espace, alice.id()));
        assertEquals("DERNIER_ADMINISTRATEUR", retrait.code());

        rejoindre(bob, EMAIL_BOB, espace, Role.ADMINISTRATEUR);
        EspacesService.Membre retrograde = espaces.changerRole(alice, espace, alice.id(), Role.LECTEUR);
        assertEquals(Role.LECTEUR, retrograde.role());
        assertEquals(Role.ADMINISTRATEUR, espaces.exiger(bob, espace, Role.ADMINISTRATEUR));
    }

    @Test
    void administrateurConfirmeGardeSonRoleEtRoleObligatoire() {
        UUID espace = espaces.creer(alice, "Rôle maintenu").id();

        assertEquals(Role.ADMINISTRATEUR, espaces.changerRole(alice, espace, alice.id(), Role.ADMINISTRATEUR).role());
        assertThrows(DonneeInvalide.class, () -> espaces.changerRole(alice, espace, alice.id(), null));
    }

    @Test
    void membreInconnuRefuse() {
        UUID espace = espaces.creer(alice, "Membre inconnu").id();

        assertEquals("MEMBRE_INCONNU",
                assertThrows(RegleViolee.class, () -> espaces.changerRole(alice, espace, carla.id(), Role.LECTEUR)).code());
        assertEquals("MEMBRE_INCONNU", assertThrows(RegleViolee.class, () -> espaces.revoquer(alice, espace, carla.id())).code());
    }

    @Test
    void roleInsuffisantEtNonMembreDistingues() {
        UUID espace = espaces.creer(alice, "Rôles distingués").id();
        rejoindre(bob, EMAIL_BOB, espace, Role.CONTRIBUTEUR);

        assertEquals(MotifRefus.ROLE_INSUFFISANT,
                assertThrows(AccesRefuse.class, () -> espaces.invitations(bob, espace)).motif());
        assertEquals(MotifRefus.NON_MEMBRE, assertThrows(AccesRefuse.class, () -> espaces.obtenir(carla, espace)).motif());
        assertEquals(MotifRefus.NON_MEMBRE,
                assertThrows(AccesRefuse.class, () -> espaces.membres(carla, UUID.randomUUID())).motif());
        assertEquals(Role.CONTRIBUTEUR, espaces.exiger(bob, espace, Role.LECTEUR));
        assertEquals(2, espaces.membres(bob, espace).size());
    }

    @Test
    void retraitDUnMembreEffectifDesLaRequeteSuivante() {
        UUID espace = espaces.creer(alice, "Retrait").id();
        rejoindre(bob, EMAIL_BOB, espace, Role.LECTEUR);

        espaces.revoquer(alice, espace, bob.id());

        assertEquals(MotifRefus.NON_MEMBRE, assertThrows(AccesRefuse.class, () -> espaces.obtenir(bob, espace)).motif());
        assertTrue(espaces.mesEspaces(bob).stream().noneMatch(vue -> vue.id().equals(espace)));
    }

    @Test
    void invitationAdresseInvalideOuSansRoleRefusee() {
        UUID espace = espaces.creer(alice, "Invitations invalides").id();

        for (String adresse : new String[] {null, "", "sans-arobase", "deux@@argos.test", "a@b"}) {
            assertThrows(DonneeInvalide.class, () -> espaces.inviter(alice, espace, adresse, Role.LECTEUR), String.valueOf(adresse));
        }
        assertThrows(DonneeInvalide.class, () -> espaces.inviter(alice, espace, EMAIL_BOB, null));
        EspacesService.Invitation normalisee = espaces.inviter(alice, espace, "  Bob@Argos.TEST ", Role.LECTEUR);
        assertEquals(EMAIL_BOB, normalisee.email());
        assertEquals("EN_ATTENTE", normalisee.etat());
    }

    @Test
    void invitationRevoqueeNeSeRevoqueNiNeSAccepteUneSecondeFois() {
        UUID espace = espaces.creer(alice, "Invitation révoquée").id();
        UUID invitation = espaces.inviter(alice, espace, EMAIL_BOB, Role.LECTEUR).id();

        espaces.revoquerInvitation(alice, espace, invitation);

        assertEquals("INVITATION_INACTIVE",
                assertThrows(RegleViolee.class, () -> espaces.revoquerInvitation(alice, espace, invitation)).code());
        assertEquals("INVITATION_INACTIVE",
                assertThrows(RegleViolee.class, () -> espaces.accepter(bob, EMAIL_BOB, invitation)).code());
        assertTrue(espaces.mesInvitations(bob, EMAIL_BOB).stream().noneMatch(vue -> vue.id().equals(invitation)));
    }

    @Test
    void invitationExpireeRefusee() {
        UUID espace = espaces.creer(alice, "Invitation expirée").id();
        UUID invitation = espaces.inviter(alice, espace, EMAIL_BOB, Role.LECTEUR).id();
        baseDeTest.exec("UPDATE invitation SET expire_le = now() - interval '1 day' WHERE id = ?::uuid", invitation.toString());

        assertEquals("INVITATION_EXPIREE", assertThrows(RegleViolee.class, () -> espaces.accepter(bob, EMAIL_BOB, invitation)).code());
        assertTrue(espaces.mesInvitations(bob, EMAIL_BOB).stream().noneMatch(vue -> vue.id().equals(invitation)));
    }

    @Test
    void invitationAdresseeAUneAutrePersonneOuSansEmailVerifieIntrouvable() {
        UUID espace = espaces.creer(alice, "Invitation d'autrui").id();
        UUID invitation = espaces.inviter(alice, espace, EMAIL_BOB, Role.LECTEUR).id();

        assertThrows(Introuvable.class, () -> espaces.accepter(carla, EMAIL_CARLA, invitation));
        assertThrows(Introuvable.class, () -> espaces.accepter(bob, null, invitation));
        assertThrows(Introuvable.class, () -> espaces.accepter(bob, EMAIL_BOB, UUID.randomUUID()));
        assertEquals(List.of(), espaces.mesInvitations(bob, null));
    }

    @Test
    void compteDejaMembreNeRejointPasDeuxFois() {
        UUID espace = espaces.creer(alice, "Déjà membre").id();
        rejoindre(bob, EMAIL_BOB, espace, Role.LECTEUR);
        UUID seconde = espaces.inviter(alice, espace, EMAIL_BOB, Role.CONTRIBUTEUR).id();

        assertEquals("DEJA_MEMBRE", assertThrows(RegleViolee.class, () -> espaces.accepter(bob, EMAIL_BOB, seconde)).code());
        assertEquals(Role.LECTEUR, espaces.exiger(bob, espace, Role.LECTEUR));
    }

    @Test
    void ordreDesRoles() {
        assertTrue(Role.ADMINISTRATEUR.auMoins(Role.CONTRIBUTEUR));
        assertTrue(Role.CONTRIBUTEUR.auMoins(Role.CONTRIBUTEUR));
        assertFalse(Role.LECTEUR.auMoins(Role.CONTRIBUTEUR));
    }

    private void rejoindre(Compte membre, String email, UUID espace, Role role) {
        UUID invitation = espaces.inviter(alice, espace, email, role).id();
        EspacesService.EspaceDeCompte rejoint = espaces.accepter(membre, email, invitation);
        assertEquals(role, rejoint.role());
    }

    private static Compte compte(String utilisateur) {
        return new Compte(Api.compte(utilisateur), utilisateur, utilisateur, utilisateur + "@argos.test", false, null);
    }
}
