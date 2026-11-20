package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.argos.workspace.ContexteBase;

/**
 * Consultation des alertes et résumés d'un compte, et remise au client desktop (contrat utilisé par
 * {@code notifier-sur-le-poste}). Chaque requête filtre explicitement sur le compte et s'exécute sous
 * le rôle {@code argos_app} avec ce compte : la politique RLS de {@code alerte} sert de garde-fou.
 */
@ApplicationScoped
public class ConsultationAlertes {

    /** Alerte ou résumé tel que le voit son destinataire ; l'erreur technique d'envoi n'est pas exposée. */
    public record AlerteVue(UUID id, String nature, String canal, String etat, String titre, String gravite,
            ContenuAlerte contenu, int tentatives, Instant creeLe, Instant envoyeLe) {
    }

    /** Issue d'une demande de remise. */
    public enum Remise { REMISE, CANAL_NON_DESKTOP, INTROUVABLE }

    private static final String COLONNES = """
            SELECT id, nature, canal, etat, titre, gravite, contenu::text, tentatives, cree_le, envoye_le FROM alerte
            """;

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    /** Alertes du compte, des plus récentes aux plus anciennes, filtrées par canal et état s'ils sont donnés. */
    public List<AlerteVue> lister(UUID compteId, String canal, String etat, int limite) {
        int limiteBornee = Math.min(Math.max(limite, 1), 200);
        return base.executer(new ContexteBase.Contexte(compteId, null, null), connexion -> {
            List<AlerteVue> vues = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement(COLONNES + """
                     WHERE compte_id = ? AND (?::varchar IS NULL OR canal = ?::varchar)
                       AND (?::varchar IS NULL OR etat = ?::varchar)
                     ORDER BY cree_le DESC, id LIMIT ?
                    """)) {
                String canalNormalise = normaliser(canal);
                String etatNormalise = normaliser(etat);
                requete.setObject(1, compteId);
                requete.setString(2, canalNormalise);
                requete.setString(3, canalNormalise);
                requete.setString(4, etatNormalise);
                requete.setString(5, etatNormalise);
                requete.setInt(6, limiteBornee);
                try (ResultSet resultat = requete.executeQuery()) {
                    while (resultat.next()) {
                        vues.add(vue(resultat));
                    }
                }
            }
            return vues;
        });
    }

    /** Alerte du compte. */
    public Optional<AlerteVue> obtenir(UUID compteId, UUID alerteId) {
        return base.executer(new ContexteBase.Contexte(compteId, null, null), connexion -> lire(connexion, compteId, alerteId));
    }

    /** Confirme la remise d'une alerte {@code DESKTOP} du compte : elle passe {@code ENVOYEE}. */
    public Remise remettre(UUID compteId, UUID alerteId) {
        return base.executer(new ContexteBase.Contexte(compteId, null, null), connexion -> {
            Optional<AlerteVue> vue = lire(connexion, compteId, alerteId);
            if (vue.isEmpty()) {
                return Remise.INTROUVABLE;
            }
            if (!"DESKTOP".equals(vue.get().canal())) {
                return Remise.CANAL_NON_DESKTOP;
            }
            EnvoiAlertes.executer(connexion, """
                    UPDATE alerte SET etat = 'ENVOYEE', envoye_le = coalesce(envoye_le, now())
                    WHERE id = ? AND compte_id = ? AND canal = 'DESKTOP'
                    """, alerteId, compteId);
            return Remise.REMISE;
        });
    }

    /** Existence d'une alerte, lue hors rôle applicatif pour journaliser un refus ; jamais restituée. */
    public boolean existe(UUID alerteId) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT 1 FROM alerte WHERE id = ?")) {
            requete.setObject(1, alerteId);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'alerte impossible", erreur);
        }
    }

    private static Optional<AlerteVue> lire(Connection connexion, UUID compteId, UUID alerteId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(COLONNES + " WHERE id = ? AND compte_id = ?")) {
            requete.setObject(1, alerteId);
            requete.setObject(2, compteId);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? Optional.of(vue(resultat)) : Optional.empty();
            }
        }
    }

    private static String normaliser(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip().toUpperCase(Locale.ROOT);
    }

    private static AlerteVue vue(ResultSet resultat) throws SQLException {
        ContenuAlerte contenu;
        try {
            contenu = MoteurAlertes.JSON.readValue(resultat.getString(7), ContenuAlerte.class);
        } catch (JsonProcessingException erreur) {
            throw new SQLException("contenu d'alerte illisible", erreur);
        }
        return new AlerteVue(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3), resultat.getString(4),
                resultat.getString(5), resultat.getString(6), contenu, resultat.getInt(8), instant(resultat, 9), instant(resultat, 10));
    }

    private static Instant instant(ResultSet resultat, int colonne) throws SQLException {
        OffsetDateTime horodatage = resultat.getObject(colonne, OffsetDateTime.class);
        return horodatage == null ? null : horodatage.toInstant();
    }
}
