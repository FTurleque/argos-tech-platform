package io.argos.aigateway;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.conservation.RegleConservation;
import io.argos.conservation.SuppressionParLots;

/**
 * Règle de conservation des compteurs journaliers d'appels à l'IA ({@code argos.conservation.quotas-ia}, ADR-0041,
 * EXP-17) : un compteur dont le jour, au fuseau des quotas, précède celui de la limite est supprimé. Le compteur du
 * jour courant n'est jamais concerné tant que la durée est positive.
 */
@ApplicationScoped
public class ConservationQuotas implements RegleConservation {

    @Inject
    SuppressionParLots suppression;

    @ConfigProperty(name = "argos.ia.fuseau")
    ZoneId fuseau;

    @Override
    public String nom() {
        return "quotas-ia";
    }

    @Override
    public long purger(Instant limite) {
        LocalDate jourLimite = LocalDate.ofInstant(limite, fuseau);
        return suppression.supprimer("DELETE FROM ia_quota_jour WHERE (jour, portee) IN"
                + " (SELECT jour, portee FROM ia_quota_jour WHERE jour < ? LIMIT ?)", jourLimite);
    }
}
