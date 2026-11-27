package io.argos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Liste des tests qui exigent Docker (AUD-TST-03) : {@code src/test/tests-docker.txt} nomme exactement les classes
 * annotées {@code @QuarkusTest} ou {@code @QuarkusIntegrationTest}, qui démarrent PostgreSQL et Keycloak par les Dev
 * Services, et celles qui démarrent elles-mêmes un conteneur (Testcontainers). Le profil Maven {@code sans-docker}
 * l'emploie comme fichier d'exclusion de Surefire : les classes sont écartées avant leur découverte, si bien que Quarkus ne construit aucune application et que Docker n'est pas
 * sollicité. Une classe ajoutée, supprimée ou renommée sans mise à jour de la liste fait échouer ce test, qui affiche
 * le contenu attendu.
 */
class ListeTestsDockerTest {

    private static final Path SOURCES = Path.of("src/test/java");
    private static final Path LISTE = Path.of("src/test/tests-docker.txt");
    private static final Pattern DOCKER = Pattern.compile("^(@Quarkus(Integration)?Test\\b|import org\\.testcontainers\\.)",
            Pattern.MULTILINE);
    private static final String COMMENTAIRE = "#";

    /** Chemins, relatifs à {@code racine} et séparés par {@code /}, des classes de test qui exigent Docker. */
    static Set<String> classesDocker(Path racine) throws IOException {
        try (Stream<Path> fichiers = Files.walk(racine)) {
            return fichiers.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> DOCKER.matcher(lire(chemin)).find())
                    .map(chemin -> racine.relativize(chemin).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /** Lignes significatives de la liste : ni vides ni commentaires. */
    static Set<String> entrees(List<String> lignes) {
        return lignes.stream().map(String::strip)
                .filter(ligne -> !ligne.isEmpty() && !ligne.startsWith(COMMENTAIRE))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    @Test
    void laListeNommeExactementLesTestsQuiExigentDocker() throws IOException {
        Set<String> attendues = classesDocker(SOURCES);
        assertEquals(attendues, entrees(Files.readAllLines(LISTE, StandardCharsets.UTF_8)),
                "src/test/tests-docker.txt à mettre à jour ; entrées attendues :\n" + String.join("\n", attendues));
    }

    @Test
    void lesCommentairesEtLignesVidesSontIgnores() {
        assertEquals(Set.of("io/argos/A.java"), entrees(List.of("# commentaire", "", "  io/argos/A.java  ")));
    }
}
