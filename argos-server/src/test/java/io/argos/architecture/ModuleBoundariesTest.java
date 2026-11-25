package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

import io.argos.architecture.exemples.ClasseHorsModule;
import io.argos.architecture.exemples.noyau.NoyauDependantDUnModule;

/**
 * Frontières du monolithe modulaire (ADR-0001, arc42 §5, dossier v2.4 §B.5).
 *
 * <p>La table {@link #ALLOWED} est la liste fermée des modules : toute classe de {@code io.argos} réside dans
 * un paquet de premier niveau qui y figure, et chaque module ne peut dépendre que des modules listés pour lui.
 * Elle n'autorise que des dépendances que le code utilise. Toute nouvelle dépendance entre modules, ou tout
 * nouveau module, doit d'abord être ajouté à cette table, puis à arc42 §5 : le test échoue sinon.
 *
 * <p>Les modules techniques transverses ({@link #TRANSVERSES}) ne dépendent d'aucun module métier et sont accessibles
 * à tous ; le noyau commun ({@code commun}, ADR-0037) ne dépend en outre d'aucun autre module.
 */
class ModuleBoundariesTest {

    private static final String ROOT = "io.argos";

    /** Module → modules dont il peut dépendre (paquets sous {@code io.argos}). */
    static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("workspace", Set.of()),
            Map.entry("catalog", Set.of()),
            Map.entry("inventory", Set.of("workspace", "catalog", "scm")),
            // signal.web demande le résumé IA d'une actualité par le contrat de la passerelle (encadrer-appels-ia).
            Map.entry("signal", Set.of("ingestion", "catalog", "aigateway")),
            Map.entry("vulnintel", Set.of("signal")),
            Map.entry("releaseradar", Set.of("catalog")),
            // regintel lit les archives JORF (ingestion.legi), reconnaît les demandes d'évolution GitHub et GitLab
            // (scm).
            Map.entry("regintel", Set.of("catalog", "aigateway", "ingestion", "scm")),
            // impact lit le référentiel des standards pour la dette de version et les annonces (radar-standards).
            Map.entry("impact", Set.of("signal", "releaseradar", "inventory", "aigateway", "catalog")),
            // alerting demande le résumé IA d'une alerte par le contrat de la passerelle (encadrer-appels-ia)
            // et rappelle les entrées en vigueur réglementaires (suivre-veille-reglementaire).
            Map.entry("alerting", Set.of("impact", "workspace", "aigateway", "regintel")),
            Map.entry("api", Set.of("workspace", "catalog", "inventory", "signal", "vulnintel", "releaseradar",
                    "regintel", "impact", "alerting", "projectintel", "audit")),
            Map.entry("mcp", Set.of("workspace", "catalog", "inventory", "signal", "vulnintel", "releaseradar",
                    "regintel", "impact", "alerting", "audit")),
            Map.entry("projectintel", Set.of("inventory", "scm")),
            Map.entry("ingestion", Set.of()),
            Map.entry("scm", Set.of()),
            Map.entry("aigateway", Set.of("audit")),
            Map.entry("audit", Set.of()),
            // Mécanismes techniques transverses : corrélation des journaux, observation des tâches planifiées,
            // producteur de l'horloge commune, exécuteurs bornés possédés par des beans CDI.
            Map.entry("observabilite", Set.of()),
            Map.entry("horloge", Set.of()),
            Map.entry("execution", Set.of()),
            // Noyau commun (ADR-0037) : contexte RLS, transaction, JDBC, erreurs du domaine, appelant et limiteur.
            Map.entry("commun", Set.of()));

    /**
     * Modules techniques sans dépendance métier, dont tout module peut dépendre (arc42 §5, socle technique) ; ils
     * peuvent dépendre les uns des autres ({@code execution} propage la corrélation d'{@code observabilite}).
     */
    static final Set<String> TRANSVERSES = Set.of("observabilite", "horloge", "execution", "commun");

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    /** Règle du module déclaré : aucune classe hors d'un module de la table, ni directement dans {@code io.argos}. */
    static ArchRule regleModuleDeclare() {
        String[] modules = ALLOWED.keySet().stream().map(module -> ROOT + "." + module + "..").toArray(String[]::new);
        return classes().that().resideInAPackage(ROOT + "..")
                .should().resideInAnyPackage(modules)
                .because("arc42 §5 : tout paquet de premier niveau de " + ROOT
                        + " est un module déclaré dans la table ALLOWED de ModuleBoundariesTest");
    }

    /** Règle NC-3 : le paquet du noyau ne dépend d'aucun paquet de {@code io.argos} hors de lui-même. */
    static ArchRule regleNoyauSansModule(String paquetNoyau) {
        return noClasses().that().resideInAPackage(paquetNoyau + "..")
                .should().dependOnClassesThat(DescribedPredicate.describe("classes d'un autre module de " + ROOT,
                        (JavaClass classe) -> classe.getPackageName().startsWith(ROOT + ".")
                                && !classe.getPackageName().startsWith(paquetNoyau)))
                .because("ADR-0037 : le noyau commun ne dépend d'aucun module")
                .allowEmptyShould(true);
    }

    @Test
    void leNoyauCommunNeDependDAucunModule() {
        regleNoyauSansModule(ROOT + ".commun").check(classes);
    }

    /** La règle du noyau refuse une classe de noyau qui dépend d'un module. */
    @Test
    void laRegleDuNoyauRefuseUneDependanceVersUnModule() {
        JavaClasses exemple = new ClassFileImporter().importClasses(NoyauDependantDUnModule.class);
        assertThrows(AssertionError.class,
                () -> regleNoyauSansModule("io.argos.architecture.exemples.noyau").check(exemple));
    }

    @Test
    void chaqueClasseResideDansUnModuleDeclare() {
        regleModuleDeclare().check(classes);
    }

    /** La règle du module déclaré refuse une classe d'un paquet de premier niveau absent de la table. */
    @Test
    void laRegleRefuseUnModuleNonDeclare() {
        JavaClasses exemple = new ClassFileImporter().importClasses(ClasseHorsModule.class);
        assertThrows(AssertionError.class, () -> regleModuleDeclare().check(exemple));
    }

    @TestFactory
    Stream<DynamicTest> chaqueModuleRespecteSesDependances() {
        return ALLOWED.keySet().stream().sorted().map(module -> DynamicTest.dynamicTest(module, () -> {
            String[] interdits = ALLOWED.keySet().stream()
                    .filter(autre -> !autre.equals(module))
                    .filter(autre -> !ALLOWED.get(module).contains(autre))
                    .filter(autre -> !TRANSVERSES.contains(autre))
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
     * Seule la passerelle IA connaît le fournisseur de modèles (arc42 §5.5 règle 3, ADR-0004) : aucune
     * classe hors de {@code aigateway} ne dépend de l'adaptateur Ollama ni du contrat du fournisseur ;
     * les modules appelants n'utilisent que {@code PasserelleIa}, {@code DemandeIa} et {@code ReponseIa}.
     */
    @Test
    void seuleLaPasserelleConnaitLeFournisseur() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".aigateway..")
                .should().dependOnClassesThat(DescribedPredicate.describe("fournisseur de modèles",
                        (JavaClass classe) -> classe.getPackageName().startsWith(ROOT + ".aigateway.ollama")
                                || classe.getName().startsWith(ROOT + ".aigateway.FournisseurModele")
                                || classe.getName().equals(ROOT + ".aigateway.QuotasIa")))
                .because("arc42 §5.5 : seule l'AI Gateway appelle le fournisseur et tient les quotas")
                .check(classes);
    }
}
