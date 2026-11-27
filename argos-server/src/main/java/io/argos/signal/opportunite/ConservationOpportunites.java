package io.argos.signal.opportunite;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.commun.Conversions;
import io.argos.conservation.RegleConservation;
import io.argos.conservation.SuppressionParLots;

/**
 * Règle de conservation des opportunités échues ({@code argos.conservation.opportunites}, ADR-0041, EXP-17-S5) : une
 * opportunité dont l'échéance précède la limite est supprimée. L'échéance est la date limite, ou, sans date limite, la
 * première collecte augmentée de l'âge maximal de restitution (ART-08). Une opportunité encore ouverte n'est jamais
 * supprimée tant que la durée de conservation est positive.
 */
@ApplicationScoped
public class ConservationOpportunites implements RegleConservation {

    @Inject
    SuppressionParLots suppression;

    @Inject
    Opportunites opportunites;

    @ConfigProperty(name = "argos.alerting.fuseau")
    ZoneId fuseau;

    @Override
    public String nom() {
        return "opportunites";
    }

    @Override
    public long purger(Instant limite) {
        LocalDate jourLimite = LocalDate.ofInstant(limite, fuseau);
        Instant collecteeAvant = limite.minus(opportunites.ageMax());
        return suppression.supprimer("DELETE FROM opportunite WHERE id IN (SELECT id FROM opportunite"
                + " WHERE date_limite < ? OR (date_limite IS NULL AND recu_le < ?) LIMIT ?)", jourLimite,
                Conversions.utc(collecteeAvant));
    }
}
