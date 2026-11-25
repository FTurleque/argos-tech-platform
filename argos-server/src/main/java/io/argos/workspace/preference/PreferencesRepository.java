package io.argos.workspace.preference;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.commun.ContexteBase;

/**
 * Accès à la table {@code workspace_preference_utilisateur}, clé : identifiant du compte Argos.
 *
 * <p>Chaque écriture remplace la valeur précédente : l'opération est idempotente et la
 * dernière écriture l'emporte (conception, « Transactions, concurrence et idempotence »).
 * Chaque transaction s'exécute sous le rôle {@code argos_app} avec le compte concerné comme compte
 * courant (protocole de la migration V2) : la RLS n'expose que ses propres préférences.
 */
@ApplicationScoped
public class PreferencesRepository {

    private final DataSource dataSource;

    @Inject
    public PreferencesRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Préférences enregistrées ; {@link PreferencesUtilisateur#aucune} si rien n'est enregistré. */
    @Transactional
    public PreferencesUtilisateur lire(UUID compte) {
        String sql = "SELECT mise_en_page, profils_actifs FROM workspace_preference_utilisateur WHERE compte_id = ?";
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(compte));
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setObject(1, compte);
                try (ResultSet resultat = requete.executeQuery()) {
                    if (!resultat.next()) {
                        return PreferencesUtilisateur.aucune(compte);
                    }
                    Array profils = resultat.getArray("profils_actifs");
                    List<String> actifs = profils == null ? List.of() : Arrays.asList((String[]) profils.getArray());
                    return new PreferencesUtilisateur(compte, Optional.ofNullable(resultat.getString("mise_en_page")), actifs);
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("Lecture des préférences impossible", erreur);
        }
    }

    /** Remplace la mise en page enregistrée. */
    @Transactional
    public void enregistrerMiseEnPage(UUID compte, String miseEnPage) {
        String sql = """
                INSERT INTO workspace_preference_utilisateur (compte_id, mise_en_page, modifie_le)
                VALUES (?, ?, now())
                ON CONFLICT (compte_id)
                DO UPDATE SET mise_en_page = EXCLUDED.mise_en_page, modifie_le = now()
                """;
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(compte));
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setObject(1, compte);
                requete.setString(2, miseEnPage);
                requete.executeUpdate();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("Enregistrement de la mise en page impossible", erreur);
        }
    }

    /** Remplace l'ensemble des profils actifs ; l'ensemble ne peut pas être vide (contrainte en base). */
    @Transactional
    public void enregistrerProfils(UUID compte, Set<String> profilsActifs) {
        enregistrerProfils(compte, profilsActifs, true);
    }

    /** Enregistre les profils actifs seulement si le compte n'en a encore enregistré aucun. */
    @Transactional
    public void enregistrerProfilsSiAucun(UUID compte, Set<String> profilsActifs) {
        enregistrerProfils(compte, profilsActifs, false);
    }

    private void enregistrerProfils(UUID compte, Set<String> profilsActifs, boolean remplacer) {
        String sql = """
                INSERT INTO workspace_preference_utilisateur (compte_id, profils_actifs, modifie_le)
                VALUES (?, ?, now())
                ON CONFLICT (compte_id)
                DO UPDATE SET profils_actifs = EXCLUDED.profils_actifs, modifie_le = now()
                """ + (remplacer ? "" : " WHERE workspace_preference_utilisateur.profils_actifs IS NULL");
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(compte));
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setObject(1, compte);
                requete.setArray(2, connexion.createArrayOf("varchar", profilsActifs.stream().sorted().toArray()));
                requete.executeUpdate();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("Enregistrement des profils actifs impossible", erreur);
        }
    }
}
