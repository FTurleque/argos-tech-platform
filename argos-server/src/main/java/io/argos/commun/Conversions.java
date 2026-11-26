package io.argos.commun;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Conversions communes entre les types JDBC et les types du domaine. */
public final class Conversions {

    private Conversions() {
    }

    /** Instant d'une colonne {@code timestamptz} ; une valeur SQL {@code NULL} donne {@code null}. */
    public static Instant instant(ResultSet resultat, int colonne) throws SQLException {
        return versInstant(resultat.getObject(colonne, OffsetDateTime.class));
    }

    /** Instant d'une colonne {@code timestamptz} désignée par son nom. */
    static Instant instant(ResultSet resultat, String colonne) throws SQLException {
        return versInstant(resultat.getObject(colonne, OffsetDateTime.class));
    }

    private static Instant versInstant(OffsetDateTime horodatage) {
        return horodatage == null ? null : horodatage.toInstant();
    }

    /** Horodatage UTC d'un instant, à lier à une colonne {@code timestamptz} ; {@code null} reste {@code null}. */
    public static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /** Texte tronqué à {@code longueurMax} caractères ; {@code null} reste {@code null}. */
    public static String couper(String texte, int longueurMax) {
        return texte == null || texte.length() <= longueurMax ? texte : texte.substring(0, longueurMax);
    }
}
