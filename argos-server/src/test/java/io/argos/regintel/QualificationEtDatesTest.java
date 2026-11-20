package io.argos.regintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.catalog.dictionnaire.DictionnaireMetier.DictionnaireEspace;
import io.argos.catalog.dictionnaire.DictionnaireMetier.Domaine;
import io.argos.catalog.dictionnaire.DictionnaireMetier.Regle;
import io.argos.catalog.dictionnaire.DictionnaireMetier.TypeRegle;
import io.argos.workspace.RegleViolee;

/**
 * Tests unitaires de la qualification déterministe (tâche 2.2, REG-02-S1), de l'extraction des dates
 * (tâche 2.4, REG-03-S1, REG-03-S2) et du cycle de vie (tâche 3.3, REG-06-S1, REG-06-S2).
 */
class QualificationEtDatesTest {

    private static final LocalDate PUBLICATION = LocalDate.of(2026, 11, 20);

    private static TexteOfficiel decret(String titre, String nor, String ministere, List<TexteOfficiel.Code> codes) {
        return new TexteOfficiel(UUID.randomUUID(), "JORFTEXT000099000001", "DECRET", "2026-1101", nor, ministere, titre,
                PUBLICATION, null, null, null, codes, List.of(), "");
    }

    private static DictionnaireEspace dictionnaire(Domaine... domaines) {
        return new DictionnaireEspace(UUID.randomUUID(), List.of(domaines));
    }

    private static Domaine domaine(String code, Regle... regles) {
        return new Domaine(code, code, List.of(regles), List.of());
    }

    // ------------------------------------------------------------------ REG-02-S1

    @Test
    void reg02S1CodeSuiviModifieRattacheAuDomaineCarriere() {
        TexteOfficiel texte = decret("Décret relatif à l'avancement", "TFPF2631001D", "Ministère de la fonction publique",
                List.of(new TexteOfficiel.Code("LEGITEXT000044416551", "Code général de la fonction publique")));
        DictionnaireEspace dictionnaire = dictionnaire(
                domaine("carriere", new Regle(TypeRegle.CODE, "code général de la fonction publique")),
                domaine("paie", new Regle(TypeRegle.MOT_CLE, "cotisation")));

        List<Qualification.Rattachement> rattachements = Qualification.qualifier(texte, dictionnaire);

        assertEquals(List.of(new Qualification.Rattachement("carriere", "code modifié : Code général de la fonction publique")),
                rattachements);
    }

    @Test
    void chaqueTypeDeRegle() {
        TexteOfficiel texte = decret("Arrêté fixant le taux de la cotisation vieillesse", "ECOS2631002A", "Ministère de l'Économie",
                List.of(new TexteOfficiel.Code("LEGITEXT000006073189", "Code de la sécurité sociale")));
        assertEquals("nature : DECRET", Qualification.motif(texte, new Regle(TypeRegle.NATURE, "DÉCRET")));
        assertEquals("ministère : Ministère de l'Économie", Qualification.motif(texte, new Regle(TypeRegle.MINISTERE, "economie")));
        assertEquals("NOR : ECOS2631002A", Qualification.motif(texte, new Regle(TypeRegle.NOR, "ECOS")));
        assertEquals("code modifié : Code de la sécurité sociale",
                Qualification.motif(texte, new Regle(TypeRegle.CODE, "LEGITEXT000006073189")));
        assertEquals("mot-clé du titre : cotisation vieillesse",
                Qualification.motif(texte, new Regle(TypeRegle.MOT_CLE, "cotisation vieillesse")));
        assertEquals(null, Qualification.motif(texte, new Regle(TypeRegle.MOT_CLE, "cotis")), "mot entier seulement");
        assertEquals(null, Qualification.motif(texte, new Regle(TypeRegle.NOR, "TFPF")));
    }

    @Test
    void aucuneRegleAucunRattachement() {
        TexteOfficiel texte = decret("Décision portant nomination", "AGRG2631003S", "Ministère de l'agriculture", List.of());
        assertTrue(Qualification.qualifier(texte, dictionnaire(domaine("paie", new Regle(TypeRegle.MOT_CLE, "cotisation"))))
                .isEmpty());
    }

    // ------------------------------------------------------------------ REG-03

