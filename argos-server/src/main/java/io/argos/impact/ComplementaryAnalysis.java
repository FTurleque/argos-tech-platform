package io.argos.impact;

import java.util.List;
import java.util.UUID;

/**
 * Point d'extension d'une analyse complémentaire (IA via {@code ai-gateway}), sollicitée seulement
 * après l'enregistrement des impacts déterministes (décision D3). Aucune implémentation n'est
 * fournie dans ce lot.
 *
 * <p>Une suggestion ne crée jamais d'impact et ne peut donc pas déclencher d'alerte (COR-04) ;
 * l'indisponibilité ou le plafond budgétaire n'affecte pas les impacts déjà enregistrés (COR-05).
 */
public interface ComplementaryAnalysis {

    /** Faux si le plafond budgétaire est atteint : aucun appel n'est alors effectué (COR-05-S2). */
    boolean budgetAvailable();

    /** Suggestions de projets concernés ; peut lever une exception si le fournisseur est indisponible. */
    List<Suggestion> analyze(UUID signalId, List<UUID> deterministicImpactIds);

    /** Suggestion : projet supposé concerné et justification. Information seulement. */
    record Suggestion(UUID projectId, String rationale) {
    }
}
