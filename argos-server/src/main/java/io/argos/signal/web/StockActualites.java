package io.argos.signal.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.ingestion.web.LecteurFlux.ElementFlux;
import io.argos.ingestion.web.LecteurPage;
import io.argos.ingestion.web.LecteurPage.Instantane;

/**
 * Écritures techniques de la collecte web, sous l'utilisateur de connexion comme les autres
 * collectes (protocole de la migration V2) : actualités dédupliquées par empreinte (WEB-05),
 * citations par source, instantané des pages surveillées (WEB-02) et état des sources (WEB-03-S2).
 *
 * <p>L'insertion conditionnelle sur l'empreinte unique laisse la base arbitrer deux collectes
 * concurrentes du même article ; la citation (actualité, source) est elle aussi unique.
 */
@ApplicationScoped
public class StockActualites {

    /** Ce dont la collecte a besoin pour une source. */
    public record SourceACollecter(UUID id, String type, String adresse, String libelle, UUID espaceId,
            String pageEmpreinte, String pageTexte) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    Event<ActualiteRecue> evenements;

    @Transactional
    public Optional<SourceACollecter> source(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, type, adresse, libelle, espace_id, page_empreinte, page_texte FROM source_web WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SourceACollecter(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de la source " + id + " impossible", e);
        }
    }

    /** Sources dont la prochaine collecte est échue, les plus anciennes d'abord. */
    @Transactional
    public List<UUID> echues(int limite) {
        List<UUID> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM source_web WHERE prochaine_collecte_le <= now() ORDER BY prochaine_collecte_le, id LIMIT ?")) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des sources échues impossible", e);
        }
        return ids;
    }

    /** Enregistre les éléments d'un flux ; renvoie le nombre de citations nouvelles pour cette source. */
    @Transactional
    public int integrerFlux(UUID sourceId, UUID espaceId, List<ElementFlux> elements) {
        int nouvelles = 0;
        try (Connection c = dataSource.getConnection()) {
            for (ElementFlux e : elements) {
                UUID actualite = actualite(c, Deduplication.article(e), "ARTICLE", e.titre(), e.lien(), e.texte(),
                        e.publieLe());
                if (citer(c, actualite, sourceId, e.lien())) {
                    nouvelles++;
                    evenements.fire(new ActualiteRecue(actualite, sourceId, espaceId));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement des actualités de la source " + sourceId + " impossible", e);
        }
        return nouvelles;
    }

    /**
     * Compare l'instantané d'une page au précédent ; un contenu différent crée une actualité datée
     * avec l'aperçu du changement (WEB-02-S1). Le premier instantané sert de référence, sans actualité.
     *
     * @return vrai si une actualité de changement a été citée
     */
    @Transactional
    public boolean integrerPage(SourceACollecter source, Instantane instantane) {
        try (Connection c = dataSource.getConnection()) {
            boolean change = source.pageEmpreinte() != null && !source.pageEmpreinte().equals(instantane.empreinte());
            boolean citee = false;
            if (change) {
                String titre = "Modification de la page « " + source.libelle() + " »";
                String apercu = LecteurPage.apercu(source.pageTexte(), instantane.texte());
                UUID actualite = actualite(c, Deduplication.changementPage(source.adresse(), instantane.empreinte()),
                        "CHANGEMENT_PAGE", titre, source.adresse(), apercu, Instant.now());
                citee = citer(c, actualite, source.id(), source.adresse());
                if (citee) {
                    evenements.fire(new ActualiteRecue(actualite, source.id(), source.espaceId()));
                }
            }
            if (source.pageEmpreinte() == null || change) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE source_web SET page_empreinte = ?, page_texte = ? WHERE id = ?")) {
                    ps.setString(1, instantane.empreinte());
                    ps.setString(2, instantane.texte());
                    ps.setObject(3, source.id());
                    ps.executeUpdate();
                }
            }
            return citee;
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de la page de la source " + source.id() + " impossible", e);
        }
    }

    /** Collecte réussie : source active, prochaine collecte après la fréquence. */
    @Transactional
    public void succes(UUID sourceId, Duration frequence) {
        executer("""
                UPDATE source_web SET etat = 'ACTIVE', echecs_consecutifs = 0, derniere_erreur = NULL,
                       derniere_collecte_le = now(), dernier_succes_le = now(), prochaine_collecte_le = ?
                WHERE id = ?
                """, Instant.now().plus(frequence).atOffset(ZoneOffset.UTC), sourceId);
    }

    /**
     * Collecte en échec : source {@code EN_ERREUR} avec le motif ; la prochaine tentative est
     * espacée (fréquence × 2^(échecs − 1), au plus × 8).
     */
    @Transactional
    public void echec(UUID sourceId, String motif, Duration frequence) {
        int echecs;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE source_web SET echecs_consecutifs = echecs_consecutifs + 1 WHERE id = ? RETURNING echecs_consecutifs")) {
            ps.setObject(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                echecs = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("mise à jour de la source " + sourceId + " impossible", e);
        }
        Duration attente = frequence.multipliedBy(1L << Math.min(echecs - 1, 3));
        executer("""
                UPDATE source_web SET etat = 'EN_ERREUR', derniere_erreur = ?, derniere_collecte_le = now(),
                       prochaine_collecte_le = ?
                WHERE id = ?
                """, couper(motif, 1000), Instant.now().plus(attente).atOffset(ZoneOffset.UTC), sourceId);
    }

    private static UUID actualite(Connection c, String empreinte, String type, String titre, String lien, String texte,
            Instant publieLe) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO actualite (id, empreinte, type, titre, lien, texte, publie_le, recu_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (empreinte) DO NOTHING
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, empreinte);
            ps.setString(3, type);
            ps.setString(4, couper(titre, 1000));
            ps.setString(5, lien);
            ps.setString(6, texte == null ? "" : texte);
            ps.setObject(7, publieLe == null ? null : publieLe.atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM actualite WHERE empreinte = ?")) {
            ps.setString(1, empreinte);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static boolean citer(Connection c, UUID actualite, UUID source, String lien) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO actualite_source (actualite_id, source_id, lien, recu_le) VALUES (?, ?, ?, now())
                ON CONFLICT DO NOTHING
                """)) {
            ps.setObject(1, actualite);
            ps.setObject(2, source);
            ps.setString(3, lien);
            return ps.executeUpdate() == 1;
        }
    }

    private void executer(String sql, Object... parametres) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametres.length; i++) {
                ps.setObject(i + 1, parametres[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("écriture de la collecte web impossible", e);
        }
    }

    private static String couper(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
