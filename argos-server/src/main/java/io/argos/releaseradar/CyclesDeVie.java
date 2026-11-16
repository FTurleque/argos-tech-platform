package io.argos.releaseradar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Copie locale du calendrier endoflife.date (données publiques, lisibles par tout compte) et fraîcheur
 * de chaque produit suivi. Une synchronisation remplace en bloc les cycles d'un produit ; un échec
 * conserve les cycles déjà connus et passe le produit {@code EN_ERREUR} avec son motif.
 */
@ApplicationScoped
public class CyclesDeVie {

    private static final Logger LOG = Logger.getLogger(CyclesDeVie.class);

    /** Fraîcheur d'un produit suivi. */
    public record EtatProduit(String produit, String etat, Instant derniereSynchro, Instant dernierSucces,
            String derniereErreur) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    ClientEndoflife client;

    /** Synchronise chaque produit ; un échec n'interrompt pas les autres. */
    public Map<String, String> synchroniser(Collection<String> produits, Instant maintenant) {
        Map<String, String> etats = new LinkedHashMap<>();
        for (String produit : produits) {
            try {
                List<ClientEndoflife.Cycle> cycles = client.cycles(produit);
                remplacer(produit, cycles, maintenant);
                etats.put(produit, "A_JOUR");
            } catch (ClientEndoflife.SourceIndisponible e) {
                LOG.warnf("synchronisation endoflife.date du produit %s en échec : %s", produit, e.getMessage());
                echec(produit, e.getMessage(), maintenant);
                etats.put(produit, "EN_ERREUR");
            } catch (RuntimeException e) {
                // Erreur de base ou réponse inattendue : le produit passe en erreur, les autres continuent.
                LOG.errorf(e, "synchronisation du produit %s interrompue", produit);
                echec(produit, "erreur interne de synchronisation", maintenant);
                etats.put(produit, "EN_ERREUR");
            }
        }
        return etats;
    }

    /** Enregistre l'échec sans jamais interrompre la synchronisation des autres produits. */
    private void echec(String produit, String motif, Instant maintenant) {
        try {
            marquerEchec(produit, motif == null ? "motif inconnu" : motif, maintenant);
        } catch (RuntimeException e) {
            LOG.errorf(e, "échec de synchronisation du produit %s non enregistré", produit);
        }
    }

    /** Fraîcheur des produits demandés, connus d'Argos. */
    public List<EtatProduit> etats(Collection<String> produits) {
        List<EtatProduit> liste = new ArrayList<>();
        if (produits.isEmpty()) {
            return liste;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT produit, etat, derniere_synchro_le, dernier_succes_le, derniere_erreur FROM cycle_vie_produit
                WHERE produit = ANY (?) ORDER BY produit
                """)) {
            ps.setArray(1, c.createArrayOf("varchar", produits.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    liste.add(new EtatProduit(rs.getString(1), rs.getString(2), instant(rs, 3), instant(rs, 4),
                            rs.getString(5)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de la fraîcheur du calendrier impossible", e);
        }
        return liste;
    }

    private void remplacer(String produit, List<ClientEndoflife.Cycle> cycles, Instant maintenant) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO cycle_vie_produit (produit, etat, derniere_synchro_le, dernier_succes_le)
                        VALUES (?, 'A_JOUR', ?, ?)
                        ON CONFLICT (produit) DO UPDATE SET etat = 'A_JOUR', derniere_synchro_le = excluded.derniere_synchro_le,
                            dernier_succes_le = excluded.dernier_succes_le, derniere_erreur = NULL
                        """)) {
                    OffsetDateTime t = maintenant.atOffset(ZoneOffset.UTC);
                    ps.setString(1, produit);
                    ps.setObject(2, t);
                    ps.setObject(3, t);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM cycle_vie WHERE produit = ?")) {
                    ps.setString(1, produit);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO cycle_vie (produit, cycle, sortie_le, fin_support_le, en_fin_support, derniere_version,
                            derniere_version_le) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    for (ClientEndoflife.Cycle cy : cycles) {
                        ps.setString(1, produit);
                        ps.setString(2, cy.cycle());
                        ps.setObject(3, cy.sortie());
                        ps.setObject(4, cy.finSupport());
                        ps.setBoolean(5, cy.enFinSupport());
                        ps.setString(6, cy.derniereVersion());
                        ps.setObject(7, cy.derniereVersionLe());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement du calendrier impossible", e);
        }
    }

    private void marquerEchec(String produit, String motif, Instant maintenant) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO cycle_vie_produit (produit, etat, derniere_synchro_le, derniere_erreur)
                VALUES (?, 'EN_ERREUR', ?, ?)
                ON CONFLICT (produit) DO UPDATE SET etat = 'EN_ERREUR', derniere_synchro_le = excluded.derniere_synchro_le,
                    derniere_erreur = excluded.derniere_erreur
                """)) {
            ps.setString(1, produit);
            ps.setObject(2, maintenant.atOffset(ZoneOffset.UTC));
            ps.setString(3, motif.length() > 1000 ? motif.substring(0, 1000) : motif);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de l'échec de synchronisation impossible", e);
        }
    }

    private static Instant instant(ResultSet rs, int colonne) throws SQLException {
        OffsetDateTime t = rs.getObject(colonne, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
}
