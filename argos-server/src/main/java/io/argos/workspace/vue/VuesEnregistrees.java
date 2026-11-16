package io.argos.workspace.vue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.workspace.AccesRefuse;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.Introuvable;
import io.argos.workspace.MotifRefus;
import io.argos.workspace.RegleViolee;

/**
 * Vues enregistrées (RCH-04) : une recherche nommée d'un compte, visible de lui seul. Les critères
 * sont un document JSON validé par la couche d'exposition ; ce module ne les interprète pas et une vue
 * n'accorde aucun droit : elle est rejouée sous les droits courants de son auteur.
 *
 * <p>Chaque opération s'exécute sous la RLS ({@link ContexteBase}) : la politique de
 * {@code workspace_vue} (migration V11) n'expose que les vues du compte courant. La vue d'un autre
 * compte est refusée et le refus journalisé ; une vue inexistante est seulement « introuvable ». Dans
 * les deux cas, la réponse est identique (404, « Ressource introuvable. ») et ne révèle pas
 * l'existence de la vue.
 */
@ApplicationScoped
public class VuesEnregistrees {

    /** Vue enregistrée ; {@code criteres} est le document JSON des mots et des filtres. */
    public record Vue(UUID id, String nom, String criteres, Instant creeLe) {
    }

    static final int NOM_MAX = 120;
    static final int CRITERES_MAX = 20_000;

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.recherche.vues-max", defaultValue = "100")
    int vuesMax;

    /** Vues du compte, par nom. */
    public List<Vue> lister(UUID compteId) {
        return base.executer(new Contexte(compteId, null, null), c -> {
            List<Vue> vues = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, nom, criteres::text, cree_le FROM workspace_vue WHERE compte_id = ? ORDER BY nom, id")) {
                ps.setObject(1, compteId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        vues.add(vue(rs));
                    }
                }
            }
            return vues;
        });
    }

    /** Enregistre une vue ; nom unique par compte, nombre de vues borné ({@code argos.recherche.vues-max}). */
    public Vue creer(UUID compteId, String nom, String criteresJson) {
        String n = nom == null ? "" : nom.strip();
        if (n.isEmpty() || n.length() > NOM_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le nom de la vue compte de 1 à " + NOM_MAX + " caractères.", "nom");
        }
        if (criteresJson == null || criteresJson.length() > CRITERES_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Critères de la vue absents ou trop longs.", "criteres");
        }
        UUID id = UUID.randomUUID();
        return base.executer(new Contexte(compteId, null, null), c -> {
            // Créations concurrentes du même compte sérialisées : le plafond ne peut pas être dépassé.
            try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                ps.setString(1, "workspace_vue:" + compteId);
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*), count(*) FILTER (WHERE nom = ?) FROM workspace_vue")) {
                ps.setString(1, n);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong(2) > 0) {
                        throw new RegleViolee("VUE_EXISTANTE", "Vous avez déjà une vue nommée ainsi.");
                    }
                    if (rs.getLong(1) >= vuesMax) {
                        throw new RegleViolee("LIMITE_VUES", "Nombre maximal de vues enregistrées atteint (" + vuesMax + ").");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO workspace_vue (id, compte_id, nom, criteres) VALUES (?, ?, ?, ?::jsonb)
                    RETURNING id, nom, criteres::text, cree_le
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, compteId);
                ps.setString(3, n);
                ps.setString(4, criteresJson);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return vue(rs);
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    // Création concurrente du même nom : l'unicité est garantie par la base.
                    throw new RegleViolee("VUE_EXISTANTE", "Vous avez déjà une vue nommée ainsi.");
                }
                throw e;
            }
        });
    }

    /** Vue du compte ; refus journalisé si elle appartient à un autre compte, « introuvable » si elle n'existe pas. */
    public Vue trouver(UUID compteId, UUID id) {
        Optional<Vue> vue = base.executer(new Contexte(compteId, null, null), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, nom, criteres::text, cree_le FROM workspace_vue WHERE id = ?")) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(vue(rs)) : Optional.empty();
                }
            }
        });
        return vue.orElseThrow(() -> refus(id));
    }

    /** Supprime une vue du compte ; mêmes refus que {@link #trouver}. */
    public void supprimer(UUID compteId, UUID id) {
        int supprimees = base.executer(new Contexte(compteId, null, null), c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM workspace_vue WHERE id = ?")) {
                ps.setObject(1, id);
                return ps.executeUpdate();
            }
        });
        if (supprimees == 0) {
            throw refus(id);
        }
    }

    /**
     * Refus d'une vue invisible sous la RLS : l'existence est lue hors rôle applicatif pour décider s'il
     * faut journaliser ; rien de la vue n'est restitué.
     */
    private RuntimeException refus(UUID id) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT 1 FROM workspace_vue WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new AccesRefuse(MotifRefus.NON_MEMBRE) : new Introuvable("Ressource introuvable.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de la vue impossible", e);
        }
    }

    private static Vue vue(ResultSet rs) throws SQLException {
        OffsetDateTime cree = rs.getObject(4, OffsetDateTime.class);
        return new Vue(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), cree == null ? null : cree.toInstant());
    }
}
