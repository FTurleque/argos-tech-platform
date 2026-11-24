package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.SommeilFixe;

/**
 * Tests indépendants du temps réel (AUD-TST-06, design.md D2 et Q-3 du changement {@code injecter-horloge-et-executeurs},
 * arc42 §8.11) : aucun test ne dort. Une attente se borne par Awaitility sur une condition observable (verrou, requête
 * reçue, journal écrit) ; un serveur lent se simule par une réponse retenue ; le temps se règle par une horloge réglable.
 * Les classes d'exemple des règles d'architecture sont exclues.
 */
class TestsSansSommeilTest {

    private static final String EXEMPLES = "io.argos.architecture.exemples..";

    static ArchRule regle() {
        return noClasses().that().resideOutsideOfPackage(EXEMPLES)
                .should().callMethod(Thread.class, "sleep", long.class)
                .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
                .because("arc42 §8.11 : une assertion ne dépend ni d'un sommeil fixe ni de l'horloge réelle");
    }

    @Test
    void aucunTestNeDort() {
        JavaClasses tests = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("io.argos");
        regle().check(tests);
    }

    /** La condition détecte un sommeil fixe ; la règle n'exclut que les classes d'exemple. */
    @Test
    void laConditionRefuseUnSommeilEtLaRegleExclutLesExemples() {
        JavaClasses exemple = new ClassFileImporter().importClasses(SommeilFixe.class);
        assertThrows(AssertionError.class, () -> noClasses().should().callMethod(Thread.class, "sleep", long.class)
                .check(exemple));
        regle().allowEmptyShould(true).check(exemple);
    }
}
