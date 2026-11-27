package io.argos.catalog.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.argos.commun.DonneeInvalide;

/**
 * Contrôle de la saisie d'une entrée du référentiel (RAD-01, RAD-07) : le refus nomme le champ en cause. La validation
 * précède toute écriture dans {@link ReferentielEspace#enregistrer}, qui laisse donc le référentiel inchangé, et
 * {@link DonneeInvalide} se traduit en réponse 400 portant ce code et cet attribut.
 */
class SaisieStandardTest {

    private static final String SPRING = "pkg:maven/org.springframework.boot/spring-boot";
    private static final String BIBLIO = "pkg:npm/biblio-ui";

    /** RAD-01-S2 : HOLD sans justification, ADOPT sans version, clé versionnée, date ou produit invalides : champ nommé. */
    @Test
    void rad01S2JustificationManquante() {
        DonneeInvalide sansJustification = refus(new SaisieStandard(BIBLIO, "Biblio", "HOLD", null, "Front", null, "2027-01-31",
                null));
        assertEquals("CHAMP_MANQUANT", sansJustification.code());
        assertEquals("justification", sansJustification.attribut());
        assertEquals("versionRecommandee", refus(new SaisieStandard(SPRING, "Spring Boot", "ADOPT", null, "Socle", "Validé",
                "2027-01-31", null)).attribut());
        assertEquals("cle", refus(new SaisieStandard("pkg:npm/biblio-ui@1.0.0", "Biblio", "HOLD", null, "Front", "Non",
                "2027-01-31", null)).attribut());
        assertEquals("dateRevue", refus(new SaisieStandard(BIBLIO, "Biblio", "HOLD", null, "Front", "Non", "31/01/2027",
                null)).attribut());
        assertEquals("produitCycleVie", refus(new SaisieStandard(BIBLIO, "Biblio", "HOLD", null, "Front", "Non", "2027-01-31",
                "../../etc")).attribut());
    }

    /** RAD-07-S1 : une clé mal encodée est refusée sur le champ {@code cle}. */
    @Test
    void rad07S1CleMalEncodee() {
        DonneeInvalide refus = refus(new SaisieStandard("pkg:maven/%zz/x", "Mal encodée", "ASSESS", null, "Socle", "À étudier",
                "2027-06-30", null));

        assertEquals("DONNEE_INVALIDE", refus.code());
        assertEquals("cle", refus.attribut());
    }

    private static DonneeInvalide refus(SaisieStandard saisie) {
        return assertThrows(DonneeInvalide.class, saisie::valider);
    }
}
