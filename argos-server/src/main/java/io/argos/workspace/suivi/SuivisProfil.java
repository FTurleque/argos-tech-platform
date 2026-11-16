package io.argos.workspace.suivi;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;

/**
 * Objets suivis et jours de rappel d'un compte pour un profil de veille (tables {@code suivi_profil}
 * et {@code suivi_rappels}, migration V15).
 *
 * <p>Les lectures et écritures d'un compte s'exécutent sous le rôle {@code argos_app} avec ce compte
 * comme compte courant ({@link ContexteBase}) : la RLS n'expose que ses propres suivis. Chaque écriture
 * remplace l'ensemble précédent pour le profil : l'opération est idempotente.
 *
 * <p>{@link #suiveurs} est une lecture technique, hors rôle applicatif, réservée aux traitements
 * planifiés (rappels) : elle sert seulement à savoir pour qui évaluer les rappels, comme
 * {@code alerting.Destinataires}.
 */
@ApplicationScoped
public class SuivisProfil {

    /** Suivis d'un compte pour un profil : valeurs par type d'objet, jours de rappel s'il en a choisi. */
    public record Suivis(Map<String, List<String>> objets, Optional<List<Integer>> rappelsJours) {

        public Suivis {
            Map<String, List<String>> copie = new LinkedHashMap<>();
            objets.forEach((type, valeurs) -> copie.put(type, List.copyOf(valeurs)));
            objets = java.util.Collections.unmodifiableMap(copie);
        }

        /** Valeurs suivies pour un type d'objet ; vide si aucune. */
        public List<String> valeurs(String typeObjet) {
            return objets.getOrDefault(typeObjet, List.of());
        }
    }

    /** Compte dont le profil est actif et qui a enregistré des suivis pour ce profil. */
    public record Suiveur(UUID compteId, String email, Suivis suivis) {
    }

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    /** Suivis enregistrés du compte pour le profil. */
    public Suivis lire(UUID compte, String profil) {
        Objects.requireNonNull(compte);
        return base.executer(new Contexte(compte, null, null), c -> lire(c, compte, profil));
    }

    /**
     * Remplace les objets suivis du compte pour le profil : chaque type présent est remplacé, chaque
     * type absent est retiré. Les valeurs doivent avoir été confrontées au catalogue par l'appelant.
     */
    public Suivis remplacer(UUID compte, String profil, Map<String, List<String>> objets) {
        Objects.requireNonNull(compte);
        return base.executer(new Contexte(compte, null, null), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM suivi_profil WHERE compte_id = argos_compte_courant() AND profil = ?")) {
                ps.setString(1, profil);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO suivi_profil (compte_id, profil, type_objet, valeurs, modifie_le)
                    VALUES (argos_compte_courant(), ?, ?, ?, now())
                    """)) {
                for (Map.Entry<String, List<String>> e : objets.entrySet()) {
                    if (e.getValue().isEmpty()) {
                        continue;
                    }
                    ps.setString(1, profil);
                    ps.setString(2, e.getKey());
                    ps.setArray(3, c.createArrayOf("varchar", e.getValue().toArray()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return lire(c, compte, profil);
        });
    }

    /** Remplace les jours de rappel du compte pour le profil (valeurs validées par l'appelant). */
    public Suivis remplacerRappels(UUID compte, String profil, List<Integer> jours) {
        Objects.requireNonNull(compte);
        return base.executer(new Contexte(compte, null, null), c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO suivi_rappels (compte_id, profil, jours, modifie_le)
                    VALUES (argos_compte_courant(), ?, ?, now())
                    ON CONFLICT (compte_id, profil) DO UPDATE SET jours = excluded.jours, modifie_le = now()
                    """)) {
                ps.setString(1, profil);
                ps.setArray(2, c.createArrayOf("integer", jours.toArray()));
                ps.executeUpdate();
            }
            return lire(c, compte, profil);
        });
    }

    /**
     * Comptes dont le profil figure parmi les profils actifs enregistrés et qui ont des suivis pour
     * ce profil, avec leur e-mail. Lecture technique pour les traitements planifiés.
     */
    public List<Suiveur> suiveurs(String profil) {
        List<UUID> comptes = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        String sql = """
                SELECT c.id, c.email FROM compte c
                JOIN workspace_preference_utilisateur p ON p.compte_id = c.id
                WHERE ? = ANY (p.profils_actifs)
                  AND EXISTS (SELECT 1 FROM suivi_profil s WHERE s.compte_id = c.id AND s.profil = ?)
                ORDER BY c.id
                """;
        List<Suiveur> resultat = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, profil);
                ps.setString(2, profil);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        comptes.add(rs.getObject(1, UUID.class));
                        emails.add(rs.getString(2));
                    }
                }
            }
            for (int i = 0; i < comptes.size(); i++) {
                resultat.add(new Suiveur(comptes.get(i), emails.get(i), lire(c, comptes.get(i), profil)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des suiveurs du profil " + profil + " impossible", e);
        }
        return resultat;
    }

    private static Suivis lire(Connection c, UUID compte, String profil) throws SQLException {
        Map<String, List<String>> objets = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT type_objet, valeurs FROM suivi_profil WHERE compte_id = ? AND profil = ? ORDER BY type_objet")) {
            ps.setObject(1, compte);
            ps.setString(2, profil);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Array valeurs = rs.getArray(2);
                    objets.put(rs.getString(1), Arrays.asList((String[]) valeurs.getArray()));
                }
            }
        }
        Optional<List<Integer>> rappels = Optional.empty();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT jours FROM suivi_rappels WHERE compte_id = ? AND profil = ?")) {
            ps.setObject(1, compte);
            ps.setString(2, profil);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rappels = Optional.of(Arrays.asList((Integer[]) rs.getArray(1).getArray()));
                }
            }
        }
        return new Suivis(objets, rappels);
    }
}
