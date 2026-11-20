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
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT espace_id, cle, statut, version_recommandee, justification FROM standard_technologie
                WHERE cle = ? AND espace_id = ANY (?) ORDER BY espace_id
                """)) {
            requete.setString(1, cle);
            requete.setArray(2, connexion.createArrayOf("uuid", espaces.toArray()));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    standards.add(new Standard(resultat.getObject(1, UUID.class), resultat.getString(2),
                            Statut.valueOf(resultat.getString(3)), resultat.getString(4), resultat.getString(5)));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture du référentiel impossible", erreur);
        }
        return standards;
    }

    /** Technologies des espaces donnés ; seulement celles reliées à un produit endoflife.date si demandé. */
    public List<TechnologieSuivie> technologies(Collection<UUID> espaces, boolean avecProduitSeulement) {
        List<TechnologieSuivie> liste = new ArrayList<>();
        if (espaces.isEmpty()) {
            return liste;
        }
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT espace_id, cle, libelle, statut, version_recommandee, produit_cycle_vie FROM standard_technologie
                WHERE espace_id = ANY (?) AND (NOT ? OR produit_cycle_vie IS NOT NULL) ORDER BY espace_id, cle
                """)) {
            requete.setArray(1, connexion.createArrayOf("uuid", espaces.toArray()));
            requete.setBoolean(2, avecProduitSeulement);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    liste.add(new TechnologieSuivie(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                            Statut.valueOf(resultat.getString(4)), resultat.getString(5), resultat.getString(6)));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture du référentiel impossible", erreur);
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
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql);
                ResultSet resultat = requete.executeQuery()) {
            while (resultat.next()) {
                liste.add(resultat.getObject(1, type));
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture du référentiel impossible", erreur);
        }
        return liste;
    }
}
