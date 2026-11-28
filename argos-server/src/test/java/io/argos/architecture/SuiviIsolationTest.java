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
 * n'accordent aucun droit (PRF-05, ADR-0024 point 6) : le paquetage ne dépend d'aucune classe de rôles,
 * d'appartenances ni de préférences, et n'emprunte au reste de {@code workspace} que le contexte RLS.
 * Ce contrôle porte sur les dépendances Java : la lecture technique {@code SuivisProfil.suiveurs} lit
 * en SQL la liste des profils actifs ({@code workspace_preference_utilisateur.profils_actifs}), hors
 * RLS, comme {@code alerting.Destinataires}, sans aucune donnée d'autorisation.
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
    void lesSuivisNEmpruntentAuModuleDesEspacesQueLeContexteRls() {
        noClasses().that().resideInAPackage("io.argos.workspace.suivi..")
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "workspace hors suivi et ContexteBase",
                        (JavaClass classe) -> classe.getPackageName().startsWith("io.argos.workspace")
                                && !classe.getPackageName().startsWith("io.argos.workspace.suivi")
                                && !classe.getName().startsWith("io.argos.commun.ContexteBase")))
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
