package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Abonnements de veille sans accès aux autorisations (ABO-05, invariant INV-D7, arc42 §5.5 règle 6) :
 * la gestion des abonnements ({@code io.argos.workspace.subscription}) ne dépend d'aucune classe de
 * rôle, d'appartenance ou de sécurité, ni du reste du module {@code workspace} où ces notions
 * peuvent vivre.
 */
class SubscriptionIsolationTest {

    private static final String SUBSCRIPTION = "io.argos.workspace.subscription..";

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
    }

    @Test
    void abonnementsNeDependentQueDeLeurPaquetEtDuSocleTechnique() {
        classes().that().resideInAPackage(SUBSCRIPTION)
                .should().onlyDependOnClassesThat().resideInAnyPackage(SUBSCRIPTION, "java..", "javax.sql..",
                        "jakarta.enterprise..", "jakarta.inject..", "jakarta.transaction..", "com.fasterxml.jackson..")
                .because("INV-D7 : la gestion des abonnements ne lit ni n'écrit aucune donnée d'autorisation")
                .check(classes);
    }

    @Test
    void aucuneNotionDeRoleOuDeSecuriteDansLesAbonnements() {
        noClasses().that().resideInAPackage(SUBSCRIPTION)
                .should().dependOnClassesThat().haveSimpleNameContaining("Role")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("Membership")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("Permission")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("Caller")
                .orShould().dependOnClassesThat().resideInAnyPackage("io.quarkus.security..", "jakarta.annotation.security..",
                        "org.eclipse.microprofile.jwt..", "io.argos.api..")
                .because("ABO-05 : un abonnement n'accorde aucun droit ; l'autorisation dépend des seuls rôles")
                .check(classes);
    }
}
