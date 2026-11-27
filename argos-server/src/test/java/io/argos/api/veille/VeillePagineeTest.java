package io.argos.api.veille;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.commun.Pagination;
import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * « Ma veille » paginée au format commun des listes (PRF-09, ADR-0040) : page découpée dans l'union composée, triée par
 * date décroissante, puis type et identifiant.
 *
 * <p>Le jeu de données, écrit hors RLS comme la collecte et la corrélation, compte {@value #AVIS} avis qui impactent
 * chacun un projet de l'espace de l'appelant ; les avis vont par trois à la même date de publication.
 */
@QuarkusTest
class VeillePagineeTest {

    private static final int AVIS = 60;
    private static final int AVIS_PAR_DATE = 3;
    private static final String VEILLE = "/api/v1/me/veille";
    private static final String APPELANT = "jules";

    @Inject
    TestDb baseDeTest;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = ?", Api.compte(APPELANT));
        UUID espace = Api.nouvelEspace("ines", "Veille paginée");
        Api.ajouterMembre("ines", espace.toString(), APPELANT, "LECTEUR");
        Api.enTantQue(APPELANT).contentType(ContentType.JSON).body(Map.of("profilsActifs", List.of("developpeur")))
                .when().put("/api/v1/preferences/profils").then().statusCode(200);
        UUID projet = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO project (id, workspace_id, name) VALUES (?, ?, 'Projet paginé')", projet, espace);
        baseDeTest.exec("""
                INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
                VALUES (?, 'SBOM', now() - interval '1 day', '1.6', 'test', ?, now())
                """, projet, importId);
        avisEtImpacts(espace, projet, importId);
    }

    /** Avis, composants et impacts en trois instructions ; avis et première version dans la même instruction. */
    private void avisEtImpacts(UUID espace, UUID projet, UUID importId) {
        baseDeTest.exec("""
                WITH avis AS (
                    INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                                        collected_at, updated_at, content_hash, affected, current_version_id)
                    SELECT gen_random_uuid(), 'OSV', 'PAGE-' || lpad(n::text, 3, '0'), 'Avis paginé ' || n, NULL, 'HIGH',
                           date_trunc('day', now()) - make_interval(days => (n - 1) / ?::int),
                           date_trunc('day', now()) - make_interval(days => (n - 1) / ?::int), now(), now(),
                           md5('page' || n) || md5('pagine' || n), '[]'::jsonb, gen_random_uuid()
                    FROM generate_series(1, ?::int) n
                    RETURNING id, current_version_id, content_hash, modified_at)
                INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at,
                                            raw_content)
                SELECT current_version_id, id, 1, content_hash, modified_at, now(), '{}' FROM avis
                """, AVIS_PAR_DATE, AVIS_PAR_DATE, AVIS);
        baseDeTest.exec("""
                INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
                SELECT gen_random_uuid(), ?, 'pkg:maven/org.page/' || s.external_id,
                       'pkg:maven/org.page/' || s.external_id || '@1.0.0', '1.0.0', false, ?
                FROM signal s WHERE s.external_id LIKE 'PAGE-%'
                """, projet, importId);
        baseDeTest.exec("""
                INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                                            level, method, transitive, inventory_origin, inventory_date, provenance,
                                            created_at, updated_at)
                SELECT gen_random_uuid(), s.id, s.current_version_id, c.project_id, ?, c.id, 'EXACT', 'PURL_RANGE_V1',
                       false, 'SBOM', now() - interval '1 day', '{}', now(), now()
                FROM signal s
                JOIN component c ON c.project_id = ? AND c.package_key = 'pkg:maven/org.page/' || s.external_id
                WHERE s.external_id LIKE 'PAGE-%'
                """, espace, projet);
    }

    private static JsonPath veille(String parametres) {
        return Api.enTantQue(APPELANT).when().get(VEILLE + parametres).then().statusCode(200).extract().jsonPath();
    }

    /** PRF-09-S1 : sans paramètre, les 50 plus récents, page 0, taille 50, total de l'union, profils actifs. */
    @Test
    void prf09S1PremierePageParDefaut() {
        JsonPath page = veille("");

        assertEquals(Pagination.TAILLE_DEFAUT, page.getList("items").size());
        assertEquals(0, page.getInt("page"));
        assertEquals(Pagination.TAILLE_DEFAUT, page.getInt("size"));
        assertEquals(AVIS, page.getLong("total"));
        assertEquals(List.of("developpeur"), page.getList("profilsActifs"));
        assertTrue(List.of("PAGE-001", "PAGE-002", "PAGE-003").contains(page.getString("items[0].identifiant")),
                "un des avis les plus récents d'abord");
    }

    /** PRF-09-S2 : deux pages disjointes qui réunissent, dans l'ordre, la veille lue en une page. */
    @Test
    void prf09S2PagesDisjointesEtStables() {
        List<String> entiere = veille("?size=" + Pagination.TAILLE_MAX).getList("items.id");
        List<String> premiere = veille("?page=0&size=" + Pagination.TAILLE_DEFAUT).getList("items.id");
        List<String> seconde = veille("?page=1&size=" + Pagination.TAILLE_DEFAUT).getList("items.id");

        assertEquals(AVIS, entiere.size());
        assertEquals(AVIS - Pagination.TAILLE_DEFAUT, seconde.size());
        assertTrue(Collections.disjoint(premiere, seconde), "aucun élément commun aux deux pages");
        List<String> reunies = new ArrayList<>(premiere);
        reunies.addAll(seconde);
        assertEquals(entiere, reunies);
        // À date égale, l'ordre suit l'identifiant.
        List<String> memeDate = entiere.subList(0, AVIS_PAR_DATE);
        assertEquals(memeDate.stream().sorted().toList(), memeDate);
    }

    /** PRF-09-S3 : un paramètre non entier donne 400 DONNEE_INVALIDE sur l'attribut en cause. */
    @Test
    void prf09S3ParametreNonEntierRefuse() {
        Api.enTantQue(APPELANT).when().get(VEILLE + "?size=abc").then().statusCode(400)
                .body("code", equalTo("DONNEE_INVALIDE"))
                .body("attribut", equalTo("size"));
    }
}
