package io.argos.alerting;

import java.util.Set;
import java.util.UUID;

/**
 * Point d'extension du résumé IA d'une alerte. L'implémentation livrée, {@link ResumeIaPasserelle},
 * passe par la passerelle {@code ai-gateway} (changement {@code encadrer-appels-ia}) ; sans
 * implémentation, l'alerte part avec l'état {@code INDISPONIBLE}.
 *
 * <p>Le résumé ne porte que sur le signal, donnée publique : aucune donnée d'espace n'est transmise ;
 * les espaces concernés servent seulement à imputer l'appel à un quota. Il est demandé avec un délai
 * borné et n'est jamais attendu indéfiniment ; son absence, son refus ou le plafond n'empêchent jamais
 * l'envoi (ALR-05, QS-10).
 */
public interface ResumeIa {

    /** Faux si le plafond est atteint : aucun appel n'est alors effectué (état {@code PLAFOND_ATTEINT}). */
    boolean budgetDisponible();

    /**
     * Résumé du signal. Un résultat vide vaut refus (état {@code REFUSE}) ; une exception vaut
     * indisponibilité (état {@code INDISPONIBLE}).
     */
    String resumer(UUID signalId, String titre);

    /** Faux si le plafond de l'instance ou le quota de chacun des espaces concernés est atteint. */
    default boolean budgetDisponible(Set<UUID> espaces) {
        return budgetDisponible();
    }

    /** Résumé du signal, imputé à l'un des espaces concernés ; mêmes conventions que {@link #resumer(UUID, String)}. */
    default String resumer(UUID signalId, String titre, Set<UUID> espaces) {
        return resumer(signalId, titre);
    }
}
