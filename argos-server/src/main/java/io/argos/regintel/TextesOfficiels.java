package io.argos.regintel;

import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.ingestion.legi.TexteJorf;

/**
 * Textes officiels collectés (table {@code texte_officiel}, données publiques sans espace), sous
 * l'utilisateur de connexion. Un texte est enregistré une seule fois par identifiant officiel, même si
 * la collecte d'un jour est rejouée (REG-01-S1) : {@code INSERT … ON CONFLICT DO NOTHING}.
 */
@ApplicationScoped
public class TextesOfficiels {

    static final String COLONNES = """
            id, identifiant, nature, numero, nor, ministere, titre, date_publication, date_signature, date_effet,
            motif_date_effet, codes_ids, codes_libelles, articles_modifies, extrait""";

    @Inject
    DataSource dataSource;

    /** Enregistre le texte s'il est inconnu ; renvoie son identifiant interne s'il vient d'être créé. */
    public Optional<UUID> enregistrer(TexteJorf t, Instant maintenant) {
        Optional<DatesTexte.DateEffet> effet = DatesTexte.dateEffet(t.datePublication(), t.contenu());
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO texte_officiel (id, identifiant, source, nature, numero, nor, ministere, titre, date_publication,
                    date_signature, date_effet, motif_date_effet, codes_ids, codes_libelles, articles_modifies, extrait,
                    archive, collecte_le)
                VALUES (?, ?, 'JORF', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (identifiant) DO NOTHING
                """)) {
            ps.setObject(1, id);
            ps.setString(2, t.identifiant());
            ps.setString(3, borne(t.nature(), 64));
            ps.setString(4, borne(t.numero(), 64));
            ps.setString(5, borne(t.nor(), 16));
            ps.setString(6, borne(t.ministere(), 500));
            ps.setString(7, borne(t.titre(), 2000));
            ps.setDate(8, Date.valueOf(t.datePublication()));
            ps.setDate(9, t.dateSignature() == null ? null : Date.valueOf(t.dateSignature()));
            ps.setDate(10, effet.map(e -> Date.valueOf(e.date())).orElse(null));
            ps.setString(11, effet.map(DatesTexte.DateEffet::motif).orElse(null));
            ps.setArray(12, c.createArrayOf("varchar", t.codesModifies().stream().map(TexteJorf.CodeModifie::identifiant).toArray()));
            ps.setArray(13, c.createArrayOf("varchar", t.codesModifies().stream().map(m -> borne(m.libelle(), 300)).toArray()));
            ps.setArray(14, c.createArrayOf("varchar", t.articlesModifies().stream().limit(200).map(a -> borne(a, 300)).toArray()));
            ps.setString(15, t.contenu() == null ? "" : borne(t.contenu(), 20000));
            ps.setString(16, borne(t.archive(), 200));
            ps.setTimestamp(17, Timestamp.from(maintenant));
            return ps.executeUpdate() == 1 ? Optional.of(id) : Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement du texte " + t.identifiant() + " impossible", e);
        }
    }

    /** Textes désignés, dans l'ordre de publication. */
    public List<TexteOfficiel> lire(Collection<UUID> ids) {
        List<TexteOfficiel> liste = new ArrayList<>();
        if (ids.isEmpty()) {
            return liste;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLONNES + " FROM texte_officiel WHERE id = ANY (?) ORDER BY date_publication, identifiant")) {
            ps.setArray(1, c.createArrayOf("uuid", ids.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    liste.add(texte(rs, 1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des textes officiels impossible", e);
        }
        return liste;
    }

    /** Texte connu par son identifiant officiel. */
    public Optional<TexteOfficiel> parIdentifiant(String identifiant) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLONNES + " FROM texte_officiel WHERE identifiant = ?")) {
            ps.setString(1, identifiant);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(texte(rs, 1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du texte officiel impossible", e);
        }
    }

    /** Lit un texte à partir de la colonne {@code debut} (ordre de {@link #COLONNES}). */
    static TexteOfficiel texte(ResultSet rs, int debut) throws SQLException {
        int i = debut;
        UUID id = rs.getObject(i++, UUID.class);
        String identifiant = rs.getString(i++);
        String nature = rs.getString(i++);
        String numero = rs.getString(i++);
        String nor = rs.getString(i++);
        String ministere = rs.getString(i++);
        String titre = rs.getString(i++);
        LocalDate publication = rs.getObject(i++, LocalDate.class);
        LocalDate signature = rs.getObject(i++, LocalDate.class);
        LocalDate effet = rs.getObject(i++, LocalDate.class);
        String motif = rs.getString(i++);
        List<String> codesIds = tableau(rs.getArray(i++));
        List<String> codesLibelles = tableau(rs.getArray(i++));
        List<String> articles = tableau(rs.getArray(i++));
        String extrait = rs.getString(i);
        List<TexteOfficiel.Code> codes = new ArrayList<>();
        for (int k = 0; k < codesIds.size() && k < codesLibelles.size(); k++) {
            codes.add(new TexteOfficiel.Code(codesIds.get(k), codesLibelles.get(k)));
        }
        return new TexteOfficiel(id, identifiant, nature, numero, nor, ministere, titre, publication, signature, effet, motif,
                codes, articles, extrait);
    }

    static List<String> tableau(Array a) throws SQLException {
        return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
    }

    private static String borne(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
