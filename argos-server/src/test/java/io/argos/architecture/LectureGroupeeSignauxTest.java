package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.argos.api.SignauxAbonnements;
import io.argos.api.veille.VeilleProfilsResource;
import io.argos.signal.SignalQueries;

/**
 * Pas de lecture d'un signal par élément dans « Ma veille » (changement {@code optimiser-ma-veille}, PRF-07-S1) :
 * la restitution lit les signaux de ses avis en une requête ({@link SignalQueries#findAll}), jamais par
 * {@link SignalQueries#find(UUID)} dans une boucle, qui ouvrait une connexion et une transaction par avis.
 */
class LectureGroupeeSignauxTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
    }

    @Test
    void laVeilleNeLitPasLesSignauxUnParUn() {
        noClasses().that().belongToAnyOf(VeilleProfilsResource.class, SignauxAbonnements.class)
                .should().callMethod(SignalQueries.class, "find", UUID.class)
                .because("PRF-07 : une lecture groupée des signaux par restitution, pas une requête par avis")
                .check(classes);
    }

    @Test
    void laRechercheDeVeilleLitSesSignauxEnUneRequete() {
        JavaClass requetes = classes.get(SignalQueries.class);
        List<JavaMethodCall> unitaires = requetes.getMethodCallsFromSelf().stream()
                .filter(c -> c.getOrigin().getName().equals("search") || c.getOrigin().getName().startsWith("lambda$search"))
                .filter(c -> c.getTarget().getOwner().isEquivalentTo(SignalQueries.class)
                        && c.getTarget().getName().equals("find"))
                .toList();
        assertTrue(unitaires.isEmpty(), "SignalQueries.search ne lit aucun signal un par un : " + unitaires);
        assertTrue(requetes.getMethodCallsFromSelf().stream()
                .anyMatch(c -> c.getOrigin().getName().equals("search") && c.getTarget().getName().equals("findAll")),
                "SignalQueries.search lit ses signaux par la lecture groupée");
    }
}
