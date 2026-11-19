package io.argos.api.veille;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.signal.SignalQueries;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Contenu de « Ma veille » ({@code GET /api/v1/me/veille}) inchangé par la lecture groupée des signaux et par
 * l'index des impacts récents (changement {@code optimiser-ma-veille}, PRF-07-S2).
 *
 * <p>Le jeu de données, écrit hors RLS comme la collecte et la corrélation, réunit plusieurs avis aux dates
 * distinctes (dont un sans date de publication), plusieurs projets par avis, un impact écarté, un avis inconnu
 * de l'espace et un projet d'un espace dont l'appelant n'est pas membre. La réponse de l'API est comparée à un
 * résultat attendu calculé indépendamment, en base, et à la lecture unitaire {@link SignalQueries#find(UUID)}.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class VeilleLectureGroupeeTest {

    private static final int AVIS = 6;

    @Inject
    TestDb db;

    @Inject
    DataSource dataSource;

    @Inject
    SignalQueries signaux;

    private UUID espace;
    private UUID autreEspace;
    private final List<UUID> signauxIds = new ArrayList<>();

    private static UUID id(String graine) {
        return UUID.nameUUIDFromBytes(graine.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @BeforeEach
    void preparer() {
        db.reset();
        db.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = '" + Api.compte("jules") + "'");
        espace = Api.nouvelEspace("ines", "Lecture groupée");
        Api.ajouterMembre("ines", espace.toString(), "jules", "LECTEUR");
        autreEspace = Api.nouvelEspace("oscar", "Lecture groupée, autre espace");
        Api.en("jules").contentType(ContentType.JSON).body(Map.of("profilsActifs", List.of("developpeur")))
                .when().put("/api/v1/preferences/profils").then().statusCode(200);

        signauxIds.clear();
        projet("p1", espace);
        projet("p2", espace);
        projet("p3", espace);
        projet("etranger", autreEspace);
        for (int n = 1; n <= AVIS + 1; n++) {
            UUID s = id("lg-signal-" + n);
            UUID v = id("lg-version-" + n);
            // L'avis 3 n'a pas de date de publication : sa date restituée est celle de modification.
            String publie = n == 3 ? "NULL" : "now() - interval '" + (n * 3) + " days'";
            // Contrainte signal_current_version_fk différée : le signal et sa version dans une même transaction.
            db.exec("""
                    BEGIN;
                    INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                                        collected_at, updated_at, content_hash, affected, current_version_id)
                    VALUES ('%1$s', 'OSV', 'GHSA-LG-%3$d', 'Avis lecture groupée %3$d', 'Résumé', '%4$s', %5$s,
                            now() - interval '%6$d hours', now(), now(), md5('%3$d') || md5('lg%3$d'), '[]', '%2$s');
                    INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at,
                                                raw_content)
                    VALUES ('%2$s', '%1$s', 1, md5('%3$d') || md5('lg%3$d'), now(), now(), '{}');
                    COMMIT;
                    """.formatted(s, v, n, n % 2 == 0 ? "CRITICAL" : "HIGH", publie, n * 5));
            db.exec("INSERT INTO signal_alias (signal_id, alias) VALUES ('%s', 'CVE-2099-%04d')".formatted(s, n));
            signauxIds.add(s);
        }
        // Avis 1 à AVIS : un impact par projet de l'espace, à des instants distincts ; l'avis AVIS + 1 n'a aucun
        // impact dans l'espace de l'appelant (projet d'un autre espace seulement).
        int rang = 0;
        for (int n = 1; n <= AVIS; n++) {
            for (String p : List.of("p1", "p2", "p3")) {
                impact(n, p, espace, "OUVERT", rang++);
            }
            impact(n, "etranger", autreEspace, "OUVERT", rang++);
        }
        impact(AVIS + 1, "etranger", autreEspace, "CONFIRME", rang++);
        // Impact écarté : jamais restitué (COR-12-S2), même s'il est le plus récent.
        db.exec("UPDATE project_impact SET state = 'ECARTE', updated_at = now() + interval '1 hour' WHERE id = '"
                + id("lg-impact-2-p3") + "'");
    }

    private void projet(String nom, UUID ws) {
        UUID p = id("lg-projet-" + nom);
        UUID imp = id("lg-import-" + nom);
        db.exec("INSERT INTO project (id, workspace_id, name) VALUES ('%s', '%s', 'Projet %s')".formatted(p, ws, nom));
        db.exec("""
                INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
                VALUES ('%s', 'SBOM', now() - interval '1 day', '1.6', 'test', '%s', now())
                """.formatted(p, imp));
        for (int n = 1; n <= AVIS + 1; n++) {
            db.exec("""
                    INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
                    VALUES ('%s', '%s', 'pkg:maven/org.lg/lib-%d', 'pkg:maven/org.lg/lib-%d@1.0.0', '1.0.0', false, '%s')
                    """.formatted(id("lg-composant-" + nom + "-" + n), p, n, n, imp));
        }
    }

    private void impact(int n, String projet, UUID ws, String etat, int rang) {
        db.exec("""
                INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                                            level, method, transitive, inventory_origin, inventory_date, provenance,
                                            created_at, updated_at, state)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'EXACT', 'PURL_RANGE_V1', false, 'SBOM',
                        now() - interval '1 day', '{}', now(), now() - interval '%d minutes', '%s')
                """.formatted(id("lg-impact-" + n + "-" + projet), id("lg-signal-" + n), id("lg-version-" + n),
                id("lg-projet-" + projet), ws, id("lg-composant-" + projet + "-" + n), rang, etat));
    }

    /** Résultat attendu, calculé en base hors de l'application : avis, gravité, date et projets visibles. */
    private record Attendu(String identifiant, String gravite, OffsetDateTime date, Set<String> projets) {
    }

    private List<Attendu> attendus() throws SQLException {
        Map<String, Attendu> parAvis = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT s.external_id, s.severity, coalesce(s.published_at, s.modified_at), p.id
                FROM project_impact i JOIN signal s ON s.id = i.signal_id JOIN project p ON p.id = i.project_id
                WHERE i.workspace_id = ? AND i.state IN ('OUVERT', 'CONFIRME')
                ORDER BY coalesce(s.published_at, s.modified_at) DESC, i.created_at, i.id
                """)) {
            ps.setObject(1, espace);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    parAvis.computeIfAbsent(rs.getString(1), k -> {
                        try {
                            return new Attendu(k, rs.getString(2), rs.getObject(3, OffsetDateTime.class),
                                    new LinkedHashSet<>());
                        } catch (SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    }).projets().add(rs.getObject(4, UUID.class).toString());
                }
            }
        }
        return List.copyOf(parAvis.values());
    }

    /**
     * PRF-07-S2 : mêmes éléments, mêmes champs, même ordre que le calcul de référence ; aucun projet ni impact
     * écarté hors du périmètre de l'appelant ; gravité et date identiques à la lecture unitaire d'un signal.
     */
    @Test
    void prf07ContenuIdentiqueAuCalculDeReference() throws SQLException {
        JsonPath v = Api.en("jules").when().get("/api/v1/me/veille").then().statusCode(200).extract().jsonPath();
        List<Attendu> attendus = attendus();
        assertEquals(AVIS, attendus.size(), "jeu de données : un avis par signal impactant l'espace");

        assertEquals(attendus.stream().map(Attendu::identifiant).toList(), v.getList("elements.identifiant"),
                "mêmes avis, du plus récent au plus ancien ; l'avis sans impact dans l'espace est absent");
        for (int k = 0; k < attendus.size(); k++) {
            Attendu a = attendus.get(k);
            String e = "elements[" + k + "].";
            assertEquals("AVIS_SECURITE", v.getString(e + "type"));
            assertEquals("OSV", v.getString(e + "source"));
            assertEquals(a.gravite(), v.getString(e + "gravite"), a.identifiant());
            assertEquals(a.date().toInstant(), java.time.Instant.parse(v.getString(e + "date")), a.identifiant());
            assertEquals(a.projets(), new LinkedHashSet<>(v.getList(e + "projets.projetId", String.class)),
                    a.identifiant() + " : seuls les projets visibles de l'espace de l'appelant");
            assertEquals(List.of("developpeur"), v.getList(e + "profils"));
            assertEquals(List.of("securite"), v.getList(e + "rubriques.identifiant"));
            assertTrue(v.getList(e + "abonnements").isEmpty());

            // Lecture unitaire de référence : même gravité, même date que la lecture groupée.
            UUID sid = UUID.fromString(v.getString(e + "id"));
            SignalQueries.SignalView unitaire = signaux.find(sid).orElseThrow();
            assertEquals(unitaire.severity(), v.getString(e + "gravite"));
            assertEquals(unitaire.publishedAt() != null ? unitaire.publishedAt() : unitaire.modifiedAt(),
                    java.time.Instant.parse(v.getString(e + "date")));
        }
        assertFalse(v.prettify().contains(id("lg-projet-etranger").toString()), "aucun projet d'un autre espace");
        assertEquals(List.of("p1", "p2"), v.getList("elements.find { it.identifiant == 'GHSA-LG-2' }.projets.projet",
                String.class).stream().map(x -> x.substring("Projet ".length())).sorted().toList(),
                "l'impact écarté de l'avis 2 sur p3 n'est pas restitué");
    }

    /** Lecture groupée : chaque vue est celle de la lecture unitaire ; ordre des identifiants, inconnus absents. */
    @Test
    void lectureGroupeeEgaleALaLectureUnitaire() {
        List<UUID> demandes = new ArrayList<>(signauxIds);
        java.util.Collections.reverse(demandes);
        UUID inconnu = id("lg-signal-inconnu");
        demandes.add(1, inconnu);
        demandes.add(demandes.get(0));

        Map<UUID, SignalQueries.SignalView> lus = signaux.findAll(demandes);
        List<UUID> attendus = new ArrayList<>(new LinkedHashSet<>(demandes));
        attendus.remove(inconnu);
        assertEquals(attendus, List.copyOf(lus.keySet()), "ordre des identifiants donnés, sans doublon ni inconnu");
        for (UUID s : signauxIds) {
            assertEquals(signaux.find(s).orElseThrow(), lus.get(s));
        }
        assertTrue(signaux.findAll(List.of()).isEmpty());
    }
}
