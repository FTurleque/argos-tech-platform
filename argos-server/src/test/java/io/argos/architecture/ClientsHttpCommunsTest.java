package io.argos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import io.argos.architecture.exemples.ClientHttpPropre;

/**
 * Clients HTTP sortants (AUD-ARC-10, ADR-0039) : seule la fabrique {@code io.argos.commun.http.ClientsHttp} construit
 * un client {@code java.net.http} ; chaque client du serveur en hérite le refus des redirections, le
 * {@code User-Agent} et la lecture bornée. Le transport épinglé de la veille web (Vert.x) n'emploie pas
 * {@code java.net.http}.
 */
class ClientsHttpCommunsTest {

    static ArchRule regle() {
        return noClasses().that().resideOutsideOfPackage("io.argos.commun.http..")
                .should().callMethod(HttpClient.class, "newBuilder")
                .orShould().callMethod(HttpClient.class, "newHttpClient")
                .because("ADR-0039 : un client HTTP sortant est construit par io.argos.commun.http.ClientsHttp");
    }

    @Test
    void seuleLaFabriqueCommuneConstruitUnClientHttp() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.argos");
        regle().check(classes);
    }

    /** La règle refuse un client construit hors de la fabrique. */
    @Test
    void laRegleRefuseUnClientConstruitAilleurs() {
        JavaClasses exemple = new ClassFileImporter().importClasses(ClientHttpPropre.class);
        assertThrows(AssertionError.class, () -> regle().check(exemple));
    }
}
