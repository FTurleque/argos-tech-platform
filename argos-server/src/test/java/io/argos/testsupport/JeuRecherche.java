package io.argos.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Données de test de la recherche, écrites sous l'utilisateur de connexion (hors RLS) comme le font
 * la collecte et la corrélation : signaux, projets, impacts, sources web, actualités et
 * rapprochements. Aucune requête réseau.
 */
@ApplicationScoped
public class JeuRecherche {

    @Inject
    DataSource dataSource;

    /** Signal OSV publié à la date donnée, affectant le paquet donné (PURL sans version, facultatif). */
    public UUID signal(String identifiant, String titre, String resume, String gravite, Instant publieLe, String paquet) {
        UUID id = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        String empreinte = String.format("%064x", Math.abs((long) id.hashCode()));
        transaction(connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("SET CONSTRAINTS ALL DEFERRED")) {
                requete.execute();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                                        collected_at, updated_at, content_hash, affected, current_version_id)
                    VALUES (?, 'OSV', ?, ?, ?, ?, ?, ?, now(), now(), ?, '[]', ?)
                    """)) {
                requete.setObject(1, id);
                requete.setString(2, identifiant);
                requete.setString(3, titre);
                requete.setString(4, resume);
                requete.setString(5, gravite);
                requete.setTimestamp(6, Timestamp.from(publieLe));
                requete.setTimestamp(7, Timestamp.from(publieLe));
                requete.setString(8, empreinte);
                requete.setObject(9, version);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
                    VALUES (?, ?, 1, ?, ?, now(), '{}')
                    """)) {
                requete.setObject(1, version);
                requete.setObject(2, id);
                requete.setString(3, empreinte);
                requete.setTimestamp(4, Timestamp.from(publieLe));
                requete.executeUpdate();
            }
            if (paquet != null) {
                try (PreparedStatement requete = connexion.prepareStatement(
                        "INSERT INTO signal_package (signal_id, package_key, ecosystem) VALUES (?, ?, ?)")) {
                    requete.setObject(1, id);
                    requete.setString(2, paquet);
                    requete.setString(3, paquet.startsWith("pkg:maven/") ? "Maven" : paquet.startsWith("pkg:npm/") ? "npm" : null);
                    requete.executeUpdate();
                }
            }
        });
        return id;
    }

    /** Projet d'un espace, avec un inventaire d'un composant. */
    public UUID projet(UUID espace, String nom) {
        UUID id = UUID.randomUUID();
        transaction(connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("INSERT INTO project (id, workspace_id, name) VALUES (?, ?, ?)")) {
                requete.setObject(1, id);
                requete.setObject(2, espace);
                requete.setString(3, nom);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT INTO inventory VALUES (?, 'SBOM', now(), '1.6', 'test', gen_random_uuid(), now())")) {
                requete.setObject(1, id);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO component (id, project_id, package_key, purl, version, import_id)
                    VALUES (gen_random_uuid(), ?, 'pkg:maven/org.test/composant', 'pkg:maven/org.test/composant@1', '1', gen_random_uuid())
                    """)) {
                requete.setObject(1, id);
                requete.executeUpdate();
            }
        });
        return id;
    }

    /** Impact d'un signal sur le composant du projet. */
    public void impact(UUID signal, UUID projet) {
        transaction(connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                                                level, method, inventory_origin, provenance, created_at, updated_at)
                    SELECT gen_random_uuid(), s.id, s.current_version_id, p.id, p.workspace_id, c.id, 'EXACT', 'PURL_EXACT',
                           'SBOM', '{"test":true}', now(), now()
                    FROM signal s, project p JOIN component c ON c.project_id = p.id
                    WHERE s.id = ? AND p.id = ?
                    """)) {
                requete.setObject(1, signal);
                requete.setObject(2, projet);
                requete.executeUpdate();
            }
        });
    }

    /** Source de flux d'un espace. */
    public UUID sourceEspace(UUID espace, UUID creePar) {
        return source("ESPACE", espace, null, creePar);
    }

    /** Source de flux personnelle d'un compte. */
    public UUID sourcePersonnelle(UUID compte) {
        return source("PERSONNELLE", null, compte, compte);
    }

    private UUID source(String portee, UUID espace, UUID proprietaire, UUID creePar) {
        UUID id = UUID.randomUUID();
        transaction(connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO source_web (id, portee, espace_id, proprietaire_id, type, adresse, libelle, cree_par)
                    VALUES (?, ?, ?, ?, 'FLUX', ?, 'Flux de test', ?)
                    """)) {
                requete.setObject(1, id);
                requete.setString(2, portee);
                requete.setObject(3, espace);
                requete.setObject(4, proprietaire);
                requete.setString(5, "https://flux.test/" + id);
                requete.setObject(6, creePar);
                requete.executeUpdate();
            }
        });
        return id;
    }

    /** Actualité reçue par une source (périmètre de la source), citée par elle. */
    public UUID actualite(UUID source, String titre, String texte, String lien, Instant publieLe) {
        UUID id = UUID.randomUUID();
        transaction(connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO actualite (id, espace_id, proprietaire_id, empreinte, type, titre, lien, texte, publie_le, recu_le)
                    SELECT ?, s.espace_id, s.proprietaire_id, md5(?::text), 'ARTICLE', ?, ?, ?, ?, now()
                    FROM source_web s WHERE s.id = ?
                    """)) {
                requete.setObject(1, id);
                requete.setString(2, id.toString());
                requete.setString(3, titre);
                requete.setString(4, lien);
                requete.setString(5, texte);
                requete.setTimestamp(6, Timestamp.from(publieLe));
                requete.setObject(7, source);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT INTO actualite_source (actualite_id, source_id, lien, recu_le) VALUES (?, ?, ?, now())")) {
                requete.setObject(1, id);
                requete.setObject(2, source);
                requete.setString(3, lien);
                requete.executeUpdate();
            }
        });
        return id;
    }

    /** Rapprochement « à vérifier » d'une actualité d'espace avec un projet du même espace. */
    public void rapprochement(UUID actualite, UUID projet, String technologie) {
        transaction(connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO actualite_impact (id, actualite_id, project_id, workspace_id, technologie, niveau, methode, cree_le)
                    SELECT gen_random_uuid(), a.id, p.id, p.workspace_id, ?, 'A_VERIFIER', 'NOM_TECHNOLOGIE', now()
                    FROM actualite a, project p WHERE a.id = ? AND p.id = ?
                    """)) {
                requete.setString(1, technologie);
                requete.setObject(2, actualite);
                requete.setObject(3, projet);
                requete.executeUpdate();
            }
        });
    }

    @FunctionalInterface
    interface Travail {
        void executer(Connection connexion) throws SQLException;
    }

    private void transaction(Travail travail) {
        try (Connection connexion = dataSource.getConnection()) {
            boolean auto = connexion.getAutoCommit();
            connexion.setAutoCommit(false);
            try {
                travail.executer(connexion);
                connexion.commit();
            } catch (SQLException | RuntimeException erreur) {
                connexion.rollback();
                throw erreur;
            } finally {
                connexion.setAutoCommit(auto);
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("jeu de recherche impossible à écrire", erreur);
        }
    }
}
