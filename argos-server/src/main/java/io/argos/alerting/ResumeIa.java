package io.argos.alerting;

import java.util.UUID;

/**
 * Point d'extension du résumé IA d'une alerte, fourni par {@code ai-gateway} (ADR-0004). Aucune
 * implémentation n'est livrée par ce changement : sans implémentation, l'alerte part avec l'état
 * {@code INDISPONIBLE}.
 *
 * <p>Le résumé ne porte que sur le signal, donnée publique : aucune donnée d'espace n'est transmise.
 * Il est demandé avec un délai borné et n'est jamais attendu indéfiniment ; son absence, son refus ou
 * le plafond de coût n'empêchent jamais l'envoi (ALR-05, QS-10).
 */
public interface ResumeIa {

    /** Faux si le plafond de coût est atteint : aucun appel n'est alors effectué (état {@code PLAFOND_ATTEINT}). */
    boolean budgetDisponible();

    /**
     * Résumé du signal. Un résultat vide vaut refus (état {@code REFUSE}) ; une exception vaut
     * indisponibilité (état {@code INDISPONIBLE}).
     */
    String resumer(UUID signalId, String titre);
}
