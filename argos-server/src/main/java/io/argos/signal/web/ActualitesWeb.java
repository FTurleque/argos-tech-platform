package io.argos.signal.web;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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
 * compte de l'appelant : une actualité n'est visible que dans son périmètre (son propriétaire, ou
 * les membres de son espace) et par une source visible ; seules les sources visibles sont citées.
 *
 * <p>Un article reçu dans plusieurs périmètres visibles de l'appelant (son périmètre personnel et
 * ses espaces) y a une ligne par périmètre, de même empreinte ; il est restitué une fois, avec les
 * citations de toutes ces lignes. La ligne restituée est celle d'un espace de préférence, puis la
 * plus ancienne reçue.
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
        int limiteBornee = Math.min(Math.max(limite, 1), 200);
        return base.executer(new Contexte(compteId, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT r.id, r.type, r.titre, r.lien, r.publie_le, r.recu_le, r.texte, r.empreinte FROM (
                        SELECT DISTINCT ON (a.empreinte) a.id, a.type, a.titre, a.lien, a.publie_le, a.recu_le, a.texte,
                               a.empreinte
                        FROM actualite a WHERE (?::uuid IS NULL OR a.espace_id = ?::uuid)
                        ORDER BY a.empreinte, (a.espace_id IS NULL), a.recu_le, a.id) r
                    ORDER BY coalesce(r.publie_le, r.recu_le) DESC, r.id LIMIT ?
                    """)) {
                requete.setObject(1, espaceId, Types.OTHER);
                requete.setObject(2, espaceId, Types.OTHER);
                requete.setInt(3, limiteBornee);
                return completer(connexion, lire(requete));
            }
        });
    }

    public Optional<Detail> detail(UUID id, UUID compteId) {
        return base.executer(new Contexte(compteId, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT a.id, a.type, a.titre, a.lien, a.publie_le, a.recu_le, a.texte, a.empreinte FROM actualite a WHERE a.id = ?")) {
                requete.setObject(1, id);
                return completer(connexion, lire(requete)).stream().findFirst()
                        .map(actualite -> new Detail(actualite,
                                new Resume(METHODE_RESUME, ContenuExterne.de(extrait(actualite.contenu().texte())))));
            }
        });
    }

    /** Premières phrases du texte, au plus {@value #RESUME_MAX} caractères. */
    static String extrait(String texte) {
        if (texte == null || texte.isBlank()) {
            return "";
        }
        String texteNettoye = texte.strip();
        if (texteNettoye.length() <= RESUME_MAX) {
            return texteNettoye;
        }
        int coupe = texteNettoye.lastIndexOf(". ", RESUME_MAX);
        return coupe > RESUME_MAX / 2 ? texteNettoye.substring(0, coupe + 1) : texteNettoye.substring(0, RESUME_MAX) + "…";
    }

    /** Actualité lue et son empreinte, qui regroupe les citations de tous les périmètres visibles. */
    private record Lue(Actualite actualite, String empreinte) {
    }

    private static List<Lue> lire(PreparedStatement requete) throws SQLException {
        List<Lue> actualites = new ArrayList<>();
        try (ResultSet resultat = requete.executeQuery()) {
            while (resultat.next()) {
                actualites.add(new Lue(new Actualite(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                        resultat.getString(4), instant(resultat, 5), instant(resultat, 6), ContenuExterne.de(resultat.getString(7)),
                        new ArrayList<>()), resultat.getString(8)));
            }
        }
        return actualites;
    }

    /** Ajoute les citations visibles de toutes les actualités visibles de même empreinte. */
    private static List<Actualite> completer(Connection connexion, List<Lue> lues) throws SQLException {
        if (lues.isEmpty()) {
            return List.of();
        }
        Map<String, List<Citation>> parEmpreinte = new LinkedHashMap<>();
        lues.forEach(lue -> parEmpreinte.put(lue.empreinte(), new ArrayList<>()));
        Array empreintes = connexion.createArrayOf("bpchar", parEmpreinte.keySet().toArray());
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT a.empreinte, w.id, w.libelle, w.portee, x.lien FROM actualite_source x
                JOIN actualite a ON a.id = x.actualite_id
                JOIN source_web w ON w.id = x.source_id
                WHERE a.empreinte = ANY (?) ORDER BY x.recu_le, w.id
                """)) {
            requete.setArray(1, empreintes);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    parEmpreinte.get(resultat.getString(1)).add(new Citation(resultat.getObject(2, UUID.class), resultat.getString(3),
                            resultat.getString(4), resultat.getString(5)));
                }
            }
        }
        return lues.stream().map(lue -> {
            Actualite actualite = lue.actualite();
            return new Actualite(actualite.id(), actualite.type(), actualite.titre(), actualite.lien(), actualite.publieLe(),
                    actualite.recuLe(), actualite.contenu(), List.copyOf(parEmpreinte.get(lue.empreinte())));
        }).toList();
    }

    private static Instant instant(ResultSet resultat, int colonne) throws SQLException {
        OffsetDateTime valeur = resultat.getObject(colonne, OffsetDateTime.class);
        return valeur == null ? null : valeur.toInstant();
    }
}
