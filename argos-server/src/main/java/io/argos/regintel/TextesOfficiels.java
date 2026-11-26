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
    public Optional<UUID> enregistrer(TexteJorf texte, Instant maintenant) {
        Optional<DatesTexte.DateEffet> effet = DatesTexte.dateEffet(texte.datePublication(), texte.contenu());
        UUID id = UUID.randomUUID();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO texte_officiel (id, identifiant, source, nature, numero, nor, ministere, titre, date_publication,
                    date_signature, date_effet, motif_date_effet, codes_ids, codes_libelles, articles_modifies, extrait,
                    archive, collecte_le)
                VALUES (?, ?, 'JORF', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (identifiant) DO NOTHING
                """)) {
            requete.setObject(1, id);
            requete.setString(2, texte.identifiant());
            requete.setString(3, borne(texte.nature(), 64));
            requete.setString(4, borne(texte.numero(), 64));
            requete.setString(5, borne(texte.nor(), 16));
            requete.setString(6, borne(texte.ministere(), 500));
            requete.setString(7, borne(texte.titre(), 2000));
            requete.setDate(8, Date.valueOf(texte.datePublication()));
            requete.setDate(9, texte.dateSignature() == null ? null : Date.valueOf(texte.dateSignature()));
            requete.setDate(10, effet.map(dateEffet -> Date.valueOf(dateEffet.date())).orElse(null));
            requete.setString(11, effet.map(DatesTexte.DateEffet::motif).orElse(null));
            requete.setArray(12, connexion.createArrayOf("varchar",
                    texte.codesModifies().stream().map(TexteJorf.CodeModifie::identifiant).toArray()));
            requete.setArray(13, connexion.createArrayOf("varchar",
                    texte.codesModifies().stream().map(code -> borne(code.libelle(), 300)).toArray()));
            requete.setArray(14, connexion.createArrayOf("varchar",
                    texte.articlesModifies().stream().limit(200).map(article -> borne(article, 300)).toArray()));
            requete.setString(15, texte.contenu() == null ? "" : borne(texte.contenu(), 20000));
            requete.setString(16, borne(texte.archive(), 200));
            requete.setTimestamp(17, Timestamp.from(maintenant));
            return requete.executeUpdate() == 1 ? Optional.of(id) : Optional.empty();
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement du texte " + texte.identifiant() + " impossible", erreur);
        }
    }

    /** Textes désignés, dans l'ordre de publication. */
    public List<TexteOfficiel> lire(Collection<UUID> ids) {
        List<TexteOfficiel> liste = new ArrayList<>();
        if (ids.isEmpty()) {
            return liste;
        }
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT " + COLONNES + " FROM texte_officiel WHERE id = ANY (?) ORDER BY date_publication, identifiant")) {
            requete.setArray(1, connexion.createArrayOf("uuid", ids.toArray()));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    liste.add(texte(resultat, 1));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des textes officiels impossible", erreur);
        }
        return liste;
    }

    /** Identifiants internes des textes enregistrés depuis les archives désignées (rejeu d'un jour). */
    public List<UUID> idsDesArchives(Collection<String> archives) {
        List<UUID> ids = new ArrayList<>();
        if (archives.isEmpty()) {
            return ids;
        }
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT id FROM texte_officiel WHERE archive = ANY (?) ORDER BY date_publication, identifiant")) {
            requete.setArray(1, connexion.createArrayOf("varchar", archives.toArray()));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    ids.add(resultat.getObject(1, UUID.class));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des textes officiels impossible", erreur);
        }
        return ids;
    }

    /** Lit un texte à partir de la colonne {@code debut} (ordre de {@link #COLONNES}). */
    static TexteOfficiel texte(ResultSet resultat, int debut) throws SQLException {
        int colonne = debut;
        UUID id = resultat.getObject(colonne++, UUID.class);
        String identifiant = resultat.getString(colonne++);
        String nature = resultat.getString(colonne++);
        String numero = resultat.getString(colonne++);
        String nor = resultat.getString(colonne++);
        String ministere = resultat.getString(colonne++);
        String titre = resultat.getString(colonne++);
        LocalDate publication = resultat.getObject(colonne++, LocalDate.class);
        LocalDate signature = resultat.getObject(colonne++, LocalDate.class);
        LocalDate effet = resultat.getObject(colonne++, LocalDate.class);
        String motif = resultat.getString(colonne++);
        List<String> codesIds = tableau(resultat.getArray(colonne++));
        List<String> codesLibelles = tableau(resultat.getArray(colonne++));
        List<String> articles = tableau(resultat.getArray(colonne++));
        String extrait = resultat.getString(colonne);
        List<TexteOfficiel.Code> codes = new ArrayList<>();
        for (int position = 0; position < codesIds.size() && position < codesLibelles.size(); position++) {
            codes.add(new TexteOfficiel.Code(codesIds.get(position), codesLibelles.get(position)));
        }
        return new TexteOfficiel(id, identifiant, nature, numero, nor, ministere, titre, publication, signature, effet, motif,
                codes, articles, extrait);
    }

    static List<String> tableau(Array tableauSql) throws SQLException {
        return tableauSql == null ? List.of() : Arrays.asList((String[]) tableauSql.getArray());
    }

    private static String borne(String texte, int max) {
        return texte == null || texte.length() <= max ? texte : texte.substring(0, max);
    }
}
