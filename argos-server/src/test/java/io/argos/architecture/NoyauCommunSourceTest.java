package io.argos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Invariant NC-1 (ADR-0037) : seul le noyau commun écrit le protocole RLS. ArchUnit ne lit pas les chaînes de
 * caractères : le test parcourt les sources de production.
 */
class NoyauCommunSourceTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path NOYAU = SOURCES.resolve("io/argos/commun");
    private static final Pattern PROTOCOLE_RLS = Pattern.compile("SET LOCAL ROLE|"
            + Pattern.quote("set_config('argos."));

    /** Fichiers de {@code racine}, hors du dossier {@code noyau}, dont le texte contient le motif. */
    static List<Path> fichiersContenant(Path racine, Path noyau, Pattern motif) throws IOException {
        try (Stream<Path> fichiers = Files.walk(racine)) {
            return fichiers.filter(fichier -> fichier.toString().endsWith(".java"))
                    .filter(fichier -> !fichier.startsWith(noyau))
                    .filter(fichier -> contient(fichier, motif))
                    .toList();
        }
    }

    private static boolean contient(Path fichier, Pattern motif) {
        try {
            return motif.matcher(Files.readString(fichier, StandardCharsets.UTF_8)).find();
        } catch (IOException erreur) {
            throw new java.io.UncheckedIOException(erreur);
        }
    }

    @Test
    void seulLeNoyauEcritLeProtocoleRls() throws IOException {
        assertTrue(Files.isDirectory(NOYAU), "le noyau commun est introuvable : " + NOYAU);
        assertEquals(List.of(), fichiersContenant(SOURCES, NOYAU, PROTOCOLE_RLS),
                "ADR-0037 : le protocole RLS n'est écrit que dans io.argos.commun");
    }

    /** Le motif repère une copie du protocole hors du noyau et ignore le noyau. */
    @Test
    void leMotifDetecteUneCopieHorsDuNoyau(@TempDir Path racine) throws IOException {
        Path noyau = Files.createDirectories(racine.resolve("commun"));
        Files.writeString(noyau.resolve("Appliquer.java"), "stmt.execute(\"SET LOCAL ROLE argos_app\");");
        Path copie = Files.writeString(racine.resolve("Copie.java"), "select set_config('argos.compte_courant', ?, true)");
        assertEquals(List.of(copie), fichiersContenant(racine, noyau, PROTOCOLE_RLS));
    }
}
