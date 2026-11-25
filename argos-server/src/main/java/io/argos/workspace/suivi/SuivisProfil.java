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

import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;

/**
 * Objets suivis et jours de rappel d'un compte pour un profil de veille (tables {@code suivi_profil}
 * et {@code suivi_rappels}, migration V15).
 *
 * <p>Les lectures et écritures d'un compte s'exécutent sous le rôle {@code argos_app} avec ce compte
 * comme compte courant ({@link ContexteBase}) : la RLS n'expose que ses propres suivis. Chaque écriture
 * remplace l'ensemble précédent pour le profil : l'opération est idempotente. Les remplacements d'un
 * même compte et d'un même profil sont sérialisés par un verrou transactionnel
 * ({@code pg_advisory_xact_lock}) : deux requêtes simultanées s'appliquent l'une après l'autre, la
 * dernière l'emporte.
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
        return base.executer(new Contexte(compte, null, null), connexion -> lire(connexion, compte, profil));
    }

    /**
     * Remplace les objets suivis du compte pour le profil : chaque type présent est remplacé, chaque
     * type absent est retiré. Les valeurs doivent avoir été confrontées au catalogue par l'appelant.
     */
    public Suivis remplacer(UUID compte, String profil, Map<String, List<String>> objets) {
        Objects.requireNonNull(compte);
        return base.executer(new Contexte(compte, null, null), connexion -> {
            verrouiller(connexion, compte, profil);
            try (PreparedStatement requete = connexion.prepareStatement(
                    "DELETE FROM suivi_profil WHERE compte_id = argos_compte_courant() AND profil = ?")) {
                requete.setString(1, profil);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO suivi_profil (compte_id, profil, type_objet, valeurs, modifie_le)
                    VALUES (argos_compte_courant(), ?, ?, ?, now())
                    """)) {
                for (Map.Entry<String, List<String>> entree : objets.entrySet()) {
                    if (entree.getValue().isEmpty()) {
                        continue;
                    }
                    requete.setString(1, profil);
                    requete.setString(2, entree.getKey());
                    requete.setArray(3, connexion.createArrayOf("varchar", entree.getValue().toArray()));
                    requete.addBatch();
                }
                requete.executeBatch();
            }
            return lire(connexion, compte, profil);
        });
    }

    /** Remplace les jours de rappel du compte pour le profil (valeurs validées par l'appelant). */
    public Suivis remplacerRappels(UUID compte, String profil, List<Integer> jours) {
        Objects.requireNonNull(compte);
        return base.executer(new Contexte(compte, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO suivi_rappels (compte_id, profil, jours, modifie_le)
                    VALUES (argos_compte_courant(), ?, ?, now())
                    ON CONFLICT (compte_id, profil) DO UPDATE SET jours = excluded.jours, modifie_le = now()
                    """)) {
                requete.setString(1, profil);
                requete.setArray(2, connexion.createArrayOf("integer", jours.toArray()));
                requete.executeUpdate();
            }
            return lire(connexion, compte, profil);
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
        try (Connection connexion = dataSource.getConnection()) {
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setString(1, profil);
                requete.setString(2, profil);
                try (ResultSet lignes = requete.executeQuery()) {
                    while (lignes.next()) {
                        comptes.add(lignes.getObject(1, UUID.class));
                        emails.add(lignes.getString(2));
                    }
                }
            }
            for (int position = 0; position < comptes.size(); position++) {
                resultat.add(new Suiveur(comptes.get(position), emails.get(position), lire(connexion, comptes.get(position), profil)));
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des suiveurs du profil " + profil + " impossible", erreur);
        }
        return resultat;
    }

    /** Verrou transactionnel sur le couple compte et profil, libéré à la fin de la transaction. */
    private static void verrouiller(Connection connexion, UUID compte, String profil) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            requete.setString(1, "suivi_profil:" + compte + ":" + profil);
            requete.executeQuery().close();
        }
    }

    private static Suivis lire(Connection connexion, UUID compte, String profil) throws SQLException {
        Map<String, List<String>> objets = new LinkedHashMap<>();
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT type_objet, valeurs FROM suivi_profil WHERE compte_id = ? AND profil = ? ORDER BY type_objet")) {
            requete.setObject(1, compte);
            requete.setString(2, profil);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    Array valeurs = lignes.getArray(2);
                    objets.put(lignes.getString(1), Arrays.asList((String[]) valeurs.getArray()));
                }
            }
        }
        Optional<List<Integer>> rappels = Optional.empty();
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT jours FROM suivi_rappels WHERE compte_id = ? AND profil = ?")) {
            requete.setObject(1, compte);
            requete.setString(2, profil);
            try (ResultSet lignes = requete.executeQuery()) {
                if (lignes.next()) {
                    rappels = Optional.of(Arrays.asList((Integer[]) lignes.getArray(1).getArray()));
                }
            }
        }
        return new Suivis(objets, rappels);
    }
}
