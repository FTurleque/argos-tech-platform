package io.argos.commun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Bornes d'une demande de page et forme d'une page (ADR-0040, PERF-03-S2). */
class PaginationTest {

    @Test
    void pageNegativeRameneeAZero() {
        assertEquals(0, new Pagination(-3, 20).page());
    }

    @Test
    void tailleRameneeDansLesBornes() {
        assertEquals(Pagination.TAILLE_MAX, new Pagination(0, 1000).taille());
        assertEquals(Pagination.TAILLE_MIN, new Pagination(0, 0).taille());
        assertEquals(Pagination.TAILLE_MIN, new Pagination(0, Integer.MIN_VALUE).taille());
    }

    @Test
    void decalageSansDepassementDEntier() {
        Pagination lointaine = new Pagination(Integer.MAX_VALUE, Pagination.TAILLE_MAX);
        assertEquals((long) Integer.MAX_VALUE * Pagination.TAILLE_MAX, lointaine.decalage());
        assertEquals(100, new Pagination(2, 50).decalage());
    }

    @Test
    void premierePageALaTailleParDefaut() {
        assertEquals(0, Pagination.PREMIERE.page());
        assertEquals(Pagination.TAILLE_DEFAUT, Pagination.PREMIERE.taille());
    }

    @Test
    void pageReprendLaDemandeAppliquee() {
        Page<String> page = Page.depuis(List.of("a", "b"), new Pagination(-1, 500), 42);
        assertEquals(0, page.page());
        assertEquals(Pagination.TAILLE_MAX, page.size());
        assertEquals(42, page.total());
        assertEquals(List.of("a", "b"), page.items());
    }

    @Test
    void pageImmuableEtConvertible() {
        List<Integer> source = new ArrayList<>(List.of(1, 2));
        Page<Integer> page = Page.depuis(source, Pagination.PREMIERE, 2);
        source.add(3);
        assertEquals(2, page.items().size());
        assertThrows(UnsupportedOperationException.class, () -> page.items().add(4));
        Page<String> convertie = page.convertir(nombre -> "n" + nombre);
        assertEquals(List.of("n1", "n2"), convertie.items());
        assertEquals(page.total(), convertie.total());
    }
}
