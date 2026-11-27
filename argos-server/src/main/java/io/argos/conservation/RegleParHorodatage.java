package io.argos.conservation;

import java.time.Instant;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import io.argos.commun.Conversions;

/**
 * Règle de conservation dont la suppression ne prend qu'un horodatage limite (ADR-0041) : la sous-classe, déclarée
 * par le module propriétaire de la table, fournit son nom, sa table et la colonne d'horodatage comparée à la limite ;
 * les lignes plus anciennes que la limite sont supprimées par lots.
 */
public abstract class RegleParHorodatage implements RegleConservation {

    /** Suppression d'un lot : table, puis colonne d'horodatage ; paramètres : limite, taille du lot. */
    private static final String GABARIT_SUPPRESSION = "DELETE FROM %1$s WHERE id IN (SELECT id FROM %1$s WHERE %2$s < ? LIMIT ?)";
    private static final Pattern IDENTIFIANT_SQL = Pattern.compile("[a-z_][a-z0-9_]*");

    @Inject
    SuppressionParLots suppression;

    private final String nomRegle;
    private final String suppressionSql;

    /**
     * @param table nom de la table, constante du module propriétaire
     * @param colonneHorodatage colonne {@code timestamptz} comparée à la limite, constante du module propriétaire
     */
    protected RegleParHorodatage(String nomRegle, String table, String colonneHorodatage) {
        this.nomRegle = nomRegle;
        this.suppressionSql = GABARIT_SUPPRESSION.formatted(identifiant(table), identifiant(colonneHorodatage));
    }

    private static String identifiant(String nom) {
        if (!IDENTIFIANT_SQL.matcher(nom).matches()) {
            throw new IllegalArgumentException("identifiant SQL invalide : " + nom);
        }
        return nom;
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
