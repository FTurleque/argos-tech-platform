package io.argos.commun.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lecture bornée des réponses HTTP (ADR-0039, AUD-PER-11) : octets, lignes et copie s'arrêtent au dépassement. */
class LectureBorneeTest {

    private static final byte[] DIX_OCTETS = "0123456789".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dossier;

    @Test
    void lesOctetsSousLaLimiteSontRendus() throws IOException {
        assertArrayEquals(DIX_OCTETS, LectureBornee.octets(new ByteArrayInputStream(DIX_OCTETS), 10));
    }

    @Test
    void lesOctetsAuDelaDeLaLimiteSontRefuses() {
        ReponseTropVolumineuse refus = assertThrows(ReponseTropVolumineuse.class,
                () -> LectureBornee.octets(new ByteArrayInputStream(DIX_OCTETS), 9));
        assertEquals(9, refus.tailleMax());
    }

    @Test
    void lesLignesSontLuesEnFluxEtBornees() throws IOException {
        byte[] texte = "a,1\nb,2\r\nc,3".getBytes(StandardCharsets.UTF_8);
        List<String> lignes = new ArrayList<>();
        LectureBornee.lignes(new ByteArrayInputStream(texte), texte.length, lignes::add);
        assertEquals(List.of("a,1", "b,2", "c,3"), lignes);
        assertThrows(ReponseTropVolumineuse.class,
                () -> LectureBornee.lignes(new ByteArrayInputStream(texte), texte.length - 1L, ligne -> { }));
    }

    @Test
    void laCopieVersUnFichierEstBornee() throws IOException {
        Path cible = dossier.resolve("archive.zip");
        assertEquals(10, LectureBornee.copier(new ByteArrayInputStream(DIX_OCTETS), cible, 10));
        assertArrayEquals(DIX_OCTETS, Files.readAllBytes(cible));
        assertThrows(ReponseTropVolumineuse.class,
                () -> LectureBornee.copier(new ByteArrayInputStream(DIX_OCTETS), dossier.resolve("autre.zip"), 5));
    }
}
