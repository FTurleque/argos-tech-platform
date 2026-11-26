package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.MethodeSansAppelant;

/**
 * Aucune méthode publique de {@code src/main} n'est atteinte seulement par les tests (AUD-QUA-17) : chaque méthode
 * publique d'une classe de {@code io.argos} est appelée ou référencée depuis le code de production, ou est un point
 * d'entrée appelé par le cadre (ressource REST, observateur CDI, tâche planifiée, outil MCP, producteur CDI,
 * traitement d'exception), une redéfinition, un accesseur de record ou une méthode d'{@code Object} ou d'énumération.
 */
class MethodesAtteintesEnProductionTest {

    /** Annotations des méthodes appelées par le cadre (Quarkus, JAX-RS, CDI, MCP), par paquet. */
    private static final Set<String> PAQUETS_POINTS_D_ENTREE = Set.of("jakarta.ws.rs", "io.quarkus.scheduler",
            "io.quarkiverse.mcp.server", "org.jboss.resteasy.reactive.server", "io.quarkus.runtime");

    /** Annotations isolées des méthodes appelées par le cadre. */
    private static final Set<String> ANNOTATIONS_POINTS_D_ENTREE = Set.of("jakarta.enterprise.inject.Produces",
            "jakarta.annotation.PostConstruct", "jakarta.annotation.PreDestroy",
            "com.fasterxml.jackson.annotation.JsonCreator", "com.fasterxml.jackson.annotation.JsonValue",
            "com.fasterxml.jackson.annotation.JsonProperty", "io.quarkus.runtime.Startup",
            "jakarta.interceptor.AroundInvoke");

    /** Paramètres d'observateur CDI. */
    private static final Set<String> ANNOTATIONS_OBSERVATEURS = Set.of("jakarta.enterprise.event.Observes",
            "jakarta.enterprise.event.ObservesAsync");

    /** Méthodes d'{@code Object} et méthodes générées des énumérations. */
    private static final Set<String> METHODES_GENERIQUES = Set.of("equals", "hashCode", "toString", "values", "valueOf",
            "close", "main");

    static ArchRule regle() {
        return methods().that().haveModifier(JavaModifier.PUBLIC)
                .and(DescribedPredicate.describe("déclarées dans io.argos", (JavaMethod methode) ->
                        methode.getOwner().getPackageName().startsWith("io.argos")))
                .should(etreAtteintesEnProduction())
                .because("AUD-QUA-17 : une méthode atteinte seulement par les tests annonce une fonction non livrée")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaMethod> etreAtteintesEnProduction() {
        return new ArchCondition<>("être appelées ou référencées depuis le code de production") {
            @Override
            public void check(JavaMethod methode, ConditionEvents evenements) {
                if (!atteinte(methode)) {
                    evenements.add(SimpleConditionEvent.violated(methode,
                            methode.getFullName() + " n'est appelée par aucune classe de production"));
                }
            }
        };
    }

    private static boolean atteinte(JavaMethod methode) {
        return !methode.getAccessesToSelf().isEmpty()
                || METHODES_GENERIQUES.contains(methode.getName())
                || pointDEntree(methode)
                || accesseurDeRecord(methode)
                || redefinition(methode);
    }

    private static boolean pointDEntree(JavaMethod methode) {
        boolean annotee = methode.getAnnotations().stream().map(annotation -> annotation.getRawType())
                .anyMatch(type -> ANNOTATIONS_POINTS_D_ENTREE.contains(type.getName())
                        || PAQUETS_POINTS_D_ENTREE.stream().anyMatch(paquet -> type.getPackageName().startsWith(paquet)));
        boolean observateur = methode.getParameters().stream().flatMap(parametre -> parametre.getAnnotations().stream())
                .anyMatch(annotation -> ANNOTATIONS_OBSERVATEURS.contains(annotation.getRawType().getName()));
        return annotee || observateur;
    }

    /** Accesseur d'un composant de record, lu par la sérialisation JSON. */
    private static boolean accesseurDeRecord(JavaMethod methode) {
        JavaClass proprietaire = methode.getOwner();
        return proprietaire.getRawSuperclass().map(parent -> parent.isEquivalentTo(Record.class)).orElse(false)
                && methode.getParameters().isEmpty()
                && proprietaire.tryGetField(methode.getName()).isPresent();
    }

    /** Redéfinition d'une méthode d'un type parent ou d'une interface, appelée à travers ce type. */
    private static boolean redefinition(JavaMethod methode) {
        JavaClass proprietaire = methode.getOwner();
        return proprietaire.getAllRawSuperclasses().stream().map(JavaClass::reflect).anyMatch(parent -> declare(parent, methode))
                || proprietaire.getAllRawInterfaces().stream().map(JavaClass::reflect).anyMatch(parent -> declare(parent, methode));
    }

    private static boolean declare(Class<?> type, JavaMethod methode) {
        Class<?>[] parametres = methode.getRawParameterTypes().stream().map(JavaClass::reflect).toArray(Class<?>[]::new);
        try {
            type.getMethod(methode.getName(), parametres);
            return true;
        } catch (NoSuchMethodException absente) {
            return false;
        }
    }

    @Test
    void chaqueMethodePubliqueEstAtteinteEnProduction() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
        regle().check(production);
    }

    @Test
    void laRegleRefuseUneMethodePubliqueSansAppelant() {
        JavaClasses exemple = new ClassFileImporter().importClasses(MethodeSansAppelant.class);
        assertThrows(AssertionError.class, () -> regle().check(exemple));
    }
}
