package io.argos.signal.web;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;

/**
 * Restitution des actualités web (WEB-01-S1, WEB-04, WEB-05) sous le rôle {@code argos_app} avec le
 * compte de l'appelant : une actualité n'est visible que par une source visible, et seules les
 * sources visibles sont citées. Un article reçu par plusieurs sources est restitué une fois.
 *
 * <p>Le contenu collecté est une donnée externe non fiable (R-11) : il est rendu comme citation,
 * jamais comme consigne ; le résumé est un extrait déterministe, sans interprétation.
 */
@ApplicationScoped
public class ActualitesWeb {

    /** Source qui a cité l'actualité. */
    public record Citation(UUID sourceId, String libelle, String portee, String lien) {
    }

    /** Contenu externe cité tel quel, marqué non fiable (WEB-04). */
    public record ContenuExterne(String texte, String nature, boolean fiable) {

        static ContenuExterne de(String texte) {
            return new ContenuExterne(texte, NATURE, false);
        }
    }

    /** Résumé déterministe : extrait du contenu externe, jamais une instruction exécutée. */
    public record Resume(String methode, ContenuExterne extrait) {
    }

    /** Actualité restituée une seule fois, avec toutes ses sources visibles. */
    public record Actualite(UUID id, String type, String titre, String lien, Instant publieLe, Instant recuLe,
            ContenuExterne contenu, List<Citation> sources) {
    }

    /** Détail d'une actualité et son résumé. */
    public record Detail(Actualite actualite, Resume resume) {
    }

    /** Nature du contenu collecté : citation externe, non fiable. */
    public static final String NATURE = "CITATION_EXTERNE";
    public static final String METHODE_RESUME = "EXTRAIT_V1";
    static final int RESUME_MAX = 280;

    @Inject
    ContexteBase base;

    /** Actualités visibles du compte, les plus récentes d'abord ; filtrées sur un espace s'il est donné. */
    public List<Actualite> lister(UUID compteId, UUID espaceId, int limite) {
        int l = Math.min(Math.max(limite, 1), 200);
        return base.executer(new Contexte(compteId, null, null), c -> {
            String filtre = espaceId == null ? "" : """
                     AND EXISTS (SELECT 1 FROM actualite_source x JOIN source_web w ON w.id = x.source_id
                                 WHERE x.actualite_id = a.id AND w.espace_id = ?)""";
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT a.id, a.type, a.titre, a.lien, a.publie_le, a.recu_le, a.texte FROM actualite a WHERE true"
                            + filtre + " ORDER BY coalesce(a.publie_le, a.recu_le) DESC, a.id LIMIT ?")) {
                int i = 1;
                if (espaceId != null) {
                    ps.setObject(i++, espaceId);
                }
                ps.setInt(i, l);
                return completer(c, lire(ps));
            }
        });
    }

    public Optional<Detail> detail(UUID id, UUID compteId) {
        return base.executer(new Contexte(compteId, null, null), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT a.id, a.type, a.titre, a.lien, a.publie_le, a.recu_le, a.texte FROM actualite a WHERE a.id = ?")) {
                ps.setObject(1, id);
                return completer(c, lire(ps)).stream().findFirst()
                        .map(a -> new Detail(a, new Resume(METHODE_RESUME, ContenuExterne.de(extrait(a.contenu().texte())))));
            }
        });
    }

    /** Premières phrases du texte, au plus {@value #RESUME_MAX} caractères. */
    static String extrait(String texte) {
        if (texte == null || texte.isBlank()) {
            return "";
        }
        String t = texte.strip();
        if (t.length() <= RESUME_MAX) {
            return t;
        }
        int coupe = t.lastIndexOf(". ", RESUME_MAX);
        return coupe > RESUME_MAX / 2 ? t.substring(0, coupe + 1) : t.substring(0, RESUME_MAX) + "…";
    }

    private static List<Actualite> lire(PreparedStatement ps) throws SQLException {
        List<Actualite> actualites = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                actualites.add(new Actualite(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), instant(rs, 5), instant(rs, 6), ContenuExterne.de(rs.getString(7)),
                        new ArrayList<>()));
            }
        }
        return actualites;
    }

    private static List<Actualite> completer(Connection c, List<Actualite> actualites) throws SQLException {
        if (actualites.isEmpty()) {
            return actualites;
        }
        Map<UUID, Actualite> parId = new LinkedHashMap<>();
        actualites.forEach(a -> parId.put(a.id(), a));
        Array ids = c.createArrayOf("uuid", parId.keySet().toArray());
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT x.actualite_id, w.id, w.libelle, w.portee, x.lien FROM actualite_source x
                JOIN source_web w ON w.id = x.source_id
                WHERE x.actualite_id = ANY (?) ORDER BY x.recu_le, w.id
                """)) {
            ps.setArray(1, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    parId.get(rs.getObject(1, UUID.class)).sources().add(new Citation(rs.getObject(2, UUID.class),
                            rs.getString(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return actualites.stream().map(a -> new Actualite(a.id(), a.type(), a.titre(), a.lien(), a.publieLe(),
                a.recuLe(), a.contenu(), List.copyOf(a.sources()))).toList();
    }

    private static Instant instant(ResultSet rs, int colonne) throws SQLException {
        OffsetDateTime v = rs.getObject(colonne, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }
}
