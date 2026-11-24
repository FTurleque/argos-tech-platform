package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.TacheNonObservee;
import io.argos.observabilite.TacheObservee;
import io.quarkus.scheduler.Scheduled;

/**
 * Toute tâche planifiée est observée (EXP-13-S2, EXP-14-S1, EXP-15, arc42 §8.6) : une méthode {@code @Scheduled}
 * porte {@code @TacheObservee}, sans quoi ses exécutions n'auraient ni corrélation, ni métriques, ni état de santé.
 */
class TachesObserveesTest {

    static ArchRule regle() {
        return methods().that().areAnnotatedWith(Scheduled.class)
                .should().beAnnotatedWith(TacheObservee.class)
                .because("arc42 §8.6 : chaque exécution planifiée est corrélée, mesurée et suivie par le contrôle de santé");
    }

    @Test
    void chaqueTachePlanifieeEstObservee() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
        regle().check(production);
    }

    @Test
    void laRegleRefuseUneTacheNonObservee() {
        JavaClasses exemple = new ClassFileImporter().importClasses(TacheNonObservee.class);
        assertThrows(AssertionError.class, () -> regle().check(exemple));
    }
}
