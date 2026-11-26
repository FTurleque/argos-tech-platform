package io.argos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.transaction.Transactional;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.AppelReseauEnTransaction;
import io.argos.ingestion.AdvisoryFeed;
import io.argos.vulnintel.EnrichmentSource;

/**
 * Invariant INV-4 du changement {@code rendre-ingestion-asynchrone} (ADR-0039) : aucun appel à une source
 * d'enrichissement ({@link EnrichmentSource#lookup}) ni lecture d'un avis ({@link AdvisoryFeed#fetch},
 * {@link AdvisoryFeed.Lecture#avis}) dans une transaction. La règle remonte les appelants transitifs de ces appels et
 * refuse ceux qui sont transactionnels ({@code @Transactional} sur la méthode ou la classe) ou qui ouvrent une
 * transaction par {@code QuarkusTransaction} (appels faits dans une lambda compris).
 */
class AppelsReseauHorsTransactionTest {

    private static final String PAQUET_TRANSACTIONS = "io.quarkus.narayana.jta";

    /** Méthodes, appelants transitifs compris, qui appellent une source externe dans une transaction. */
    static List<String> violations(JavaClasses classes) {
        Deque<JavaCodeUnit> aVisiter = new ArrayDeque<>();
        for (JavaClass classe : classes) {
            classe.getCodeUnits().stream()
                    .filter(unite -> unite.getMethodCallsFromSelf().stream().anyMatch(AppelsReseauHorsTransactionTest::appelReseau))
                    .forEach(aVisiter::add);
        }
        Set<JavaCodeUnit> atteintes = new LinkedHashSet<>();
        while (!aVisiter.isEmpty()) {
            JavaCodeUnit unite = aVisiter.pop();
            if (atteintes.add(unite)) {
                unite.getAccessesToSelf().forEach(acces -> aVisiter.add(acces.getOrigin()));
            }
        }
        return atteintes.stream().filter(AppelsReseauHorsTransactionTest::transactionnelle).map(JavaCodeUnit::getFullName)
                .sorted().toList();
    }

    private static boolean appelReseau(JavaMethodCall appel) {
        JavaClass proprietaire = appel.getTargetOwner();
        String nom = appel.getName();
        return "lookup".equals(nom) && proprietaire.isAssignableTo(EnrichmentSource.class)
                || "fetch".equals(nom) && proprietaire.isAssignableTo(AdvisoryFeed.class)
                || "avis".equals(nom) && proprietaire.isAssignableTo(AdvisoryFeed.Lecture.class);
    }

    private static boolean transactionnelle(JavaCodeUnit unite) {
        return unite.isAnnotatedWith(Transactional.class) || unite.getOwner().isAnnotatedWith(Transactional.class)
                || unite.getMethodCallsFromSelf().stream()
                        .anyMatch(appel -> appel.getTargetOwner().getPackageName().equals(PAQUET_TRANSACTIONS));
    }

    @Test
    void aucunAppelReseauDansUneTransaction() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
        assertEquals(List.of(), violations(classes),
                "ADR-0039 : une source externe n'est jamais appelée transaction ouverte");
    }

    /** La règle refuse un appel dans une méthode transactionnelle et sous une transaction ouverte par un appelant. */
    @Test
    void laRegleRefuseLesAppelsEnTransaction() {
        JavaClasses exemple = new ClassFileImporter().importClasses(AppelReseauEnTransaction.class);
        assertEquals(2, violations(exemple).size(), violations(exemple).toString());
    }
}
