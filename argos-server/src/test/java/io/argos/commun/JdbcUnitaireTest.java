package io.argos.commun;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/** Traduction unique des erreurs SQL de l'utilitaire JDBC (ADR-0037), sans base. */
class JdbcUnitaireTest {

    private static final int LONGUEUR_SQL = 200;
    private static final int LONGUEUR_MESSAGE_MAX = 130;

    @Test
    void laTraductionTronqueUnLongSql() {
        ErreurBase erreur = Jdbc.traduire("SELECT\n    " + "x".repeat(LONGUEUR_SQL), new SQLException("cause"));
        assertTrue(erreur.getMessage().length() < LONGUEUR_MESSAGE_MAX);
        assertNotNull(erreur.getCause());
    }
}
