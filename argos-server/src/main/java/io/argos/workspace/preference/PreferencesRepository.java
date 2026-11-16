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

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Accès à la table {@code workspace_preference_utilisateur}.
 *
 * <p>Chaque écriture remplace la valeur précédente : l'opération est idempotente et la
 * dernière écriture l'emporte (conception, « Transactions, concurrence et idempotence »).
 */
@ApplicationScoped
public class PreferencesRepository {

    private final DataSource dataSource;

    @Inject
    public PreferencesRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Préférences enregistrées ; {@link PreferencesUtilisateur#aucune} si rien n'est enregistré. */
    public PreferencesUtilisateur lire(String utilisateur) {
        String sql = "SELECT mise_en_page, profils_actifs FROM workspace_preference_utilisateur WHERE utilisateur = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, utilisateur);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return PreferencesUtilisateur.aucune(utilisateur);
                }
                Array profils = rs.getArray("profils_actifs");
                List<String> actifs = profils == null ? List.of() : Arrays.asList((String[]) profils.getArray());
                return new PreferencesUtilisateur(utilisateur, Optional.ofNullable(rs.getString("mise_en_page")), actifs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture des préférences impossible", e);
        }
    }

    /** Remplace la mise en page enregistrée. */
    public void enregistrerMiseEnPage(String utilisateur, String miseEnPage) {
        String sql = """
                INSERT INTO workspace_preference_utilisateur (utilisateur, mise_en_page, modifie_le)
                VALUES (?, ?, now())
                ON CONFLICT (utilisateur)
                DO UPDATE SET mise_en_page = EXCLUDED.mise_en_page, modifie_le = now()
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, utilisateur);
            ps.setString(2, miseEnPage);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Enregistrement de la mise en page impossible", e);
        }
    }

    /** Remplace l'ensemble des profils actifs ; l'ensemble ne peut pas être vide (contrainte en base). */
    public void enregistrerProfils(String utilisateur, Set<String> profilsActifs) {
        String sql = """
                INSERT INTO workspace_preference_utilisateur (utilisateur, profils_actifs, modifie_le)
                VALUES (?, ?, now())
                ON CONFLICT (utilisateur)
                DO UPDATE SET profils_actifs = EXCLUDED.profils_actifs, modifie_le = now()
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, utilisateur);
            ps.setArray(2, c.createArrayOf("varchar", profilsActifs.stream().sorted().toArray()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Enregistrement des profils actifs impossible", e);
        }
    }
}
