package io.argos.impact;

import java.util.List;
import java.util.UUID;

/**
 * Point d'extension d'une analyse complémentaire (IA via {@code ai-gateway}), sollicitée seulement
 * après l'enregistrement des impacts déterministes (décision D3). L'implémentation {@link
 * AnalyseComplementaireIa} passe par la passerelle IA et reste désactivée par défaut
 * ({@code argos.ia.analyse-correlation.active=false}).
 *
 * <p>Une suggestion ne crée jamais d'impact et ne peut donc pas déclencher d'alerte (COR-04) ;
 * l'indisponibilité, le plafond d'appels de l'instance ou le quota d'un espace n'affecte pas les
 * impacts déjà enregistrés (COR-05).
 */
public interface ComplementaryAnalysis {

    /** Faux si le plafond d'appels ou le quota est atteint : aucun appel n'est alors effectué (COR-05-S2). */
    boolean budgetAvailable();

    /** Suggestions de projets concernés ; peut lever une exception si le fournisseur est indisponible. */
    List<Suggestion> analyze(UUID signalId, List<UUID> deterministicImpactIds);

    /** Suggestion : projet supposé concerné et justification. Information seulement. */
    record Suggestion(UUID projectId, String rationale) {
    }
}
