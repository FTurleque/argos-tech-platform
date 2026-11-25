package io.argos.alerting;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.argos.commun.ContexteBase;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;

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
    Jdbc jdbc;

    /** Alertes du compte, des plus récentes aux plus anciennes, filtrées par canal et état s'ils sont donnés. */
    public List<AlerteVue> lister(UUID compteId, String canal, String etat, int limite) {
        int limiteBornee = Math.min(Math.max(limite, 1), 200);
        String canalNormalise = normaliser(canal);
        String etatNormalise = normaliser(etat);
        return base.executer(new ContexteBase.Contexte(compteId, null, null), connexion -> Jdbc.lister(connexion, COLONNES + """
                 WHERE compte_id = ? AND (?::varchar IS NULL OR canal = ?::varchar)
                   AND (?::varchar IS NULL OR etat = ?::varchar)
                 ORDER BY cree_le DESC, id LIMIT ?
                """, Jdbc.parametres(compteId, canalNormalise, canalNormalise, etatNormalise, etatNormalise, limiteBornee),
                ConsultationAlertes::vue));
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
            Jdbc.modifier(connexion, """
                    UPDATE alerte SET etat = 'ENVOYEE', envoye_le = coalesce(envoye_le, now())
                    WHERE id = ? AND compte_id = ? AND canal = 'DESKTOP'
                    """, Jdbc.parametres(alerteId, compteId));
            return Remise.REMISE;
        });
    }

    /** Existence d'une alerte, lue hors rôle applicatif pour journaliser un refus ; jamais restituée. */
    public boolean existe(UUID alerteId) {
        return jdbc.unique("SELECT 1 FROM alerte WHERE id = ?", Jdbc.parametres(alerteId), resultat -> Boolean.TRUE)
                .isPresent();
    }

    private static Optional<AlerteVue> lire(Connection connexion, UUID compteId, UUID alerteId) {
        return Jdbc.unique(connexion, COLONNES + " WHERE id = ? AND compte_id = ?", Jdbc.parametres(alerteId, compteId),
                ConsultationAlertes::vue);
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
                resultat.getString(5), resultat.getString(6), contenu, resultat.getInt(8), Conversions.instant(resultat, 9),
                Conversions.instant(resultat, 10));
    }
}