    @Test
    void reg03S1DateExtraiteDistincteDeLaPublication() {
        DatesTexte.DateEffet dateEffet = DatesTexte.dateEffet(PUBLICATION,
                "Entrée en vigueur : le texte entre en vigueur le 1er janvier 2027.").orElseThrow();
        assertEquals(LocalDate.of(2027, 1, 1), dateEffet.date());
        assertTrue(dateEffet.motif().contains("1er janvier 2027"), dateEffet.motif());
    }

    @Test
    void reg03S2DateAbsente() {
        assertTrue(DatesTexte.dateEffet(PUBLICATION, "Objet : taux d'une cotisation.").isEmpty());
        assertTrue(DatesTexte.dateEffet(PUBLICATION, null).isEmpty());
        assertEquals("date à confirmer", DatesTexte.DATE_A_CONFIRMER);
    }

    @Test
    void autresFormulations() {
        assertEquals(PUBLICATION.plusDays(1), DatesTexte.dateEffet(PUBLICATION,
                "Le présent arrêté entre en vigueur le lendemain de sa publication.").orElseThrow().date());
        assertEquals(LocalDate.of(2027, 3, 1), DatesTexte.dateEffet(PUBLICATION,
                "Les dispositions entrent en vigueur à compter du 1er mars 2027.").orElseThrow().date());
        assertEquals(LocalDate.of(2027, 7, 14), DatesTexte.dateEffet(PUBLICATION,
                "À compter du 14 juillet 2027, les articles 2 et 3 entrent en vigueur.").orElseThrow().date());
        DatesTexte.DateEffet plusieurs = DatesTexte.dateEffet(PUBLICATION,
                "L'article 1er entre en vigueur le 1er juillet 2027. L'article 2 entre en vigueur le 1er février 2027.")
                .orElseThrow();
        assertEquals(LocalDate.of(2027, 2, 1), plusieurs.date());
        assertTrue(plusieurs.motif().contains("plusieurs dates"), plusieurs.motif());
        assertTrue(DatesTexte.dateEffet(PUBLICATION, "entre en vigueur le 31 février 2027").isEmpty(), "date impossible");
    }

    // ------------------------------------------------------------------ REG-06

    @Test
    void reg06S1TransitionValide() {
        assertEquals(CycleVie.Etat.IMPACT_PRODUIT,
                CycleVie.transition(CycleVie.Etat.EN_ANALYSE, CycleVie.Etat.IMPACT_PRODUIT, false));
        assertEquals(CycleVie.Etat.SANS_IMPACT, CycleVie.transition(CycleVie.Etat.EN_ANALYSE, CycleVie.Etat.SANS_IMPACT, false));
        assertEquals(CycleVie.Etat.SPECIFIE, CycleVie.transition(CycleVie.Etat.IMPACT_PRODUIT, CycleVie.Etat.SPECIFIE, true));
        assertEquals(CycleVie.Etat.LIVRE, CycleVie.transition(CycleVie.Etat.SPECIFIE, CycleVie.Etat.LIVRE, true));
        assertEquals(CycleVie.Etat.EN_ANALYSE, CycleVie.apresValidation(CycleVie.Etat.NOUVEAU));
    }

    @Test
    void reg06S2TransitionInvalide() {
        RegleViolee erreur = assertThrows(RegleViolee.class,
                () -> CycleVie.transition(CycleVie.Etat.NOUVEAU, CycleVie.Etat.LIVRE, true));
        assertEquals("TRANSITION_REFUSEE", erreur.code());
        assertThrows(RegleViolee.class, () -> CycleVie.transition(CycleVie.Etat.NOUVEAU, CycleVie.Etat.EN_ANALYSE, false),
                "seule la validation de la fiche fait passer en analyse");
        assertThrows(RegleViolee.class, () -> CycleVie.transition(CycleVie.Etat.SANS_IMPACT, CycleVie.Etat.IMPACT_PRODUIT, false));
        assertThrows(RegleViolee.class, () -> CycleVie.transition(CycleVie.Etat.LIVRE, CycleVie.Etat.SPECIFIE, true));
        assertEquals("DEMANDE_EVOLUTION_REQUISE", assertThrows(RegleViolee.class,
                () -> CycleVie.transition(CycleVie.Etat.IMPACT_PRODUIT, CycleVie.Etat.SPECIFIE, false)).code());
    }
}
