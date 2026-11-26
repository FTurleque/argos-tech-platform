package io.argos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.ObservateurSynchroneEtranger;

/**
 * Invariant INV-5 du changement {@code rendre-ingestion-asynchrone} (ADR-0039, arc42 §8, « Événements entre
 * modules ») : un observateur synchrone ({@code @Observes} sans phase transactionnelle) d'un événement publié par un
 * autre module figure dans la liste fermée {@link #SYNCHRONES_ADMIS}, avec sa raison. Tout autre observateur
 * inter-modules est transactionnel ({@code AFTER_SUCCESS}) ou asynchrone ({@code @ObservesAsync}). Les événements du
 * conteneur et des bibliothèques (hors {@code io.argos}) ne sont pas concernés.
 */
class ObservateursEvenementsTest {

    private static final String RACINE = "io.argos.";

    /** Observateurs synchrones inter-modules admis : méthode → raison. */
    static final Map<String, String> SYNCHRONES_ADMIS = Map.of(
            "io.argos.vulnintel.RafraichissementIndex.surEcheance",
            "invalidation en mémoire des index, sans appel réseau, avant la synchronisation de l'échéance (COL-09-S5)",
            "io.argos.api.preference.ActivationProfilParDefaut.activer",
            "profil par défaut activé avant la première réponse du compte créé, écriture courte en base");

    /** Observateurs synchrones d'un événement d'un autre module, hors de la liste fermée. */
    static List<String> violations(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass classe : classes) {
            for (JavaMethod methode : classe.getMethods()) {
                for (JavaParameter parametre : methode.getParameters()) {
                    if (synchroneEtranger(classe, parametre)
                            && !SYNCHRONES_ADMIS.containsKey(classe.getName() + "." + methode.getName())) {
                        violations.add(classe.getName() + "." + methode.getName() + " observe " + parametre.getRawType().getName());
                    }
                }
            }
        }
        return violations;
    }

    private static boolean synchroneEtranger(JavaClass observateur, JavaParameter parametre) {
        if (!parametre.isAnnotatedWith(Observes.class)
                || parametre.getAnnotationOfType(Observes.class).during() != TransactionPhase.IN_PROGRESS) {
            return false;
        }
        String evenement = parametre.getRawType().getPackageName();
        return evenement.startsWith(RACINE) && !module(evenement).equals(module(observateur.getPackageName()));
    }

    private static String module(String paquet) {
        String suite = paquet.startsWith(RACINE) ? paquet.substring(RACINE.length()) : paquet;
        int point = suite.indexOf('.');
        return point < 0 ? suite : suite.substring(0, point);
    }

    @Test
    void lesObservateursSynchronesInterModulesSontDeclares() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
        assertEquals(List.of(), violations(classes), "ADR-0039 : observateur inter-modules AFTER_SUCCESS ou @ObservesAsync");
    }

    /** La règle refuse un observateur synchrone d'un événement d'un autre module absent de la liste. */
    @Test
    void laRegleRefuseUnObservateurSynchroneNonDeclare() {
        JavaClasses exemple = new ClassFileImporter().importClasses(ObservateurSynchroneEtranger.class);
        assertEquals(1, violations(exemple).size());
    }
}
