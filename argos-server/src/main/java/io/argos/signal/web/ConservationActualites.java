package io.argos.signal.web;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.conservation.RegleParHorodatage;

/**
 * Règle de conservation des actualités de la veille web ({@code argos.conservation.actualites}, ADR-0041, EXP-17) :
 * une actualité reçue pour la première fois avant la limite est supprimée, avec ses citations et ses rapprochements
 * (suppression en cascade, V10). La date retenue est celle de la réception : une page ancienne encore présente dans
 * un flux n'est pas supprimée puis recollectée chaque jour.
 */
@ApplicationScoped
public class ConservationActualites extends RegleParHorodatage {

    public ConservationActualites() {
        super("actualites", "DELETE FROM actualite WHERE id IN (SELECT id FROM actualite WHERE recu_le < ? LIMIT ?)");
    }
}
