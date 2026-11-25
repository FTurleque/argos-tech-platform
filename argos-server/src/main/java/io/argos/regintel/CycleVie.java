package io.argos.regintel;

import java.util.Map;
import java.util.Set;

import io.argos.commun.RegleViolee;

/**
 * Cycle de vie du suivi d'un texte dans un espace (REG-06, DC-2) : nouveau, en analyse, sans impact ou
 * impact produit, spécifié, livré.
 *
 * <p>Le passage de {@code NOUVEAU} à {@code EN_ANALYSE} résulte de la validation de la fiche par
 * l'expert ({@link #apresValidation}) ; les autres transitions sont demandées par l'expert. Le passage à
 * {@code SPECIFIE} exige une demande d'évolution liée (REG-08). Toute autre transition est refusée et
 * l'état reste inchangé (REG-06-S2).
 */
public final class CycleVie {

    /** État de suivi d'un texte. */
    public enum Etat { NOUVEAU, EN_ANALYSE, SANS_IMPACT, IMPACT_PRODUIT, SPECIFIE, LIVRE }

    /** Transitions demandées par l'expert, depuis chaque état. */
    private static final Map<Etat, Set<Etat>> TRANSITIONS = Map.of(
            Etat.NOUVEAU, Set.of(),
            Etat.EN_ANALYSE, Set.of(Etat.SANS_IMPACT, Etat.IMPACT_PRODUIT),
            Etat.SANS_IMPACT, Set.of(),
            Etat.IMPACT_PRODUIT, Set.of(Etat.SPECIFIE),
            Etat.SPECIFIE, Set.of(Etat.LIVRE),
            Etat.LIVRE, Set.of());

    private CycleVie() {
    }

    /**
     * Vérifie une transition demandée par l'expert et renvoie l'état d'arrivée.
     *
     * @param demandeLiee vrai si une demande d'évolution est associée au texte
     * @throws RegleViolee {@code TRANSITION_REFUSEE} hors de l'ordre du cycle de vie, ou
     *         {@code DEMANDE_EVOLUTION_REQUISE} pour {@code SPECIFIE} sans demande liée
     */
    public static Etat transition(Etat depuis, Etat vers, boolean demandeLiee) {
        if (depuis == null || vers == null || !TRANSITIONS.get(depuis).contains(vers)) {
            throw new RegleViolee("TRANSITION_REFUSEE", "Transition refusée : un texte « " + libelle(depuis)
                    + " » ne peut pas passer à « " + libelle(vers) + " ».");
        }
        if (vers == Etat.SPECIFIE && !demandeLiee) {
            throw new RegleViolee("DEMANDE_EVOLUTION_REQUISE",
                    "Associez d'abord une demande d'évolution au texte pour le passer à « spécifié ».");
        }
        return vers;
    }

    /** État après la validation de la fiche : un texte nouveau passe en analyse, les autres ne changent pas. */
    public static Etat apresValidation(Etat depuis) {
        return depuis == Etat.NOUVEAU ? Etat.EN_ANALYSE : depuis;
    }

    /** Libellé affiché d'un état. */
    public static String libelle(Etat etat) {
        if (etat == null) {
            return "inconnu";
        }
        return switch (etat) {
            case NOUVEAU -> "nouveau";
            case EN_ANALYSE -> "en analyse";
            case SANS_IMPACT -> "sans impact";
            case IMPACT_PRODUIT -> "impact produit";
            case SPECIFIE -> "spécifié";
            case LIVRE -> "livré";
        };
    }
}
