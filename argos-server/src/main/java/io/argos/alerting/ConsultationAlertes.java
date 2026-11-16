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
        int n = Math.min(Math.max(limite, 1), 200);
        return base.executer(new ContexteBase.Contexte(compteId, null, null), c -> {
            List<AlerteVue> vues = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(COLONNES + """
                     WHERE compte_id = ? AND (?::varchar IS NULL OR canal = ?::varchar)
                       AND (?::varchar IS NULL OR etat = ?::varchar)
                     ORDER BY cree_le DESC, id LIMIT ?
                    """)) {
                String cn = normaliser(canal);
                String et = normaliser(etat);
                ps.setObject(1, compteId);
                ps.setString(2, cn);
                ps.setString(3, cn);
                ps.setString(4, et);
                ps.setString(5, et);
                ps.setInt(6, n);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        vues.add(vue(rs));
                    }
                }
            }
            return vues;
        });
    }

    /** Alerte du compte. */
    public Optional<AlerteVue> obtenir(UUID compteId, UUID alerteId) {
        return base.executer(new ContexteBase.Contexte(compteId, null, null), c -> lire(c, compteId, alerteId));
    }

    /** Confirme la remise d'une alerte {@code DESKTOP} du compte : elle passe {@code ENVOYEE}. */
    public Remise remettre(UUID compteId, UUID alerteId) {
        return base.executer(new ContexteBase.Contexte(compteId, null, null), c -> {
            Optional<AlerteVue> vue = lire(c, compteId, alerteId);
            if (vue.isEmpty()) {
                return Remise.INTROUVABLE;
            }
            if (!"DESKTOP".equals(vue.get().canal())) {
                return Remise.CANAL_NON_DESKTOP;
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE alerte SET etat = 'ENVOYEE', envoye_le = coalesce(envoye_le, now())
                    WHERE id = ? AND compte_id = ? AND canal = 'DESKTOP'
                    """)) {
                ps.setObject(1, alerteId);
                ps.setObject(2, compteId);
                ps.executeUpdate();
            }
            return Remise.REMISE;
        });
    }

    /** Existence d'une alerte, lue hors rôle applicatif pour journaliser un refus ; jamais restituée. */
    public boolean existe(UUID alerteId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM alerte WHERE id = ?")) {
            ps.setObject(1, alerteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'alerte impossible", e);
        }
    }

    private static Optional<AlerteVue> lire(Connection c, UUID compteId, UUID alerteId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(COLONNES + " WHERE id = ? AND compte_id = ?")) {
            ps.setObject(1, alerteId);
            ps.setObject(2, compteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(vue(rs)) : Optional.empty();
            }
        }
    }

    private static String normaliser(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip().toUpperCase(Locale.ROOT);
    }

    private static AlerteVue vue(ResultSet rs) throws SQLException {
        ContenuAlerte contenu;
        try {
            contenu = MoteurAlertes.JSON.readValue(rs.getString(7), ContenuAlerte.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("contenu d'alerte illisible", e);
        }
        return new AlerteVue(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), contenu, rs.getInt(8), instant(rs, 9), instant(rs, 10));
    }

    private static Instant instant(ResultSet rs, int colonne) throws SQLException {
        OffsetDateTime v = rs.getObject(colonne, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }
}
