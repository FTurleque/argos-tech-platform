package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Les objets suivis d'un profil ({@code workspace.suivi} : disciplines, territoires, jours de rappel)
 * n'accordent aucun droit (PRF-05, ADR-0024 point 6) : le paquetage ne lit ni rôles, ni appartenances,
 * ni préférences, et n'emprunte au reste de {@code workspace} que le contexte RLS.
 */
class SuiviIsolationTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
    }

    @Test
    void lesSuivisNEmpruntentAWorkspaceQueLeContexteRls() {
        noClasses().that().resideInAPackage("io.argos.workspace.suivi..")
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "workspace hors suivi et ContexteBase",
                        (JavaClass c) -> c.getPackageName().startsWith("io.argos.workspace")
                                && !c.getPackageName().startsWith("io.argos.workspace.suivi")
                                && !c.getName().startsWith("io.argos.workspace.ContexteBase")))
                .because("un suivi ne lit ni rôles ni appartenances (PRF-05)")
                .check(classes);
    }

    @Test
    void lesSuivisNeLisentAucuneDonneeDAutorisation() {
        noClasses().that().resideInAPackage("io.argos.workspace.suivi..")
                .should().dependOnClassesThat().haveSimpleNameContaining("Role")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("Caller")
                .orShould().dependOnClassesThat().resideInAnyPackage("io.quarkus.security..",
                        "jakarta.annotation.security..")
                .because("PRF-05 : un suivi n'accorde aucun droit")
                .check(classes);
    }
}
