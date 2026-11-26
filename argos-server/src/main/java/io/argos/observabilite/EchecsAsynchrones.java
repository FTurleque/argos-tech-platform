package io.argos.observabilite;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Échecs des traitements qui ne rendent compte à aucun appelant (ADR-0039, arc42 §8.6) : une tâche de fond ou un
 * observateur asynchrone en erreur imprévue est journalisé avec sa cause par son lanceur, puis compté ici.
 * <ul>
 * <li>{@code argos_traitements_fond_echecs_total} : tâches de fond terminées en erreur, étiquette {@code traitement} ;</li>
 * <li>{@code argos_evenements_asynchrones_echecs_total} : notifications asynchrones en erreur, étiquette
 * {@code evenement} (nom simple du type d'événement).</li>
 * </ul>
 * Les étiquettes ne prennent que des valeurs bornées, fixées par le code.
 */
@ApplicationScoped
public class EchecsAsynchrones {

    static final String METRIQUE_TRAITEMENTS = "argos.traitements.fond.echecs";
    static final String METRIQUE_EVENEMENTS = "argos.evenements.asynchrones.echecs";

    @Inject
    MeterRegistry registre;

    /** Compte l'échec d'une tâche de fond. */
    public void compterTraitement(String nomTraitement) {
        compteur(METRIQUE_TRAITEMENTS, "Tâches de fond terminées en erreur", "traitement", nomTraitement).increment();
    }

    /** Compte l'échec de la notification asynchrone d'un événement. */
    public void compterEvenement(String typeEvenement) {
        compteur(METRIQUE_EVENEMENTS, "Notifications asynchrones en erreur", "evenement", typeEvenement).increment();
    }

    private Counter compteur(String nom, String description, String etiquette, String valeur) {
        return Counter.builder(nom).description(description).tag(etiquette, valeur).register(registre);
    }
}
