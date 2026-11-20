package io.argos.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.argos.api.SignauxAbonnements;
import io.argos.api.veille.VeilleProfilsResource;
import io.argos.architecture.exemples.LectureUnitaireParReference;
import io.argos.signal.SignalQueries;

/**
 * Pas de lecture d'un signal par élément dans « Ma veille » (changement {@code optimiser-ma-veille}, PRF-07-S1) :
 * la restitution lit les signaux de ses avis en une requête ({@link SignalQueries#findAll}). {@link SignalQueries#find(UUID)}
 * en boucle ouvre une connexion et une transaction par avis. La règle couvre les appels directs et les références de
 * méthode ({@code map(signaux::find)}), dans la classe, ses lambdas et ses classes internes.
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
        for (Class<?> lecteur : List.of(VeilleProfilsResource.class, SignauxAbonnements.class)) {
            List<JavaCodeUnitAccess<?>> unitaires = classesDe(classes, lecteur)
                    .flatMap(LectureGroupeeSignauxTest::accesDepuis)
                    .filter(LectureGroupeeSignauxTest::estLectureUnitaire)
                    .toList();
            assertTrue(unitaires.isEmpty(), "PRF-07 : " + lecteur.getSimpleName()
                    + " lit les signaux de ses avis en une requête groupée, pas un par un : " + unitaires);
        }
    }

    @Test
    void laRechercheDeVeilleLitSesSignauxEnUneRequete() {
        JavaClass requetes = classes.get(SignalQueries.class);
        Predicate<JavaCodeUnitAccess<?>> depuisSearch = acces -> acces.getOrigin().getName().equals("search")
                || acces.getOrigin().getName().startsWith("lambda$search");
        List<JavaCodeUnitAccess<?>> unitaires = accesDepuis(requetes)
                .filter(depuisSearch)
                .filter(LectureGroupeeSignauxTest::estLectureUnitaire)
                .toList();
        assertTrue(unitaires.isEmpty(), "SignalQueries.search ne lit aucun signal un par un : " + unitaires);
        assertTrue(accesDepuis(requetes).filter(depuisSearch)
                        .anyMatch(acces -> acces.getTarget().getName().equals("findAll")),
                "SignalQueries.search lit ses signaux par la lecture groupée");
    }

    /** La règle voit une lecture unitaire écrite par référence de méthode ({@code ids.stream().map(signaux::find)}). */
    @Test
    void laRegleDetecteUneReferenceDeMethode() {
        JavaClasses exemple = new ClassFileImporter().importClasses(LectureUnitaireParReference.class);
        assertTrue(classesDe(exemple, LectureUnitaireParReference.class)
                        .flatMap(LectureGroupeeSignauxTest::accesDepuis)
                        .anyMatch(LectureGroupeeSignauxTest::estLectureUnitaire),
                "une référence SignalQueries::find est une lecture unitaire");
    }

    private static Stream<JavaClass> classesDe(JavaClasses importees, Class<?> classe) {
        return importees.stream().filter(importee -> importee.getName().equals(classe.getName())
                || importee.getName().startsWith(classe.getName() + "$"));
    }

    /** Appels et références de méthode faits depuis la classe (lambdas comprises). */
    private static Stream<JavaCodeUnitAccess<?>> accesDepuis(JavaClass classe) {
        return Stream.concat(classe.getMethodCallsFromSelf().stream(), classe.getMethodReferencesFromSelf().stream());
    }

    private static boolean estLectureUnitaire(JavaCodeUnitAccess<?> acces) {
        return acces.getTargetOwner().isEquivalentTo(SignalQueries.class)
                && acces.getTarget().getName().equals("find")
                && acces.getTarget().getRawParameterTypes().size() == 1
                && acces.getTarget().getRawParameterTypes().get(0).isEquivalentTo(UUID.class);
    }
}
