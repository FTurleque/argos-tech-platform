package io.argos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.argos.commun.DonneeInvalide;
import io.argos.inventory.DepotProjet;

/**
 * Saisies de l'administration des projets (INV-12-S6) : une saisie invalide est refusée par {@code DONNEE_INVALIDE}
 * avec l'attribut en cause, avant tout appel au module {@code inventory}.
 */
class SaisieProjetTest {

    private static final int NOM_TROP_LONG = 201;

    private final UUID espace = UUID.randomUUID();

    private static void refuse(Executable saisie, String attribut) {
        DonneeInvalide erreur = assertThrows(DonneeInvalide.class, saisie, attribut);
        assertEquals("DONNEE_INVALIDE", erreur.code());
        assertEquals(attribut, erreur.attribut());
    }

    private static SaisieProjet.Depot depot(String forge, String identifiant) {
        return new SaisieProjet.Depot(forge, identifiant, "main", null, null, null);
    }

    /** INV-12-S6 : nom blanc ou trop long, espace absent, forge inconnue, dépôt absent ou mal formé. */
    @Test
    void inv12S6SaisieInvalide() {
        refuse(() -> SaisieProjet.nom(new SaisieProjet.NouveauProjet(espace, "  ", null)), "nom");
        refuse(() -> SaisieProjet.espace(new SaisieProjet.NouveauProjet(null, "sans espace", null)), "espaceId");
        refuse(() -> SaisieProjet.espace(null), "espaceId");
        refuse(() -> SaisieProjet.depot(depot("BITBUCKET", "org/app")), "forge");
        refuse(() -> SaisieProjet.depot(new SaisieProjet.Depot("GITHUB", null, null, null, null, null)), "depot");
        refuse(() -> SaisieProjet.nom(new SaisieProjet.NouveauProjet(espace, "a".repeat(NOM_TROP_LONG), null)), "nom");
        refuse(() -> SaisieProjet.depot(depot("GITHUB", "sans-proprietaire")), "depot");
        refuse(() -> SaisieProjet.depot(depot("GITHUB", "../..")), "depot");
    }

    /** Saisie valide : nom rogné, forge en majuscules, branche {@code main} par défaut. */
    @Test
    void saisieValideNormalisee() {
        assertEquals("app", SaisieProjet.nom(new SaisieProjet.NouveauProjet(espace, "  app ", null)));
        DepotProjet saisi = SaisieProjet.depot(new SaisieProjet.Depot(" github ", "org/app", null, null, null, null));
        assertEquals("GITHUB", saisi.forge());
        assertEquals("org/app", saisi.depot());
        assertEquals("main", saisi.branche());
    }
}
