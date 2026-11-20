package io.argos.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.projectintel.SyntheseIa;
import io.argos.projectintel.VueActivite;

/**
 * Synthèse IA de l'activité simulée (aucun fournisseur réel n'est appelé) : fidèle aux indicateurs,
 * chiffre inventé, indisponible, plafond de coût atteint ou refus ; enregistre les indicateurs reçus.
 */
@ApplicationScoped
public class SimulatedSyntheseIa implements SyntheseIa {

    /** Comportement simulé. */
    public enum Mode { FIDELE, CHIFFRE_INVENTE, UNAVAILABLE, BUDGET_EXHAUSTED, REFUSED }

    private volatile Mode mode = Mode.FIDELE;
    private final List<VueActivite> recues = Collections.synchronizedList(new ArrayList<>());

    public void configure(Mode mode) {
        this.mode = mode;
        recues.clear();
    }

    public List<VueActivite> recues() {
        synchronized (recues) {
            return List.copyOf(recues);
        }
    }

    @Override
    public boolean budgetDisponible() {
        return mode != Mode.BUDGET_EXHAUSTED;
    }

    @Override
    public String synthetiser(VueActivite vue) {
        recues.add(vue);
        VueActivite.Compteurs compteurs = vue.compteurs();
        return switch (mode) {
            case UNAVAILABLE -> throw new IllegalStateException("fournisseur d'IA simulé indisponible");
            case REFUSED -> "";
            case CHIFFRE_INVENTE -> "Le projet avance bien : environ 93 % du travail semble terminé.";
            default -> "Sur " + vue.periode().jours() + " jours, " + compteurs.ticketsOuverts() + " tickets ouverts et "
                    + compteurs.ticketsFermes() + " fermés ; " + compteurs.fusionsFusionnees() + " demandes de fusion fusionnées."
                    + vue.jalons().stream().filter(jalon -> jalon.avancement() != null).findFirst()
                            .map(jalon -> " Jalon « " + jalon.titre() + " » : " + jalon.avancement().libelle() + ".").orElse("");
        };
    }
}
