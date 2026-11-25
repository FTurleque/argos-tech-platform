package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.TransactionManuelle;

/**
 * Invariant NC-2 (ADR-0037, arc42 §8.7) : une seule manière d'ouvrir une transaction, JTA ; aucune classe du serveur
 * ne gère l'autocommit d'une connexion ni ne la valide ou ne l'annule.
 */
class TransactionsJtaTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
    }

    static ArchRule regle() {
        return noClasses()
                .should().callMethod(Connection.class, "setAutoCommit", boolean.class)
                .orShould().callMethod(Connection.class, "commit")
                .orShould().callMethod(Connection.class, "rollback")
                .orShould().callMethod(Connection.class, "rollback", java.sql.Savepoint.class)
                .because("ADR-0037 : le modèle transactionnel est JTA ; aucune transaction manuelle sur une connexion");
    }

    @Test
    void aucuneClasseNeGereLaTransactionDUneConnexion() {
        regle().check(classes);
    }

    /** La règle refuse la gestion manuelle de l'autocommit, de la validation et de l'annulation. */
    @Test
    void laRegleRefuseUneTransactionManuelle() {
        JavaClasses exemple = new ClassFileImporter().importClasses(TransactionManuelle.class);
        assertThrows(AssertionError.class, () -> regle().check(exemple));
    }
}
