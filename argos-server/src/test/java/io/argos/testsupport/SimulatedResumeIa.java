package io.argos.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.alerting.ResumeIa;
import io.argos.alerting.ResumeIaPasserelle;

/**
 * Résumé IA simulé (aucun fournisseur réel n'est appelé) : disponible, indisponible, plafond de coût
 * atteint, refus ou réponse trop lente ; enregistre chaque appel. En mode {@code PASSERELLE}, il délègue
 * à l'implémentation livrée ({@link ResumeIaPasserelle}), branchée sur l'API Ollama simulée par
 * {@link StubServer}.
 */
@ApplicationScoped
public class SimulatedResumeIa implements ResumeIa {

    /** Comportement simulé. */
    public enum Mode { AVAILABLE, UNAVAILABLE, BUDGET_EXHAUSTED, REFUSED, SLOW, PASSERELLE }

    @Inject
    ResumeIaPasserelle passerelle;

    private volatile Mode mode = Mode.AVAILABLE;
    private final List<UUID> calls = Collections.synchronizedList(new ArrayList<>());

    public void configure(Mode mode) {
        this.mode = mode;
        calls.clear();
    }

    public List<UUID> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    @Override
    public boolean budgetDisponible() {
        return mode == Mode.PASSERELLE ? passerelle.budgetDisponible() : mode != Mode.BUDGET_EXHAUSTED;
    }

    @Override
    public boolean budgetDisponible(Set<UUID> espaces) {
        return mode == Mode.PASSERELLE ? passerelle.budgetDisponible(espaces) : budgetDisponible();
    }

    @Override
    public String resumer(UUID signalId, String titre, Set<UUID> espaces) {
        if (mode == Mode.PASSERELLE) {
            calls.add(signalId);
            return passerelle.resumer(signalId, titre, espaces);
        }
        return resumer(signalId, titre);
    }

    @Override
    public String resumer(UUID signalId, String titre) {
        calls.add(signalId);
        switch (mode) {
            case UNAVAILABLE -> throw new IllegalStateException("fournisseur d'IA simulé indisponible");
            case REFUSED -> {
                return "";
            }
            case SLOW -> {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                }
                return "résumé trop tardif";
            }
            default -> {
                return "Résumé simulé : " + titre;
            }
        }
    }
}
