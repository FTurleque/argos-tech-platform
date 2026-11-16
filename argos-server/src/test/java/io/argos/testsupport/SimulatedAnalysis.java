package io.argos.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.impact.ComplementaryAnalysis;

/**
 * Analyse IA simulée (aucun fournisseur réel n'est appelé) : disponible, indisponible ou plafond
 * budgétaire atteint ; enregistre chaque appel.
 */
@ApplicationScoped
public class SimulatedAnalysis implements ComplementaryAnalysis {

    /** Comportement simulé. */
    public enum Mode { AVAILABLE, UNAVAILABLE, BUDGET_EXHAUSTED }

    private volatile Mode mode = Mode.AVAILABLE;
    private volatile List<Suggestion> suggestions = List.of();
    private final List<UUID> calls = Collections.synchronizedList(new ArrayList<>());

    public void configure(Mode mode, List<Suggestion> suggestions) {
        this.mode = mode;
        this.suggestions = List.copyOf(suggestions);
        calls.clear();
    }

    public List<UUID> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    @Override
    public boolean budgetAvailable() {
        return mode != Mode.BUDGET_EXHAUSTED;
    }

    @Override
    public List<Suggestion> analyze(UUID signalId, List<UUID> deterministicImpactIds) {
        calls.add(signalId);
        if (mode == Mode.UNAVAILABLE) {
            throw new IllegalStateException("fournisseur d'IA simulé indisponible");
        }
        return suggestions;
    }
}
