package io.argos.architecture.exemples;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Gestion manuelle de la transaction d'une connexion, contre laquelle {@code TransactionsJtaTest} vérifie que sa règle
 * la refuse. Jamais exécutée.
 */
public final class TransactionManuelle {

    private TransactionManuelle() {
    }

    public static void ecrire(Connection connexion) throws SQLException {
        connexion.setAutoCommit(false);
        connexion.commit();
        connexion.rollback();
    }
}
