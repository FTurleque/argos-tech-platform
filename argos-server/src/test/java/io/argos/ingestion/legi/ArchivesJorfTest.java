package io.argos.ingestion.legi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Choix des archives quotidiennes du JORF publiées pour un jour (REG-01). */
class ArchivesJorfTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 2);

    @Test
    void archivesDuJourDansLOrdreChronologique() {
        List<String> index = List.of("JORF_20261102-180000.tar.gz", "JORF_20261101-120000.tar.gz",
                "JORF_20261102-060000.tar.gz", "JORF_20261103-060000.tar.gz");

        assertEquals(List.of("JORF_20261102-060000.tar.gz", "JORF_20261102-180000.tar.gz"),
                ArchivesJorf.archivesDu(index, JOUR));
    }

    @Test
    void aucuneArchivePourUnJourSansPublication() {
        assertEquals(List.of(), ArchivesJorf.archivesDu(List.of("JORF_20261101-120000.tar.gz"), JOUR));
        assertEquals(List.of(), ArchivesJorf.archivesDu(List.of(), JOUR));
    }

    /** Le jour est comparé sur la date complète : un préfixe voisin (autre mois, autre année) n'est pas retenu. */
    @Test
    void dateCompleteComparee() {
        List<String> index = List.of("JORF_20251102-060000.tar.gz", "JORF_20261202-060000.tar.gz", "Freemium_jorf_20261102.tar.gz",
                "JORF_20261102-070000.tar.gz");

        assertEquals(List.of("JORF_20261102-070000.tar.gz"), ArchivesJorf.archivesDu(index, JOUR));
    }
}
