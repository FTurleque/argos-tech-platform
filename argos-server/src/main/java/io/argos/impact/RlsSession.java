package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Applique à la transaction courante le contexte de cloisonnement défini par la migration V2
 * (ADR-0016) : rôle {@code argos_app}, compte courant, aucun espace courant. La politique RLS de
 * {@code project_impact} n'expose alors que les impacts des espaces dont le compte est membre.
 *
 * <p>Même protocole que {@code io.argos.commun.ContexteBase#appliquer}, que {@code impact} ne
 * peut pas utiliser directement (ModuleBoundariesTest).
 */
final class RlsSession {

    private RlsSession() {
    }

    static void account(Connection connexion, UUID accountId) throws SQLException {
        try (Statement instruction = connexion.createStatement()) {
            instruction.execute("SET LOCAL ROLE argos_app");
        }
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT set_config('argos.compte_courant', ?, true), set_config('argos.sujet_courant', '', true),"
                        + " set_config('argos.email_courant', '', true), set_config('argos.espace_courant', '', true)")) {
            requete.setString(1, accountId == null ? "" : accountId.toString());
            requete.execute();
        }
    }
}
