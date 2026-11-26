package io.argos.conservation;

import java.time.Instant;

import jakarta.inject.Inject;

import io.argos.commun.Conversions;

/**
 * Règle de conservation dont la suppression ne prend qu'un horodatage limite (ADR-0041) : la sous-classe, déclarée
 * par le module propriétaire de la table, fournit son nom et son SQL, dont le premier paramètre est la limite
 * ({@code timestamptz}) et le dernier la taille du lot ({@code … LIMIT ?}).
 */
public abstract class RegleParHorodatage implements RegleConservation {

    @Inject
    SuppressionParLots suppression;

    private final String nomRegle;
    private final String suppressionSql;

    protected RegleParHorodatage(String nomRegle, String suppressionSql) {
        this.nomRegle = nomRegle;
        this.suppressionSql = suppressionSql;
    }

    @Override
    public String nom() {
        return nomRegle;
    }

    @Override
    public long purger(Instant limite) {
        return suppression.supprimer(suppressionSql, Conversions.utc(limite));
    }
}
