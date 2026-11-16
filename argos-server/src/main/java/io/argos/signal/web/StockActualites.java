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
import jakarta.transaction.Transactional.TxType;

import org.jboss.logging.Logger;

import io.argos.ingestion.web.LecteurFlux.ElementFlux;
import io.argos.ingestion.web.LecteurPage;
import io.argos.ingestion.web.LecteurPage.Instantane;

/**
 * Écritures techniques de la collecte web, sous l'utilisateur de connexion comme les autres
 * collectes (protocole de la migration V2) : actualités dédupliquées par empreinte (WEB-05),
 * citations par source, instantané des pages surveillées (WEB-02) et état des sources (WEB-03-S2).
 *
 * <p>Une actualité appartient au périmètre de la source qui la reçoit : l'espace d'une source
 * d'espace, le propriétaire d'une source personnelle. La déduplication se fait dans ce périmètre
 * seulement : une source ne fixe jamais le titre, le texte ni le lien vus dans un autre périmètre,
 * et l'insertion d'une actualité ne révèle rien de ceux-ci. L'insertion conditionnelle sur
 * (périmètre, empreinte) laisse la base arbitrer deux collectes concurrentes du même article ; la
 * citation (actualité, source) est elle aussi unique.
 *
 * <p>La collecte d'une source est sérialisée par un verrou consultatif de session PostgreSQL
 * ({@link #verrouiller(UUID)}) : la collecte à la demande, la première collecte et le planificateur
 * ne traitent jamais la même source en même temps.
 */
@ApplicationScoped
public class StockActualites {

    /** Ce dont la collecte a besoin pour une source. */
    public record SourceACollecter(UUID id, String type, String adresse, String libelle, UUID espaceId,
            UUID proprietaireId, String pageEmpreinte, String pageTexte) {
    }

    /**
     * Verrou consultatif de session sur une source, tenu par une connexion dédiée hors transaction ;
     * sa fermeture libère le verrou et rend la connexion.
     */
    public static final class Verrou implements AutoCloseable {

        private final Connection connexion;
        private final UUID sourceId;

        private Verrou(Connection connexion, UUID sourceId) {
            this.connexion = connexion;
            this.sourceId = sourceId;
        }

        @Override
        public void close() {
            try (Connection c = connexion; PreparedStatement ps = c.prepareStatement(
                    DEVERROUILLER)) {
                ps.setString(1, sourceId.toString());
                ps.execute();
            } catch (SQLException e) {
                LOG.errorf(e, "libération du verrou de collecte de la source %s impossible", sourceId);
            }
        }
    }

    private static final Logger LOG = Logger.getLogger(StockActualites.class);

    // Clé du verrou consultatif d'une source : espace de clés propre à la collecte web, identifiant lié.
    private static final String VERROUILLER = "SELECT pg_try_advisory_lock(hashtext('collecte_web'), hashtext(?))";
    private static final String DEVERROUILLER = "SELECT pg_advisory_unlock(hashtext('collecte_web'), hashtext(?))";

    // Requêtes par périmètre : l'index unique partiel visé dépend du périmètre de la source.
    private static final String INSERER_ACTUALITE = """
            INSERT INTO actualite (id, espace_id, proprietaire_id, empreinte, type, titre, lien, texte, publie_le, recu_le)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """;
    private static final String CONFLIT_ESPACE = "ON CONFLICT (espace_id, empreinte) WHERE espace_id IS NOT NULL DO NOTHING";
    private static final String CONFLIT_PROPRIETAIRE =
            "ON CONFLICT (proprietaire_id, empreinte) WHERE proprietaire_id IS NOT NULL DO NOTHING";
    private static final String ACTUALITE_ESPACE = "SELECT id FROM actualite WHERE espace_id = ? AND empreinte = ?";
    private static final String ACTUALITE_PROPRIETAIRE = "SELECT id FROM actualite WHERE proprietaire_id = ? AND empreinte = ?";

