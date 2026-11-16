package io.argos.impact;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.argos.signal.opportunite.Opportunite;

/**
 * Stratégie de rapprochement du profil {@code artiste} : discipline + territoire + date limite
 * (ADR-0024 point 5, exigence ART-03, décision D1). Déterministe : elle compare des valeurs
 * structurées et ne fait appel à aucune IA (ADR-0013).
 *
 * <ul>
 *   <li>une discipline de l'opportunité figure parmi celles suivies ;</li>
 *   <li>un territoire de l'opportunité figure parmi ceux suivis, ou l'opportunité vaut pour toute la
 *       France (territoire {@value #FRANCE}) ;</li>
 *   <li>sa date limite n'est pas dépassée (le jour même reste ouvert) ; sans date limite connue,
 *       l'opportunité est restituée « date à confirmer ».</li>
 * </ul>
 */
public final class StrategieDisciplineTerritoire {

    /** Identifiant déclaré dans le manifeste du profil. */
    public static final String IDENTIFIANT = "discipline-territoire-date-limite";

    /** Territoire d'une opportunité ouverte à toute la France. */
    public static final String FRANCE = "france";

    /** Issue du rapprochement. */
    public enum Issue {
        /** Restituée comme opportunité ouverte, avec sa date limite. */
        OUVERTE,
        /** Restituée, date limite à confirmer auprès de l'organisme. */
        DATE_A_CONFIRMER,
        /** Non restituée : aucune discipline suivie. */
        DISCIPLINE_NON_SUIVIE,
        /** Non restituée : aucun territoire suivi. */
        TERRITOIRE_NON_SUIVI,
        /** Non présentée comme opportunité ouverte : date limite dépassée (ART-03-S2). */
        DATE_LIMITE_DEPASSEE;

        public boolean restituee() {
            return this == OUVERTE || this == DATE_A_CONFIRMER;
        }
    }

    /** Décision explicable : issue, valeurs qui ont correspondu et explication lisible. */
    public record Decision(Issue issue, List<String> disciplines, List<String> territoires, String explication) {

        public Decision {
            disciplines = List.copyOf(disciplines);
            territoires = List.copyOf(territoires);
        }
    }

    private StrategieDisciplineTerritoire() {
    }

    public static Decision evaluer(Opportunite o, Set<String> disciplinesSuivies, Set<String> territoiresSuivis,
            LocalDate aujourdhui) {
        List<String> disciplines = communes(o.disciplines(), disciplinesSuivies);
        if (disciplines.isEmpty()) {
            return new Decision(Issue.DISCIPLINE_NON_SUIVIE, List.of(), List.of(),
                    "Aucune discipline de l'appel (" + o.disciplines() + ") ne figure parmi vos disciplines suivies.");
        }
        List<String> territoires = o.territoires().contains(FRANCE) ? List.of(FRANCE)
                : communes(o.territoires(), territoiresSuivis);
        if (territoires.isEmpty()) {
            return new Decision(Issue.TERRITOIRE_NON_SUIVI, disciplines, List.of(),
                    "Aucun territoire de l'appel (" + o.territoires() + ") ne figure parmi vos territoires suivis.");
        }
        String correspondance = "discipline " + String.join(", ", disciplines) + " ; territoire "
                + String.join(", ", territoires);
        if (o.dateLimite() == null) {
            return new Decision(Issue.DATE_A_CONFIRMER, disciplines, territoires,
                    "Correspond à vos suivis (" + correspondance + ") ; date limite à confirmer auprès de l'organisme.");
        }
        if (o.dateLimite().isBefore(aujourdhui)) {
            return new Decision(Issue.DATE_LIMITE_DEPASSEE, disciplines, territoires,
                    "Date limite dépassée (" + o.dateLimite() + ").");
        }
        long jours = ChronoUnit.DAYS.between(aujourdhui, o.dateLimite());
        return new Decision(Issue.OUVERTE, disciplines, territoires, "Correspond à vos suivis (" + correspondance
                + ") ; date limite le " + o.dateLimite() + (jours == 0 ? " (aujourd'hui)." : " (dans " + jours + " jours)."));
    }

    private static List<String> communes(Collection<String> valeurs, Set<String> suivies) {
        return valeurs.stream().filter(suivies::contains).distinct().toList();
    }
}
