package io.argos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.argos.commun.DonneeInvalide;
import io.argos.commun.Pagination;

/**
 * Lecture des paramètres de pagination des listes de l'API REST (INV-06, décision G4 du porteur du projet,
 * 2026-11-19) : une valeur non entière est refusée par {@code DONNEE_INVALIDE} avec l'attribut en cause ; une valeur
 * absente ou vide prend la valeur par défaut ; un entier hors des bornes de {@code int} est saturé, puis soumis aux
 * bornes de la liste. Le câblage dans chaque liste est vérifié par {@code ParametresPaginationTest}.
 */
class ParametresPaginationUnitaireTest {

    private static final int DEFAUT = 50;
    private static final int SAISIE_SIGNEE = 5;
    private static final int SAISIE_ENTOUREE = 7;

    private static void refuse(String valeur, String attribut) {
        DonneeInvalide erreur = assertThrows(DonneeInvalide.class, () -> ParametresPagination.entier(valeur, attribut, DEFAUT),
                valeur);
        assertEquals("DONNEE_INVALIDE", erreur.code());
        assertEquals(attribut, erreur.attribut());
    }

    @Test
    void valeurNonEntiereRefuseeAvecLAttribut() {
        for (String valeur : List.of("abc", "1.5", "1e2", "dix", "x", "0x10", "1 000", "--1")) {
            refuse(valeur, "page");
        }
        refuse("1.5", "size");
        refuse("dix", "taille");
        refuse("abc", "limite");
    }

    @Test
    void valeurAbsenteOuVideDonneLeDefaut() {
        assertEquals(DEFAUT, ParametresPagination.entier(null, "limite", DEFAUT));
        assertEquals(DEFAUT, ParametresPagination.entier("", "limite", DEFAUT));
        assertEquals(DEFAUT, ParametresPagination.entier("   ", "limite", DEFAUT));
    }

    @Test
    void entierSigneOuEntoureDEspacesAccepte() {
        assertEquals(SAISIE_SIGNEE, ParametresPagination.entier("+5", "page", 0));
        assertEquals(-SAISIE_SIGNEE, ParametresPagination.entier("-5", "page", 0));
        assertEquals(SAISIE_ENTOUREE, ParametresPagination.entier(" 7 ", "size", DEFAUT));
    }

    /** Un entier hors des bornes de {@code int} est saturé, puis borné comme toute autre valeur hors bornes. */
    @Test
    void entierHorsDesBornesDeIntSature() {
        assertEquals(Integer.MAX_VALUE, ParametresPagination.entier("99999999999", "limite", DEFAUT));
        assertEquals(Integer.MIN_VALUE, ParametresPagination.entier("-99999999999", "page", 0));
        Pagination saturee = ParametresPagination.pagination("-99999999999", "99999999999");
        assertEquals(0, saturee.page());
        assertEquals(Pagination.TAILLE_MAX, saturee.taille());
    }

    /** ADR-0040 : demande de page lue dans {@code page} et {@code size}, première page à la taille par défaut sinon. */
    @Test
    void paginationParDefautEtAttributsEnCause() {
        assertEquals(Pagination.PREMIERE, ParametresPagination.pagination(null, ""));
        assertEquals(new Pagination(2, SAISIE_ENTOUREE), ParametresPagination.pagination("2", "7"));
        assertEquals(ParametresPagination.PAGE, assertThrows(DonneeInvalide.class,
                () -> ParametresPagination.pagination("abc", "1")).attribut());
        assertEquals(ParametresPagination.TAILLE, assertThrows(DonneeInvalide.class,
                () -> ParametresPagination.pagination("0", "1.5")).attribut());
    }
}
