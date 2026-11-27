package io.argos.catalog.dictionnaire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.argos.catalog.dictionnaire.DictionnaireMetier.Regle;
import io.argos.catalog.dictionnaire.DictionnaireMetier.TypeRegle;
import io.argos.commun.DonneeInvalide;

/**
 * Règles du dictionnaire métier d'un domaine (veille réglementaire) : contrôle et normalisation faits avant toute
 * écriture ({@link DictionnaireMetier#valider}).
 */
class ValidationReglesTest {

    private static final int VALEUR_MAX = 200;
    private static final String CODE_REFUS = "DONNEE_INVALIDE";
    private static final String ATTRIBUT = "regles";

    private static DonneeInvalide refus(List<Regle> regles) {
        DonneeInvalide refus = assertThrows(DonneeInvalide.class, () -> DictionnaireMetier.valider(regles));
        assertEquals(CODE_REFUS, refus.code());
        assertEquals(ATTRIBUT, refus.attribut());
        return refus;
    }

    @Test
    void listeAbsenteOuAuDelaDuMaximumRefusee() {
        refus(null);
        refus(Collections.nCopies(DictionnaireMetier.REGLES_MAX + 1, new Regle(TypeRegle.MOT_CLE, "paie")));
        assertEquals(Set.of(), DictionnaireMetier.valider(List.of()));
    }

    @Test
    void regleSansTypeRefusee() {
        refus(Arrays.asList(new Regle(TypeRegle.MOT_CLE, "paie"), null));
        refus(List.of(new Regle(null, "paie")));
    }

    @Test
    void valeurVideTropLongueOuAvecCaractereDeControleRefusee() {
        refus(List.of(new Regle(TypeRegle.MOT_CLE, "   ")));
        refus(List.of(new Regle(TypeRegle.MOT_CLE, "m".repeat(VALEUR_MAX + 1))));
        refus(List.of(new Regle(TypeRegle.MOT_CLE, "paie\u0007")));
        assertEquals(1, DictionnaireMetier.valider(List.of(new Regle(TypeRegle.MOT_CLE, "m".repeat(VALEUR_MAX)))).size());
    }

    @Test
    void prefixeNorEnMajusculesCommencantParDesLettres() {
        assertEquals(Set.of(new Regle(TypeRegle.NOR, "TFPF")),
                DictionnaireMetier.valider(List.of(new Regle(TypeRegle.NOR, " tfpf "))));
        DonneeInvalide refus = refus(List.of(new Regle(TypeRegle.NOR, "12AB")));
        assertEquals("Un préfixe NOR commence par des lettres (par exemple TFPF).", refus.getMessage());
    }

    @Test
    void natureEnMajusculesEspacesReduitsEtDoublonsFusionnes() {
        List<Regle> regles = new ArrayList<>(List.of(new Regle(TypeRegle.NATURE, "décret"),
                new Regle(TypeRegle.MOT_CLE, "  temps   de  travail "), new Regle(TypeRegle.NATURE, "DÉCRET")));
        assertEquals(List.of(new Regle(TypeRegle.NATURE, "DÉCRET"), new Regle(TypeRegle.MOT_CLE, "temps de travail")),
                List.copyOf(DictionnaireMetier.valider(regles)));
    }
}
