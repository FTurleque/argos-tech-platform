package io.argos.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.argos.inventory.ImportPlanifie.Categorie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** INV-07 : chaque issue d'import a une rubrique du bilan ; aucune valeur n'est comptée par défaut. */
class ImportPlanifieCategorieTest {

    @Test
    void chaqueIssueAUneRubrique() {
        assertEquals(Categorie.IMPORTES, ImportPlanifie.categorie(StatutImport.IMPORTED));
        assertEquals(Categorie.REFUSES, ImportPlanifie.categorie(StatutImport.REJECTED));
        assertEquals(Categorie.ERREURS, ImportPlanifie.categorie(StatutImport.ERROR));
        assertEquals(Categorie.REPORTES, ImportPlanifie.categorie(StatutImport.DEFERRED));
    }

    @Test
    void inventaireConserveEtAbsenceDeSourceSontComptesConserves() {
        assertEquals(Categorie.CONSERVES, ImportPlanifie.categorie(StatutImport.KEPT));
        assertEquals(Categorie.CONSERVES, ImportPlanifie.categorie(StatutImport.NONE));
    }

    @ParameterizedTest
    @EnumSource(StatutImport.class)
    void touteIssueEstClassee(StatutImport statut) {
        assertNotNull(ImportPlanifie.categorie(statut));
    }
}
