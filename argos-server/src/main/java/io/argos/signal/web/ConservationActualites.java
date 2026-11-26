package io.argos.signal.web;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Conversions;
import io.argos.conservation.RegleConservation;
import io.argos.conservation.SuppressionParLots;

/**
 * Règle de conservation des actualités de la veille web ({@code argos.conservation.actualites}, ADR-0041, EXP-17) :
 * une actualité reçue pour la première fois avant la limite est supprimée, avec ses citations et ses rapprochements
 * (suppression en cascade, V10). La date de réception est retenue plutôt que la date de publication : une page
 * ancienne encore présente dans un flux n'est pas supprimée puis recollectée chaque jour.
 */
@ApplicationScoped
public class ConservationActualites implements RegleConservation {

    @Inject
    SuppressionParLots suppression;

    @Override
    public String nom() {
        return "actualites";
    }

    @Override
    public long purger(Instant limite) {
        return suppression.supprimer("DELETE FROM actualite WHERE id IN"
                + " (SELECT id FROM actualite WHERE recu_le < ? LIMIT ?)", Conversions.utc(limite));
    }
}
