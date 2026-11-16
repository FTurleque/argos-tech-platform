package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Les préférences (profils actifs, mise en page) ne servent jamais à décider d'une
 * autorisation (INV-3, PRF-05, ADR-0024 point 6).
 *
 * <p>Seuls {@code api} et {@code mcp} lisent {@code io.argos.workspace.preference} ; le reste de
 * {@code workspace}, où vivent équipes et rôles, n'en dépend pas, et les préférences ne
 * dépendent pas du reste de {@code workspace}.
 */
class PreferencesIsolationTest {

    private static final String PREFERENCES = "io.argos.workspace.preference..";

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
    }

    @Test
    void seulsApiEtMcpLisentLesPreferences() {
        noClasses().that().resideOutsideOfPackages(PREFERENCES, "io.argos.api..", "io.argos.mcp..")
                .should().dependOnClassesThat().resideInAPackage(PREFERENCES)
                .because("une préférence ne participe à aucune décision d'autorisation (PRF-05)")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void lesPreferencesNeDependentPasDuResteDeWorkspace() {
        noClasses().that().resideInAPackage(PREFERENCES)
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "workspace hors préférences",
                        c -> c.getPackageName().startsWith("io.argos.workspace")
                                && !c.getPackageName().startsWith("io.argos.workspace.preference")))
                .because("les préférences ne lisent ni rôles ni équipes (INV-3)")
                .allowEmptyShould(true)
                .check(classes);
    }
}
