package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Frontières du monolithe modulaire (ADR-0001, arc42 §5, dossier v2.4 §B.5).
 *
 * <p>Chaque module ne peut dépendre que des modules listés ici. Toute nouvelle
 * dépendance entre modules doit d'abord être ajoutée à cette table, puis à
 * arc42 §5 : le test échoue sinon.
 */
class ModuleBoundariesTest {

    private static final String ROOT = "io.argos";

    /** Module → modules dont il peut dépendre (paquets sous {@code io.argos}). */
    static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("workspace", Set.of("audit")),
            Map.entry("catalog", Set.of("audit")),
            Map.entry("inventory", Set.of("workspace", "catalog", "scm")),
            Map.entry("signal", Set.of("ingestion", "catalog")),
            Map.entry("vulnintel", Set.of("signal", "catalog")),
            Map.entry("releaseradar", Set.of("signal", "catalog")),
            Map.entry("regintel", Set.of("signal", "catalog", "aigateway")),
            Map.entry("impact", Set.of("signal", "vulnintel", "releaseradar", "inventory", "aigateway")),
            Map.entry("alerting", Set.of("impact", "workspace")),
            Map.entry("api", Set.of("workspace", "catalog", "inventory", "signal", "vulnintel", "releaseradar",
                    "regintel", "impact", "alerting", "projectintel", "audit")),
            // mcp réutilise l'établissement de l'appelant de l'API (api.security) : voir mcpNeLitQueLaSecuriteDeLApi.
            Map.entry("mcp", Set.of("workspace", "catalog", "inventory", "signal", "vulnintel", "releaseradar",
                    "regintel", "impact", "alerting", "projectintel", "audit", "api")),
            Map.entry("projectintel", Set.of("inventory", "scm")),
            Map.entry("ingestion", Set.of()),
            Map.entry("scm", Set.of()),
            Map.entry("aigateway", Set.of("audit")),
            Map.entry("audit", Set.of()));

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @TestFactory
    Stream<DynamicTest> chaqueModuleRespecteSesDependances() {
        return ALLOWED.keySet().stream().sorted().map(module -> DynamicTest.dynamicTest(module, () -> {
            String[] interdits = ALLOWED.keySet().stream()
                    .filter(autre -> !autre.equals(module))
                    .filter(autre -> !ALLOWED.get(module).contains(autre))
                    .map(autre -> ROOT + "." + autre + "..")
                    .toArray(String[]::new);
            noClasses().that().resideInAPackage(ROOT + "." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(interdits)
                    .because("arc42 §5 : " + module + " ne peut dépendre que de " + ALLOWED.get(module))
                    .allowEmptyShould(true)
                    .check(classes);
        }));
    }

    /**
     * Le serveur MCP n'emprunte à {@code api} que l'établissement de l'appelant et de son périmètre
     * ({@code io.argos.api.security}), pour appliquer les mêmes droits que le portail (ADR-0014) ;
     * il n'utilise ni les ressources REST ni leurs représentations.
     */
    @Test
    void mcpNeLitQueLaSecuriteDeLApi() {
        noClasses().that().resideInAPackage(ROOT + ".mcp..")
                .should().dependOnClassesThat(DescribedPredicate.describe("classes de api hors api.security",
                        (JavaClass c) -> c.getPackageName().startsWith(ROOT + ".api")
                                && !c.getPackageName().startsWith(ROOT + ".api.security")))
                .because("arc42 §5 : dans le module api, mcp ne dépend que de api.security")
                .check(classes);
    }
}
