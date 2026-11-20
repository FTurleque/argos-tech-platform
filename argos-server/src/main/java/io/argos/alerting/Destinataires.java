package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Destinataires possibles d'une alerte : membres des espaces des impacts, quel que soit leur rôle.
 * Lecture technique, hors rôle applicatif ; elle sert seulement à savoir pour qui évaluer les règles.
 * Le contenu de chaque alerte est ensuite relu sous le contexte du destinataire (RLS, ACC-04).
 */
@ApplicationScoped
public class Destinataires {

    /** Compte membre d'au moins un espace concerné, et son e-mail éventuel. */
    public record Destinataire(UUID compteId, String email) {
    }

    @Inject
    DataSource dataSource;

    /** Membres d'au moins un des espaces, une fois chacun. */
    public List<Destinataire> membres(Set<UUID> espaces) {
        if (espaces.isEmpty()) {
            return new ArrayList<>();
        }
        return lire("""
                SELECT DISTINCT c.id, c.email FROM appartenance a JOIN compte c ON c.id = a.compte_id
                WHERE a.espace_id = ANY (?) ORDER BY c.id
                """, (connexion, requete) -> requete.setArray(1, connexion.createArrayOf("uuid", espaces.toArray())));
    }

    /**
     * Membres de l'espace dont les préférences enregistrées activent le profil donné, avec leur e-mail.
     * Les préférences n'accordent aucun droit (PRF-05) : elles désignent seulement, parmi les membres, ceux
     * qui suivent ce profil.
     */
    public List<Destinataire> membresAvecProfil(UUID espace, String profil) {
        return lire("""
                SELECT c.id, c.email FROM appartenance a JOIN compte c ON c.id = a.compte_id
                JOIN workspace_preference_utilisateur p ON p.compte_id = c.id
                WHERE a.espace_id = ? AND ? = ANY (p.profils_actifs) ORDER BY c.id
                """, (connexion, requete) -> {
                    requete.setObject(1, espace);
                    requete.setString(2, profil);
                });
    }

    /** Liaison des paramètres d'une requête de destinataires. */
    @FunctionalInterface
    private interface Parametres {
        void lier(Connection connexion, PreparedStatement requete) throws SQLException;
    }

    /** Destinataires (compte, e-mail) lus par la requête donnée. */
    private List<Destinataire> lire(String sql, Parametres parametres) {
        List<Destinataire> lus = new ArrayList<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            parametres.lier(connexion, requete);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    lus.add(new Destinataire(resultat.getObject(1, UUID.class), resultat.getString(2)));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des destinataires impossible", erreur);
        }
        return lus;
    }

    /** Espaces dont le compte est membre, relus à chaque évaluation : une révocation s'applique aussitôt. */
    public Set<UUID> espacesDe(UUID compteId) {
        Set<UUID> result = new HashSet<>();
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT espace_id FROM appartenance WHERE compte_id = ?")) {
            requete.setObject(1, compteId);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    result.add(resultat.getObject(1, UUID.class));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des appartenances impossible", erreur);
        }
        return result;
    }
}
