package io.argos.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.testsupport.StubServer.Stub;

/**
 * Données de test de la veille réglementaire : archives JORF composées à partir des fichiers enregistrés
 * ({@code fixtures/jorf}, format DILA, textes fictifs) et servies par {@link StubServer}, et insertions
 * techniques (hors RLS) de textes, domaines, experts et fiches. Aucune requête vers la DILA.
 */
@ApplicationScoped
public class ReglementaireFixtures {

    /** Chemin de l'index des archives sur le serveur simulé. */
    public static final String INDEX = "/jorf/";

    @Inject
    DataSource dataSource;

    /** Contenu d'un fichier enregistré de {@code fixtures/jorf}. */
    public static String fichier(String nom) {
        try (InputStream in = ReglementaireFixtures.class.getResourceAsStream("/fixtures/jorf/" + nom)) {
            if (in == null) {
                throw new IllegalArgumentException("fixture absente : " + nom);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Archive {@code .tar.gz} (format ustar) contenant les fichiers donnés (chemin → contenu). */
    public static byte[] archive(Map<String, String> fichiers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            for (Map.Entry<String, String> f : fichiers.entrySet()) {
                byte[] contenu = f.getValue().getBytes(StandardCharsets.UTF_8);
                gz.write(entete(f.getKey(), contenu.length));
                gz.write(contenu);
                gz.write(new byte[(512 - contenu.length % 512) % 512]);
            }
            gz.write(new byte[1024]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] entete(String nom, long taille) {
        byte[] h = new byte[512];
        byte[] n = nom.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(n, 0, h, 0, Math.min(100, n.length));
        ecrire(h, 100, String.format("%07o", 0644));
        ecrire(h, 108, String.format("%07o", 0));
        ecrire(h, 116, String.format("%07o", 0));
        ecrire(h, 124, String.format("%011o", taille));
        ecrire(h, 136, String.format("%011o", 0));
        h[156] = '0';
        ecrire(h, 257, "ustar");
        ecrire(h, 263, "00");
        for (int i = 148; i < 156; i++) {
            h[i] = ' ';
        }
        long somme = 0;
        for (byte b : h) {
            somme += b & 0xff;
        }
        ecrire(h, 148, String.format("%06o", somme));
        h[154] = 0;
        return h;
    }

    private static void ecrire(byte[] h, int debut, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, h, debut, b.length);
    }

    /** Programme l'index (tiré de {@code index.html}) et les archives des 19, 20 et 21 novembre 2026. */
    public static void servirArchives(Map<String, String> du20, Map<String, String> du21) {
        StubServer.on(INDEX, Stub.ok(fichier("index.html")));
        StubServer.on("/jorf/JORF_20261119-213012.tar.gz", Stub.octets(archive(Map.of())));
        StubServer.on("/jorf/JORF_20261120-213004.tar.gz", Stub.octets(archive(du20)));
        StubServer.on("/jorf/JORF_20261121-213010.tar.gz", Stub.octets(archive(du21)));
    }

    /** Archive du 20 novembre 2026 : décret « carrière » (avec son article) et arrêté « paie ». */
    public static Map<String, String> archiveDu20() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("20261120-213004/jorf/global/texte/version/JORF/TEXT/00/00/99/00/00/JORFTEXT000099000001.xml",
                fichier("texte-carriere.xml"));
        m.put("20261120-213004/jorf/global/article/JORF/ARTI/00/00/99/00/00/JORFARTI000099000011.xml",
                fichier("article-carriere.xml"));
        m.put("20261120-213004/jorf/global/texte/version/JORF/TEXT/00/00/99/00/00/JORFTEXT000099000002.xml",
                fichier("texte-paie.xml"));
        m.put("20261120-213004/jorf/global/texte/version/JORF/TEXT/00/00/99/00/00/JORFTEXT000099000009.xml",
                fichier("xxe.xml"));
        return m;
    }

    /** Archive du 21 novembre 2026 : décision sans rapport avec les domaines de test. */
    public static Map<String, String> archiveDu21() {
        return Map.of("20261121-213010/jorf/global/texte/version/JORF/TEXT/00/00/99/00/00/JORFTEXT000099000003.xml",
                fichier("texte-autre.xml"));
    }

    // ------------------------------------------------------------------ insertions techniques

    /** Texte officiel enregistré directement (hors collecte). */
    public UUID texte(String identifiant, String titre, LocalDate publication, LocalDate effet) {
        UUID id = UUID.randomUUID();
        executer("""
                INSERT INTO texte_officiel (id, identifiant, source, nature, nor, titre, date_publication, date_effet,
                    motif_date_effet, archive, collecte_le)
                VALUES (?, ?, 'JORF', 'DECRET', 'TEST2600001D', ?, ?, ?, ?, 'test', ?)
                """, ps -> {
            ps.setObject(1, id);
            ps.setString(2, identifiant);
            ps.setString(3, titre);
            ps.setDate(4, Date.valueOf(publication));
            ps.setDate(5, effet == null ? null : Date.valueOf(effet));
            ps.setString(6, effet == null ? null : "test");
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
        });
        return id;
    }

    /** Domaine métier d'un espace. */
    public void domaine(UUID espace, String code, String libelle, UUID auteur) {
        executer("INSERT INTO domaine_metier (espace_id, code, libelle, modifie_par, modifie_le) VALUES (?, ?, ?, ?, now())",
                ps -> {
                    ps.setObject(1, espace);
                    ps.setString(2, code);
                    ps.setString(3, libelle);
                    ps.setObject(4, auteur);
                });
    }

    /** Expert désigné d'un domaine. */
    public void expert(UUID espace, String domaine, UUID compte) {
        executer("""
                INSERT INTO expert_domaine (espace_id, domaine, compte_id, modifie_par, modifie_le) VALUES (?, ?, ?, ?, now())
                """, ps -> {
            ps.setObject(1, espace);
            ps.setString(2, domaine);
            ps.setObject(3, compte);
            ps.setObject(4, compte);
        });
    }

    /** Fiche d'un texte pour un espace, dans l'état de validation donné, rattachée au domaine par une règle. */
    public UUID fiche(UUID texte, UUID espace, String domaine, String etatValidation, String resume, UUID decidePar) {
        UUID id = UUID.randomUUID();
        boolean decidee = !"EN_ATTENTE_VALIDATION".equals(etatValidation);
        executer("""
                INSERT INTO fiche_reglementaire (id, texte_id, espace_id, etat_validation, etat_suivi, resume, resume_origine,
                    date_effet, date_effet_origine, decide_par, decide_le, cree_le, modifie_le)
                SELECT ?, t.id, ?, ?, ?, ?, 'EXPERT', t.date_effet, CASE WHEN t.date_effet IS NULL THEN NULL ELSE 'TEXTE' END,
                       ?, ?, now(), now()
                FROM texte_officiel t WHERE t.id = ?
                """, ps -> {
            ps.setObject(1, id);
            ps.setObject(2, espace);
            ps.setString(3, etatValidation);
            ps.setString(4, "VALIDEE".equals(etatValidation) ? "EN_ANALYSE" : "NOUVEAU");
            ps.setString(5, resume);
            ps.setObject(6, decidee ? decidePar : null);
            ps.setTimestamp(7, decidee ? Timestamp.from(Instant.now()) : null);
            ps.setObject(8, texte);
        });
        executer("INSERT INTO fiche_domaine (fiche_id, espace_id, domaine, origine, motif) VALUES (?, ?, ?, 'REGLE', 'test')",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, espace);
                    ps.setString(3, domaine);
                });
        return id;
    }

    /** Domaine supplémentaire rattaché à une fiche existante par une règle. */
    public void rattacher(UUID fiche, UUID espace, String domaine) {
        executer("INSERT INTO fiche_domaine (fiche_id, espace_id, domaine, origine, motif) VALUES (?, ?, ?, 'REGLE', 'test')",
                ps -> {
                    ps.setObject(1, fiche);
                    ps.setObject(2, espace);
                    ps.setString(3, domaine);
                });
    }

    @FunctionalInterface
    interface Parametrage {
        void appliquer(PreparedStatement ps) throws SQLException;
    }

    private void executer(String sql, Parametrage p) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            p.appliquer(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }
}
