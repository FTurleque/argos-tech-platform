package io.argos.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.alerting.ResumeIa;

/**
 * Résumé IA simulé (aucun fournisseur réel n'est appelé) : disponible, indisponible, plafond de coût
 * atteint, refus ou réponse trop lente ; enregistre chaque appel.
 */
@ApplicationScoped
public class SimulatedResumeIa implements ResumeIa {

    /** Comportement simulé. */
    public enum Mode { AVAILABLE, UNAVAILABLE, BUDGET_EXHAUSTED, REFUSED, SLOW }

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
        return mode != Mode.BUDGET_EXHAUSTED;
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
                } catch (InterruptedException e) {
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
