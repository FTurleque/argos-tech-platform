package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.LectureDirecteHeure;

/**
 * Horloge injectée (AUD-QUA-10, design.md D2 du changement {@code injecter-horloge-et-executeurs}, arc42 §8.16) :
 * aucune classe de production ne lit l'heure système sans le {@link java.time.Clock} commun ({@code now()} sans
 * argument, {@code System.currentTimeMillis()}). Le producteur de l'horloge n'en a pas besoin : il crée le
 * {@code Clock} par {@code Clock.system}.
 */
class HorlogeInjecteeTest {

    static ArchRule regle() {
        return noClasses().should().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .because("arc42 §8.16 : l'heure murale vient du Clock injecté (io.argos.horloge), remplaçable en test");
    }

    @Test
    void aucuneLectureDirecteDeLHeureEnProduction() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
        regle().check(production);
    }

    @Test
    void laRegleRefuseUneLectureDirecte() {
        JavaClasses exemple = new ClassFileImporter().importClasses(LectureDirecteHeure.class);
        assertThrows(AssertionError.class, () -> regle().check(exemple));
    }
}
