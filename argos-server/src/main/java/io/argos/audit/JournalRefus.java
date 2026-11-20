package io.argos.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Journal des refus d'accès (ACC-06, ADR-0016).
 *
 * <p>Chaque entrée enregistre le compte (ou, à défaut, le sujet OIDC), la ressource demandée
 * (méthode et chemin, sans paramètre de requête ni corps), le motif et l'horodatage. Aucune
 * donnée de la ressource refusée n'est recopiée. L'écriture se fait dans sa propre transaction,
 * indépendamment de la requête refusée.
 */
@ApplicationScoped
public class JournalRefus {

    private static final Logger LOG = Logger.getLogger(JournalRefus.class);
    private static final int LONGUEUR_MAX_RESSOURCE = 500;

    @Inject
    DataSource dataSource;

    public void enregistrer(UUID compteId, String sujetOidc, String ressource, String motif) {
        String ressourceBornee = ressource.length() > LONGUEUR_MAX_RESSOURCE
                ? ressource.substring(0, LONGUEUR_MAX_RESSOURCE) : ressource;
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement(
                        "INSERT INTO refus_acces (compte_id, sujet_oidc, ressource, motif) VALUES (?, ?, ?, ?)")) {
            requete.setObject(1, compteId);
            requete.setString(2, sujetOidc);
            requete.setString(3, ressourceBornee);
            requete.setString(4, motif);
            requete.executeUpdate();
        } catch (SQLException erreur) {
            // Le refus est appliqué même si sa trace échoue ; l'échec reste visible dans les journaux.
            LOG.errorf(erreur, "Refus d'accès non journalisé : %s %s", motif, ressourceBornee);
        }
    }
}
