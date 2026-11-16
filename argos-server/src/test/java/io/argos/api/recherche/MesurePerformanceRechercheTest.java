package io.argos.api.recherche;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.argos.testsupport.Api;
import io.argos.testsupport.JeuRecherche;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

/**
 * Mesure du temps de réponse de la recherche (tâche 2.2 de {@code rechercher-et-filtrer}, RCH-05,
 * QS-13) sur le volume fixé le 2026-11-16 (Q-2) : 100 000 actualités dans chacun de deux espaces,
 * plus 20 000 signaux publics dont 2 000 impactent un projet de chaque espace. L'utilisateur mesuré
 * voit donc 120 000 éléments ; les 100 000 de l'autre espace sont écartés par les règles de visibilité.
 *
 * <p>La campagne passe par l'API REST ({@code GET /api/v1/recherche}, jeton réel) : la latence
 * mesurée est celle d'un appel d'API, comparée à la cible Q-05 (p95 &lt; 500 ms, arc42 §10.2). Le
 * rapport est écrit dans {@code target/mesure-recherche.txt}.
 *
 * <p>Mesure longue (génération du jeu comprise) : exécutée à la demande seulement, par
 * {@code mvn -f argos-server/pom.xml test -Dtest=MesurePerformanceRechercheTest -Dargos.performance=true}.
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "argos.performance", matches = "true")
class MesurePerformanceRechercheTest {

    static final int ACTUALITES_PAR_ESPACE = 100_000;
    static final int SIGNAUX = 20_000;
    static final int ECHAUFFEMENT = 3;
    static final int REPETITIONS = 20;
    static final long CIBLE_Q05_MS = 500;

    @Inject
    TestDb db;

    @Inject
    JeuRecherche jeu;

    @Inject
    DataSource dataSource;

    @Test
    void campagneDeRecherchesFiltrees() throws IOException {
        db.reset();
        UUID espaceA = Api.nouvelEspace("oscar", "Mesure A");
        UUID espaceB = Api.nouvelEspace("marc", "Mesure B");
        UUID oscar = Api.compte("oscar");
        UUID marc = Api.compte("marc");
        UUID paie = jeu.projet(espaceA, "Paie");
        UUID portail = jeu.projet(espaceB, "Portail");
        UUID sourceA = jeu.sourceEspace(espaceA, oscar);
        UUID sourceB = jeu.sourceEspace(espaceB, marc);

        long debut = System.nanoTime();
        generer(espaceA, espaceB, sourceA, sourceB, paie, portail);
        long generationS = (System.nanoTime() - debut) / 1_000_000_000L;

        String jeton = Api.jeton("oscar");
        Map<String, Map<String, Object>> campagne = new LinkedHashMap<>();
        campagne.put("mot sélectif (1 %)", Map.of("q", "Oracle"));
        campagne.put("deux mots", Map.of("q", "Oracle correctif"));
        campagne.put("mot moyen (5 %)", Map.of("q", "Kubernetes"));
        campagne.put("filtres seuls : 30 jours", Map.of("horizon", 30));
        campagne.put("filtres seuls : projet", Map.of("projet", paie.toString()));
        campagne.put("Oracle + projet + 30 jours", Map.of("q", "Oracle", "projet", paie.toString(), "horizon", 30));
        campagne.put("technologie Oracle + projet + 30 jours",
                Map.of("technologie", "Oracle", "projet", paie.toString(), "horizon", 30));
        campagne.put("gravité HIGH + 30 jours", Map.of("gravite", "HIGH", "horizon", 30));
        campagne.put("articles + mot", Map.of("type", "ARTICLE", "q", "correctif"));
        campagne.put("aucun filtre (fil complet)", Map.of());
        campagne.put("page 10", Map.of("q", "Kubernetes", "page", 10));
        Map<String, Object> pireCas = Map.of("q", "veille");

        for (int i = 0; i < ECHAUFFEMENT; i++) {
            campagne.values().forEach(p -> appeler(jeton, p));
            appeler(jeton, pireCas);
        }

        StringBuilder rapport = new StringBuilder();
        rapport.append("Mesure QS-13 — recherche de veille (rechercher-et-filtrer, tâche 2.2)\n");
        rapport.append("Jeu : ").append(ACTUALITES_PAR_ESPACE).append(" actualités par espace (2 espaces), ").append(SIGNAUX)
                .append(" signaux publics, 2 000 impacts par espace ; génération ").append(generationS).append(" s\n");
        rapport.append("Répétitions par requête : ").append(REPETITIONS).append(" après ").append(ECHAUFFEMENT)
                .append(" tours d'échauffement ; latence API (ms), client RestAssured local\n\n");
        rapport.append(String.format("%-40s %8s %8s %8s %8s%n", "requête", "total", "p50", "p95", "max"));
        List<Long> toutes = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> requete : campagne.entrySet()) {
            Mesure m = mesurer(jeton, requete.getValue());
            toutes.addAll(m.durees());
            rapport.append(String.format("%-40s %8d %8d %8d %8d%n", requete.getKey(), m.total(), centile(m.durees(), 50),
                    centile(m.durees(), 95), max(m.durees())));
        }
        long p95 = centile(toutes, 95);
        rapport.append(String.format("%n%-40s %8s %8d %8d %8d%n", "campagne (toutes requêtes)", "", centile(toutes, 50), p95,
                max(toutes)));
        Mesure pire = mesurer(jeton, pireCas);
        rapport.append(String.format("%-40s %8d %8d %8d %8d%n", "hors campagne : mot présent partout", pire.total(),
                centile(pire.durees(), 50), centile(pire.durees(), 95), max(pire.durees())));
        rapport.append("\nCible Q-05 (arc42 §10.2) : p95 < ").append(CIBLE_Q05_MS).append(" ms ; campagne : p95 = ").append(p95)
                .append(" ms\n");
        Path fichier = Path.of("target", "mesure-recherche.txt");
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, rapport.toString(), StandardCharsets.UTF_8);
        System.out.println(rapport);

        // Contrôles fonctionnels sur le volume : résultats attendus et cloisonnement.
        Response r = appeler(jeton, Map.of("q", "Oracle"));
        assertTrue(r.jsonPath().getLong("total") >= ACTUALITES_PAR_ESPACE / 100, r.asString());
        assertFalse(r.asString().contains(espaceB.toString()));
        assertEquals(0, appeler(jeton, Map.of("q", "Portail")).jsonPath().getLong("total"));
        assertTrue(p95 < CIBLE_Q05_MS, "p95 de la campagne : " + p95 + " ms");
    }

    private record Mesure(long total, List<Long> durees) {
    }

    private static Mesure mesurer(String jeton, Map<String, Object> parametres) {
        List<Long> durees = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < REPETITIONS; i++) {
            long debut = System.nanoTime();
            Response r = appeler(jeton, parametres);
            durees.add((System.nanoTime() - debut) / 1_000_000L);
            total = r.jsonPath().getLong("total");
        }
        return new Mesure(total, durees);
    }

    private static Response appeler(String jeton, Map<String, Object> parametres) {
        Response r = given().auth().oauth2(jeton).queryParams(parametres).when().get("/api/v1/recherche");
        assertEquals(200, r.statusCode(), r.asString());
        return r;
    }

    private static long centile(List<Long> valeurs, int centile) {
        List<Long> triees = valeurs.stream().sorted().toList();
        int rang = (int) Math.ceil(centile / 100.0 * triees.size()) - 1;
        return triees.get(Math.max(rang, 0));
    }

    private static long max(List<Long> valeurs) {
        return valeurs.stream().mapToLong(Long::longValue).max().orElse(0);
    }

    /**
     * Jeu généré en SQL, sous l'utilisateur de connexion : textes de 30 mots tirés d'un vocabulaire de
     * 3 000 mots, « Oracle » dans 1 % des titres, « Kubernetes » dans 5 %, « correctif » dans 10 %,
     * « veille » dans tous ; dates réparties sur un an.
     */
    private void generer(UUID espaceA, UUID espaceB, UUID sourceA, UUID sourceB, UUID paie, UUID portail) {
        executer("DROP TABLE IF EXISTS mesure_textes");
        executer("""
                CREATE UNLOGGED TABLE mesure_textes AS
                SELECT t, string_agg(md5((t * 7919 + k * 104729)::text), ' ') AS texte
                FROM generate_series(0, 999) t, generate_series(1, 30) k GROUP BY t
                """);
        for (UUID[] espace : List.of(new UUID[] {espaceA, sourceA}, new UUID[] {espaceB, sourceB})) {
            executer("""
                    INSERT INTO actualite (id, espace_id, empreinte, type, titre, texte, publie_le, recu_le)
                    SELECT gen_random_uuid(), '%1$s', md5('%1$s' || g), CASE WHEN g %% 10 = 3 THEN 'CHANGEMENT_PAGE' ELSE 'ARTICLE' END,
                           concat_ws(' ', CASE WHEN g %% 100 = 0 THEN 'Oracle' END, CASE WHEN g %% 20 = 0 THEN 'Kubernetes' END,
                                     CASE WHEN g %% 10 = 0 THEN 'correctif' END, 'veille article', md5(g::text)),
                           x.texte, now() - make_interval(days => g %% 365), now() - make_interval(days => g %% 365)
                    FROM generate_series(1, %2$d) g JOIN mesure_textes x ON x.t = g %% 1000
                    """.formatted(espace[0], ACTUALITES_PAR_ESPACE));
            executer("""
                    INSERT INTO actualite_source (actualite_id, source_id, recu_le)
                    SELECT id, '%s', recu_le FROM actualite WHERE espace_id = '%s'
                    """.formatted(espace[1], espace[0]));
        }
        executer("""
                BEGIN;
                SET CONSTRAINTS ALL DEFERRED;
                INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at, collected_at,
                                    updated_at, content_hash, affected, current_version_id)
                SELECT ('40000000-0000-0000-0000-' || lpad(g::text, 12, '0'))::uuid, 'OSV', 'GHSA-MESURE-' || g,
                       concat_ws(' ', CASE WHEN g %% 100 = 0 THEN 'Oracle' END, CASE WHEN g %% 20 = 0 THEN 'Kubernetes' END,
                                 CASE WHEN g %% 10 = 0 THEN 'correctif' END, 'veille avis', md5(g::text)),
                       x.texte, (ARRAY['LOW','MODERATE','HIGH','CRITICAL'])[1 + g %% 4],
                       now() - make_interval(days => g %% 365), now() - make_interval(days => g %% 365), now(), now(),
                       md5(g::text) || md5(g::text), '[]', ('50000000-0000-0000-0000-' || lpad(g::text, 12, '0'))::uuid
                FROM generate_series(1, %1$d) g JOIN mesure_textes x ON x.t = g %% 1000;
                INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
                SELECT ('50000000-0000-0000-0000-' || lpad(g::text, 12, '0'))::uuid,
                       ('40000000-0000-0000-0000-' || lpad(g::text, 12, '0'))::uuid, 1, md5(g::text) || md5(g::text), now(), now(), '{}'
                FROM generate_series(1, %1$d) g;
                COMMIT;
                """.formatted(SIGNAUX));
        // Impacts : signaux de rang 10, 20… sur Paie (espace A), de rang 5, 15… sur Portail (espace B).
        for (Map.Entry<UUID, Integer> projet : Map.of(paie, 10, portail, 5).entrySet()) {
            executer("""
                    INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level,
                                                method, inventory_origin, provenance, created_at, updated_at)
                    SELECT gen_random_uuid(), s.id, s.current_version_id, p.id, p.workspace_id, c.id, 'EXACT', 'PURL_EXACT',
                           'SBOM', '{"mesure":true}', now(), now()
                    FROM generate_series(0, 1999) g
                    JOIN signal s ON s.external_id = 'GHSA-MESURE-' || (g * 10 + %d)
                    JOIN project p ON p.id = '%s'
                    JOIN component c ON c.project_id = p.id
                    """.formatted(projet.getValue(), projet.getKey()));
        }
        executer("DROP TABLE mesure_textes");
        executer("ANALYZE");
    }

    private void executer(String sql) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }
}
