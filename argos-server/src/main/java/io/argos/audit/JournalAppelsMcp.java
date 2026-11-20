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
 * Journal des appels d'outils MCP (MCP-06, ADR-0014).
 *
 * <p>Chaque entrée enregistre le compte (ou, à défaut, le sujet OIDC), l'outil, ses paramètres
 * sérialisés en JSON et bornés, la durée et l'issue de l'appel. Le résultat renvoyé à l'assistant
 * n'est jamais recopié. L'écriture se fait dans sa propre transaction, sous l'utilisateur de
 * connexion : le rôle applicatif {@code argos_app} n'a aucun droit sur la table (migration V9).
 */
@ApplicationScoped
public class JournalAppelsMcp {

    /** Issue d'un appel. */
    public enum Issue { SUCCES, PARAMETRES_INVALIDES, INTROUVABLE, REFUS_ACCES, REFUS_LIMITE, ERREUR }

    private static final Logger LOG = Logger.getLogger(JournalAppelsMcp.class);
    private static final int LONGUEUR_MAX_OUTIL = 100;

    @Inject
    DataSource dataSource;

    /**
     * Enregistre un appel. {@code parametresJson} doit être un document JSON valide, déjà borné par
     * l'appelant.
     */
    public void enregistrer(UUID compteId, String sujetOidc, String outil, String parametresJson, long dureeMs,
            Issue issue) {
        String outilBorne = outil.length() > LONGUEUR_MAX_OUTIL ? outil.substring(0, LONGUEUR_MAX_OUTIL) : outil;
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("""
                        INSERT INTO mcp_appel (compte_id, sujet_oidc, outil, parametres, duree_ms, issue)
                        VALUES (?, ?, ?, ?::jsonb, ?, ?)
                        """)) {
            requete.setObject(1, compteId);
            requete.setString(2, compteId == null ? sujetOidc : null);
            requete.setString(3, outilBorne);
            requete.setString(4, parametresJson);
            requete.setInt(5, (int) Math.min(Math.max(dureeMs, 0), Integer.MAX_VALUE));
            requete.setString(6, issue.name());
            requete.executeUpdate();
        } catch (SQLException erreur) {
            // L'appel a déjà été traité ; l'échec de sa trace reste visible dans les journaux.
            LOG.errorf(erreur, "Appel MCP non journalisé : %s %s", outilBorne, issue);
        }
    }
}
