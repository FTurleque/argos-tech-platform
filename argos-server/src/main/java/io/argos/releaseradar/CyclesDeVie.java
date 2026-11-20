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
            } catch (ClientEndoflife.SourceIndisponible erreur) {
                LOG.warnf("synchronisation endoflife.date du produit %s en échec : %s", produit, erreur.getMessage());
                echecOuJournaliser(produit, erreur.getMessage(), maintenant);
                etats.put(produit, "EN_ERREUR");
            } catch (IllegalStateException erreur) {
                // Panne d'accès aux données : le produit passe en erreur, les autres continuent.
                LOG.errorf(erreur, "synchronisation du produit %s interrompue", produit);
                echecOuJournaliser(produit, "erreur interne de synchronisation", maintenant);
                etats.put(produit, "EN_ERREUR");
            }
        }
        return etats;
    }

    /** Enregistre l'échec ; une panne d'accès aux données est journalisée et n'interrompt pas les autres produits. */
    private void echecOuJournaliser(String produit, String motif, Instant maintenant) {
        try {
            marquerEchec(produit, motif == null ? "motif inconnu" : motif, maintenant);
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "échec de synchronisation du produit %s non enregistré", produit);
        }
    }

    /** Fraîcheur des produits demandés, connus d'Argos. */
    public List<EtatProduit> etats(Collection<String> produits) {
        List<EtatProduit> liste = new ArrayList<>();
        if (produits.isEmpty()) {
            return liste;
        }
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT produit, etat, derniere_synchro_le, dernier_succes_le, derniere_erreur FROM cycle_vie_produit
                WHERE produit = ANY (?) ORDER BY produit
                """)) {
            requete.setArray(1, connexion.createArrayOf("varchar", produits.toArray()));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    liste.add(new EtatProduit(resultat.getString(1), resultat.getString(2), instant(resultat, 3), instant(resultat, 4),
                            resultat.getString(5)));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de la fraîcheur du calendrier impossible", erreur);
        }
        return liste;
    }

    private void remplacer(String produit, List<ClientEndoflife.Cycle> cycles, Instant maintenant) {
        try (Connection connexion = dataSource.getConnection()) {
            boolean autoCommit = connexion.getAutoCommit();
            connexion.setAutoCommit(false);
            try {
                try (PreparedStatement requete = connexion.prepareStatement("""
                        INSERT INTO cycle_vie_produit (produit, etat, derniere_synchro_le, dernier_succes_le)
                        VALUES (?, 'A_JOUR', ?, ?)
                        ON CONFLICT (produit) DO UPDATE SET etat = 'A_JOUR', derniere_synchro_le = excluded.derniere_synchro_le,
                            dernier_succes_le = excluded.dernier_succes_le, derniere_erreur = NULL
                        """)) {
                    OffsetDateTime horodatage = maintenant.atOffset(ZoneOffset.UTC);
                    requete.setString(1, produit);
                    requete.setObject(2, horodatage);
                    requete.setObject(3, horodatage);
                    requete.executeUpdate();
                }
                try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM cycle_vie WHERE produit = ?")) {
                    requete.setString(1, produit);
                    requete.executeUpdate();
                }
                try (PreparedStatement requete = connexion.prepareStatement("""
                        INSERT INTO cycle_vie (produit, cycle, sortie_le, fin_support_le, en_fin_support, derniere_version,
                            derniere_version_le) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    for (ClientEndoflife.Cycle cycle : cycles) {
                        requete.setString(1, produit);
                        requete.setString(2, cycle.cycle());
                        requete.setObject(3, cycle.sortie());
                        requete.setObject(4, cycle.finSupport());
                        requete.setBoolean(5, cycle.enFinSupport());
                        requete.setString(6, cycle.derniereVersion());
                        requete.setObject(7, cycle.derniereVersionLe());
                        requete.addBatch();
                    }
                    requete.executeBatch();
                }
                connexion.commit();
            } catch (SQLException | RuntimeException erreur) {
                connexion.rollback();
                throw erreur;
            } finally {
                connexion.setAutoCommit(autoCommit);
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement du calendrier impossible", erreur);
        }
    }

    private void marquerEchec(String produit, String motif, Instant maintenant) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO cycle_vie_produit (produit, etat, derniere_synchro_le, derniere_erreur)
                VALUES (?, 'EN_ERREUR', ?, ?)
                ON CONFLICT (produit) DO UPDATE SET etat = 'EN_ERREUR', derniere_synchro_le = excluded.derniere_synchro_le,
                    derniere_erreur = excluded.derniere_erreur
                """)) {
            requete.setString(1, produit);
            requete.setObject(2, maintenant.atOffset(ZoneOffset.UTC));
            requete.setString(3, motif.length() > 1000 ? motif.substring(0, 1000) : motif);
            requete.executeUpdate();
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement de l'échec de synchronisation impossible", erreur);
        }
    }

    private static Instant instant(ResultSet resultat, int colonne) throws SQLException {
        OffsetDateTime horodatage = resultat.getObject(colonne, OffsetDateTime.class);
        return horodatage == null ? null : horodatage.toInstant();
    }
}