    @Inject
    DataSource dataSource;

    @Inject
    Event<ActualiteRecue> evenements;

    @Transactional
    public Optional<SourceACollecter> source(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, type, adresse, libelle, espace_id, proprietaire_id, page_empreinte, page_texte FROM source_web WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SourceACollecter(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class), rs.getString(7),
                        rs.getString(8)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de la source " + id + " impossible", e);
        }
    }

    /**
     * Prend sans attendre le verrou de collecte d'une source. La connexion qui le porte est obtenue
     * hors de toute transaction JTA et reste ouverte jusqu'à {@link Verrou#close()}.
     *
     * @return le verrou, ou vide si une autre collecte de cette source est en cours
     */
    @Transactional(TxType.NOT_SUPPORTED)
    public Optional<Verrou> verrouiller(UUID sourceId) {
        Connection c = null;
        try {
            c = dataSource.getConnection();
            c.setAutoCommit(true);
            try (PreparedStatement ps = c.prepareStatement(VERROUILLER)) {
                ps.setString(1, sourceId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (!rs.getBoolean(1)) {
                        return Optional.empty();
                    }
                }
            }
            Verrou verrou = new Verrou(c, sourceId);
            c = null;
            return Optional.of(verrou);
        } catch (SQLException e) {
            throw new IllegalStateException("verrouillage de la source " + sourceId + " impossible", e);
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException e) {
                    // Échec de fermeture d'une connexion sans verrou : rien à libérer, seule la trace compte.
                    LOG.debugf(e, "fermeture de la connexion de verrouillage de la source %s impossible", sourceId);
                }
            }
        }
    }

    /** Vrai si la prochaine collecte de la source est échue. */
    @Transactional
    public boolean echue(UUID sourceId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM source_web WHERE id = ? AND prochaine_collecte_le <= now()")) {
            ps.setObject(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de la source " + sourceId + " impossible", e);
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
    public int integrerFlux(SourceACollecter source, List<ElementFlux> elements) {
        int nouvelles = 0;
        try (Connection c = dataSource.getConnection()) {
            for (ElementFlux e : elements) {
                UUID actualite = actualite(c, source, Deduplication.article(e), "ARTICLE", e.titre(), e.lien(),
                        e.texte(), e.publieLe());
                if (citer(c, actualite, source.id(), e.lien())) {
                    nouvelles++;
                    evenements.fire(new ActualiteRecue(actualite, source.id(), source.espaceId()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement des actualités de la source " + source.id() + " impossible", e);
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
                UUID actualite = actualite(c, source, Deduplication.changementPage(source.adresse(), instantane.empreinte()),
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

    /**
     * Actualité du périmètre de la source pour cette empreinte, créée si elle n'existe pas. Le contenu
     * d'une actualité existante n'est jamais modifié ; seules les actualités de ce périmètre sont lues.
     */
    private static UUID actualite(Connection c, SourceACollecter source, String empreinte, String type, String titre,
            String lien, String texte, Instant publieLe) throws SQLException {
        boolean espace = source.espaceId() != null;
        UUID perimetre = espace ? source.espaceId() : source.proprietaireId();
        try (PreparedStatement ps = c.prepareStatement(
                INSERER_ACTUALITE + (espace ? CONFLIT_ESPACE : CONFLIT_PROPRIETAIRE))) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, espace ? perimetre : null);
            ps.setObject(3, espace ? null : perimetre);
            ps.setString(4, empreinte);
            ps.setString(5, type);
            ps.setString(6, couper(titre, 1000));
            ps.setString(7, lien);
            ps.setString(8, texte == null ? "" : texte);
            ps.setObject(9, publieLe == null ? null : publieLe.atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(espace ? ACTUALITE_ESPACE : ACTUALITE_PROPRIETAIRE)) {
            ps.setObject(1, perimetre);
            ps.setString(2, empreinte);
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
