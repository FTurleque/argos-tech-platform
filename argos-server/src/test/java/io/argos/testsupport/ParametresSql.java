package io.argos.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Requêtes de test paramétrées : les valeurs sont liées, jamais insérées dans le texte SQL. */
public final class ParametresSql {

    private ParametresSql() {
    }

    /** Prépare la requête et lie les paramètres dans l'ordre des {@code ?}. */
    public static PreparedStatement preparer(Connection connexion, String sql, Object... parametres) throws SQLException {
        PreparedStatement requete = connexion.prepareStatement(sql);
        try {
            for (int rang = 0; rang < parametres.length; rang++) {
                requete.setObject(rang + 1, parametres[rang]);
            }
            return requete;
        } catch (SQLException erreur) {
            requete.close();
            throw erreur;
        }
    }
}
