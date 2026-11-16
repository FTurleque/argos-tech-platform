package io.argos.catalog.standard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Lectures techniques du référentiel, sous l'utilisateur de connexion, toujours restreintes aux
 * espaces demandés par l'appelant : point d'extension {@link ReferentielStandards} du serveur MCP
 * (espaces de l'utilisateur MCP), technologies suivies par le radar et produits endoflife.date à
 * synchroniser. Aucune de ces lectures ne modifie le référentiel.
 */
@ApplicationScoped
public class ReferentielStandardsEnBase implements ReferentielStandards {

    /**
     * Technologie du référentiel d'un espace.
     *
     * @param produitCycleVie produit endoflife.date relié, absent si l'espace n'en a pas renseigné
     */
    public record TechnologieSuivie(UUID espaceId, String cle, String libelle, Statut statut, String versionRecommandee,
            String produitCycleVie) {
    }

    @Inject
    DataSource dataSource;

    @Override
    public List<Standard> standards(String cle, Set<UUID> espaces) {
        List<Standard> standards = new ArrayList<>();
        if (cle == null || espaces.isEmpty()) {
            return standards;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT espace_id, cle, statut, version_recommandee, justification FROM standard_technologie
                WHERE cle = ? AND espace_id = ANY (?) ORDER BY espace_id
                """)) {
            ps.setString(1, cle);
            ps.setArray(2, c.createArrayOf("uuid", espaces.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    standards.add(new Standard(rs.getObject(1, UUID.class), rs.getString(2), Statut.valueOf(rs.getString(3)),
                            rs.getString(4), rs.getString(5)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du référentiel impossible", e);
        }
        return standards;
    }

    /** Technologies des espaces donnés ; seulement celles reliées à un produit endoflife.date si demandé. */
    public List<TechnologieSuivie> technologies(Collection<UUID> espaces, boolean avecProduitSeulement) {
        List<TechnologieSuivie> liste = new ArrayList<>();
        if (espaces.isEmpty()) {
            return liste;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT espace_id, cle, libelle, statut, version_recommandee, produit_cycle_vie FROM standard_technologie
                WHERE espace_id = ANY (?) AND (NOT ? OR produit_cycle_vie IS NOT NULL) ORDER BY espace_id, cle
                """)) {
            ps.setArray(1, c.createArrayOf("uuid", espaces.toArray()));
            ps.setBoolean(2, avecProduitSeulement);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    liste.add(new TechnologieSuivie(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            Statut.valueOf(rs.getString(4)), rs.getString(5), rs.getString(6)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du référentiel impossible", e);
        }
        return liste;
    }

    /** Espaces dont au moins une technologie est reliée à un produit endoflife.date. */
    public List<UUID> espacesSuivis() {
        return lireListe("SELECT DISTINCT espace_id FROM standard_technologie WHERE produit_cycle_vie IS NOT NULL ORDER BY 1",
                UUID.class);
    }

    /** Produits endoflife.date reliés à au moins une technologie d'un espace : ceux à synchroniser. */
    public List<String> produitsSuivis() {
        return lireListe("SELECT DISTINCT produit_cycle_vie FROM standard_technologie WHERE produit_cycle_vie IS NOT NULL"
                + " ORDER BY 1", String.class);
    }

    private <T> List<T> lireListe(String sql, Class<T> type) {
        List<T> liste = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                liste.add(rs.getObject(1, type));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du référentiel impossible", e);
        }
        return liste;
    }
}
