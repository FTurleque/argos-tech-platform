package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.impact.CorrelationEngine;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * État de traitement des impacts (changement automatiser-chaine-veille, volet C, tâches 3.1 et 3.2 ; décision J5 du
 * porteur du projet, 2026-11-19) : niveau et état distincts (COR-09), transitions par un membre CONTRIBUTEUR ou
 * ADMINISTRATEUR avec refus 400, 403, 404 et 409 (COR-10), historique et filtre par état (COR-12), garde-fou RLS
 * sous {@code argos_app} et contrat OpenAPI. Jetons réels du Keycloak des Dev Services ; source OSV simulée.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class EtatTraitementImpactTest {

    private static final String LIB = "pkg:maven/org.exemple/lib";

    @Inject
    TestDb db;

    @Inject
    DataSource dataSource;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    CorrelationEngine engine;

    private UUID spaceA;
    private UUID spaceB;

    /** alice administre A, jules y est CONTRIBUTEUR, karim LECTEUR ; ines administre B. */
    @BeforeEach
    void reset() {
        db.reset();
        spaceA = Api.nouvelEspace("alice", "Espace traitement A");
        spaceB = Api.nouvelEspace("ines", "Espace traitement B");
        Api.compte("jules");
        Api.compte("karim");
        Api.ajouterMembre("alice", spaceA.toString(), "jules", "CONTRIBUTEUR");
        Api.ajouterMembre("alice", spaceA.toString(), "karim", "LECTEUR");
    }

    private UUID projet(UUID espace, String nom, Instant genere, String... purls) {
        UUID projet = projects.create(espace, nom, null, null);
        importer(projet, genere, purls);
        return projet;
    }

    private void importer(UUID projet, Instant genere, String... purls) {
        List<String[]> composants = new ArrayList<>();
        for (int i = 0; i < purls.length; i++) {
            composants.add(new String[] {"c" + i, purls[i]});
        }
        inventories.importSbom(projet, Fixtures.cyclonedx("1.6", genere.toString(), composants, List.of("c0"))
                .getBytes(StandardCharsets.UTF_8), "ci");
    }

    private UUID publier(String id, String purl, String introduit, String corrige) {
        String json = Fixtures.osv(id, "2026-11-10T00:00:00Z", "Maven", "org.exemple:lib", purl, introduit, corrige);
        Fixtures.stubOsv(id, "2026-11-10T00:00:00Z", json);
        synchronizer.synchronize("OSV");
        return db.uuid("SELECT id FROM signal WHERE external_id = '" + id + "'");
    }

    private static Instant recent() {
        return Instant.now().minus(2, ChronoUnit.DAYS);
    }

    private UUID impactDe(UUID projet) {
        return db.uuid("SELECT id FROM project_impact WHERE project_id = '" + projet + "'");
    }

    private static io.restassured.response.ValidatableResponse transition(String utilisateur, UUID impact,
            Map<String, ?> corps) {
        return Api.en(utilisateur).contentType(ContentType.JSON).body(corps)
                .when().post("/api/v1/impacts/" + impact + "/transitions").then();
    }

    private String etat(UUID impact) {
        return db.string("SELECT state FROM project_impact WHERE id = '" + impact + "'");
    }

    private long transitions(UUID impact) {
        return db.count("SELECT count(*) FROM project_impact_transition WHERE impact_id = '" + impact + "'");
    }

    @Test
    void cor09S1ImpactCreeOuvertAvecSonNiveau() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-1", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);

        assertEquals("OUVERT", etat(impact));
        Api.en("karim").when().get("/api/v1/impacts/" + impact)
                .then().statusCode(200)
                .body("level", equalTo("EXACT"))
                .body("etat", equalTo("OUVERT"))
                .body("historique", empty())
                .body("transitionsPossibles", empty());
        Api.en("jules").when().get("/api/v1/impacts/" + impact)
                .then().statusCode(200)
                .body("transitionsPossibles", equalTo(List.of("CONFIRME", "ECARTE", "RESOLU")));
    }

    @Test
    void cor09S2NiveauRecalculeSansEffetSurLEtat() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        UUID signal = publier("GHSA-ETAT-2", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);
        transition("jules", impact, Map.of("vers", "CONFIRME")).statusCode(200).body("etat", equalTo("CONFIRME"));

        // SBOM devenue obsolète (plus de 30 jours) : le niveau passe probable, l'état reste confirmé.
        importer(projet, Instant.now().minus(40, ChronoUnit.DAYS), LIB + "@1.2.0");
        engine.correlate(signal, java.util.Optional.of(projet));

        assertEquals(impact, impactDe(projet), "même impact (COR-07)");
        assertEquals("PROBABLE", db.string("SELECT level FROM project_impact WHERE id = '" + impact + "'"));
        assertEquals("CONFIRME", etat(impact));
    }

    @Test
    void cor09S3EtCor10S1ConfirmationParUnContributeurSansEffetSurLeNiveau() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-3", null, "0", "1.3.0");
        UUID impact = impactDe(projet);
        assertEquals("A_VERIFIER", db.string("SELECT level FROM project_impact WHERE id = '" + impact + "'"));

        transition("jules", impact, Map.of("vers", "CONFIRME", "commentaire", "Version confirmée en production"))
                .statusCode(200)
                .body("etat", equalTo("CONFIRME"))
                .body("level", equalTo("A_VERIFIER"))
                .body("historique", hasSize(1))
                .body("historique[0].de", equalTo("OUVERT"))
                .body("historique[0].vers", equalTo("CONFIRME"))
                .body("historique[0].auteur", notNullValue())
                .body("historique[0].auteurCompteId", equalTo(Api.compte("jules").toString()))
                .body("historique[0].systeme", equalTo(false))
                .body("historique[0].commentaire", equalTo("Version confirmée en production"))
                .body("historique[0].date", notNullValue())
                .body("transitionsPossibles", equalTo(List.of("ECARTE", "RESOLU")));
        assertEquals(1, transitions(impact));
    }

    @Test
    void cor10S2LecteurRefuseEtRefusJournalise() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-4", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);

        transition("karim", impact, Map.of("vers", "ECARTE", "motif", "Non utilisé")).statusCode(403);

        assertEquals("OUVERT", etat(impact));
        assertEquals(0, transitions(impact));
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("karim")
                + "' AND ressource = 'POST /api/v1/impacts/" + impact + "/transitions' AND motif = 'ROLE_INSUFFISANT'"));
    }

    @Test
    void cor10S3TransitionNonPrevueEt409Idempotent() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-5", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);
        transition("alice", impact, Map.of("vers", "ECARTE", "motif", "Composant non déployé")).statusCode(200);

        transition("jules", impact, Map.of("vers", "CONFIRME"))
                .statusCode(409)
                .body("code", equalTo("TRANSITION_INTERDITE"))
                .body("message", containsString("Transitions possibles : OUVERT"));
        assertEquals("ECARTE", etat(impact));
        assertEquals(1, transitions(impact), "aucune transition historisée pour la demande refusée");

        // Transition vers l'état courant : refusée sans historiser.
        transition("jules", impact, Map.of("vers", "ECARTE", "motif", "encore")).statusCode(409);
        assertEquals(1, transitions(impact));
    }

    @Test
    void cor10S4EcarterSansMotifEtDonneesInvalides() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-6", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);

        transition("jules", impact, Map.of("vers", "ECARTE")).statusCode(400)
                .body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo("motif"));
        transition("jules", impact, Map.of("vers", "ECARTE", "motif", "   ")).statusCode(400)
                .body("code", equalTo("DONNEE_INVALIDE"));
        transition("jules", impact, Map.of("vers", "CLOS")).statusCode(400)
                .body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo("vers"));
        transition("jules", impact, Map.of("vers", "CONFIRME", "commentaire", "x".repeat(1001))).statusCode(400)
                .body("attribut", equalTo("commentaire"));
        assertEquals("OUVERT", etat(impact));
        assertEquals(0, transitions(impact));
    }

    @Test
    void cor10S5ImpactDUnAutreEspace() {
        UUID projetB = projet(spaceB, "b", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-7", LIB, "0", "1.3.0");
        UUID impactB = impactDe(projetB);

        String corps = transition("alice", impactB, Map.of("vers", "CONFIRME")).statusCode(404).extract().asString();

        assertTrue(!corps.contains(projetB.toString()) && !corps.contains("GHSA-ETAT-7"), "le refus ne révèle rien");
        assertEquals("OUVERT", etat(impactB));
        assertEquals(1, db.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("alice")
                + "' AND ressource = 'POST /api/v1/impacts/" + impactB + "/transitions' AND motif = 'NON_MEMBRE'"));
        transition("alice", UUID.randomUUID(), Map.of("vers", "CONFIRME")).statusCode(404);
        given().contentType(ContentType.JSON).body(Map.of("vers", "CONFIRME"))
                .when().post("/api/v1/impacts/" + impactB + "/transitions").then().statusCode(401);
    }

    @Test
    void cor12S1HistoriqueConsulteParUnLecteur() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0", "pkg:maven/org.autre/outil@1.0.0");
        UUID signal = publier("GHSA-ETAT-8", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);
        transition("jules", impact, Map.of("vers", "CONFIRME", "commentaire", "Confirmé")).statusCode(200);

        // Le composant quitte l'inventaire : la recorrélation du projet résout l'impact (COR-11-S1).
        importer(projet, recent(), "pkg:maven/org.autre/outil@1.0.0");

        assertEquals("RESOLU", etat(impact));
        Api.en("karim").when().get("/api/v1/impacts/" + impact)
                .then().statusCode(200)
                .body("etat", equalTo("RESOLU"))
                .body("signalId", equalTo(signal.toString()))
                .body("componentId", nullValue())
                .body("componentPurl", equalTo(LIB + "@1.2.0"))
                .body("componentVersion", equalTo("1.2.0"))
                .body("historique", hasSize(2))
                .body("historique[0].vers", equalTo("CONFIRME"))
                .body("historique[0].commentaire", equalTo("Confirmé"))
                .body("historique[1].de", equalTo("CONFIRME"))
                .body("historique[1].vers", equalTo("RESOLU"))
                .body("historique[1].auteur", equalTo("SYSTEME"))
                .body("historique[1].systeme", equalTo(true))
                .body("historique[1].motif", equalTo("COMPOSANT_RETIRE"));
    }

    /**
     * J5 : une résolution décidée par un utilisateur autorisé n'est pas annulée par la recorrélation (import suivant ou
     * nouvelle corrélation du signal) ; seule une résolution système peut redevenir « ouvert » (COR-11-S5).
     */
    @Test
    void j5ResolutionHumaineConserveeALaRecorrelation() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        UUID signal = publier("GHSA-ETAT-J5", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);
        transition("jules", impact, Map.of("vers", "RESOLU", "commentaire", "Atténué hors SBOM")).statusCode(200);

        importer(projet, Instant.now().minus(1, ChronoUnit.DAYS), LIB + "@1.2.0");
        engine.correlateProject(projet);
        engine.correlate(signal, Optional.empty());

        assertEquals("RESOLU", etat(impact));
        assertEquals(1, transitions(impact), "aucune réouverture système");
    }

    /**
     * INV-C3 (J5) : un impact dont le composant a quitté l'inventaire ne peut pas redevenir ouvert par une transition
     * utilisateur (409), et la transition n'est pas proposée.
     */
    @Test
    void invC3ImpactSansComposantNeRedevientPasActif() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0", "pkg:maven/org.autre/outil@1.0.0");
        publier("GHSA-ETAT-C3", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);
        importer(projet, Instant.now().minus(1, ChronoUnit.DAYS), "pkg:maven/org.autre/outil@1.0.0");
        assertEquals("RESOLU", etat(impact));

        transition("jules", impact, Map.of("vers", "OUVERT"))
                .statusCode(409).body("code", equalTo("TRANSITION_INTERDITE"));
        assertEquals("RESOLU", etat(impact));
        assertEquals(1, transitions(impact));
        Api.en("jules").when().get("/api/v1/impacts/" + impact)
                .then().statusCode(200).body("transitionsPossibles", hasSize(0));
    }

    @Test
    void cor12S2ListeFiltreeParEtat() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.0.0", LIB + "@1.1.0", LIB + "@1.2.0");
        UUID signal = publier("GHSA-ETAT-9", LIB, "0", "1.3.0");
        List<UUID> ids = new ArrayList<>();
        for (String v : List.of("1.0.0", "1.1.0", "1.2.0")) {
            ids.add(db.uuid("SELECT i.id FROM project_impact i JOIN component c ON c.id = i.component_id"
                    + " WHERE i.project_id = '" + projet + "' AND c.version = '" + v + "'"));
        }
        transition("jules", ids.get(1), Map.of("vers", "ECARTE", "motif", "Code mort")).statusCode(200);
        transition("jules", ids.get(2), Map.of("vers", "RESOLU", "commentaire", "Corrigé")).statusCode(200);
        String chemin = "/api/v1/signals/" + signal + "/impacts";

        Api.en("jules").queryParam("etat", "OUVERT").when().get(chemin)
                .then().statusCode(200).body("total", equalTo(1)).body("items[0].id", equalTo(ids.get(0).toString()))
                .body("items[0].etat", equalTo("OUVERT"));
        Api.en("jules").when().get(chemin)
                .then().statusCode(200).body("total", equalTo(1)).body("items[0].etat", equalTo("OUVERT"))
                .body("items[0].historique", nullValue());
        Api.en("jules").queryParam("etat", "ECARTE").queryParam("etat", "resolu").when().get(chemin)
                .then().statusCode(200).body("total", equalTo(2));
        Api.en("jules").queryParam("etat", "INCONNU").when().get(chemin)
                .then().statusCode(400).body("code", equalTo("DONNEE_INVALIDE")).body("attribut", equalTo("etat"));

        // Liste des impacts du projet (COR-12-S2 au sens strict) : même filtre, même défaut.
        String projetChemin = "/api/v1/projects/" + projet + "/impacts";
        Api.en("jules").queryParam("etat", "OUVERT").when().get(projetChemin)
                .then().statusCode(200).body("$", hasSize(1)).body("[0].id", equalTo(ids.get(0).toString()));
        Api.en("karim").when().get(projetChemin)
                .then().statusCode(200).body("$", hasSize(1)).body("[0].etat", equalTo("OUVERT"));
        Api.en("jules").queryParam("etat", "ECARTE").queryParam("etat", "RESOLU").when().get(projetChemin)
                .then().statusCode(200).body("$", hasSize(2));
        Api.en("jules").queryParam("etat", "INCONNU").when().get(projetChemin)
                .then().statusCode(400).body("code", equalTo("DONNEE_INVALIDE"));
        Api.en("ines").when().get(projetChemin).then().statusCode(404);
    }

    /**
     * Garde-fou en base (COR-10-S2) : sous argos_app, un LECTEUR ou un non-membre ne peut ni modifier l'état ni écrire
     * l'historique, même par une requête directe ; un CONTRIBUTEUR le peut. L'historique n'est lisible que des membres.
     */
    @Test
    void rlsLecteurNePeutPasModifierLEtat() throws Exception {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-RLS", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);
        String majEtat = "UPDATE project_impact SET state = 'ECARTE' WHERE id = '" + impact + "'";

        assertEquals(0, (int) this.<Integer>sousArgosApp(Api.compte("karim"), s -> s.executeUpdate(majEtat)), "lecteur : aucune ligne");
        assertEquals(0, (int) this.<Integer>sousArgosApp(Api.compte("ines"), s -> s.executeUpdate(majEtat)), "non-membre : aucune ligne");
        assertEquals("OUVERT", etat(impact));
        UUID karim = Api.compte("karim");
        SQLException refus = assertThrows(SQLException.class, () -> sousArgosApp(karim, s -> s.executeUpdate(
                "INSERT INTO project_impact_transition(id, impact_id, workspace_id, from_state, to_state, actor_account_id,"
                        + " actor_system, reason, occurred_at) VALUES (gen_random_uuid(), '" + impact + "', '" + spaceA
                        + "', 'OUVERT', 'ECARTE', '" + karim + "', false, 'forcé', now())")));
        assertEquals("42501", refus.getSQLState(), "politique d'insertion de l'historique");
        SQLException colonne = assertThrows(SQLException.class, () -> sousArgosApp(Api.compte("jules"),
                s -> s.executeUpdate("UPDATE project_impact SET level = 'EXACT' WHERE id = '" + impact + "'")));
        assertEquals("42501", colonne.getSQLState(), "seule la colonne state est modifiable");

        assertEquals(1, (int) this.<Integer>sousArgosApp(Api.compte("jules"), s -> s.executeUpdate(
                "UPDATE project_impact SET state = 'CONFIRME' WHERE id = '" + impact + "'")), "contributeur");

        transition("jules", impact, Map.of("vers", "RESOLU")).statusCode(200);
        assertEquals(1L, (long) sousArgosApp(Api.compte("karim"), s -> compter(s,
                "SELECT count(*) FROM project_impact_transition")), "historique lisible d'un membre");
        assertEquals(0L, (long) sousArgosApp(Api.compte("ines"), s -> compter(s,
                "SELECT count(*) FROM project_impact_transition")), "historique invisible d'un non-membre");
    }

    /**
     * Migration V20 : un impact écrit sans état est OUVERT, son instantané paquet / PURL / version suit le composant,
     * le retrait du composant met la référence à nul sans supprimer l'impact, et l'état est contraint.
     */
    @Test
    void migrationV20EtatParDefautInstantaneEtRetraitDuComposant() {
        UUID projet = projet(spaceA, "app", recent(), LIB + "@1.2.0");
        publier("GHSA-ETAT-V20", LIB, "0", "1.3.0");
        UUID impact = impactDe(projet);
        db.exec("DELETE FROM project_impact_transition");
        db.exec("""
                CREATE TEMP TABLE copie AS SELECT * FROM project_impact;
                DELETE FROM project_impact;
                INSERT INTO project_impact(id, signal_id, signal_version_id, project_id, workspace_id, component_id, level,
                       method, transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
                SELECT id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method, transitive,
                       inventory_origin, inventory_date, provenance, created_at, updated_at FROM copie;
                DROP TABLE copie;
                """);

        assertEquals("OUVERT", etat(impact));
        assertEquals(LIB + "|" + LIB + "@1.2.0|1.2.0", db.string("SELECT package_key || '|' || component_purl || '|'"
                + " || component_version FROM project_impact WHERE id = '" + impact + "'"));
        db.exec("UPDATE project_impact SET state = 'RESOLU' WHERE id = '" + impact + "'");
        db.exec("DELETE FROM component WHERE project_id = '" + projet + "'");
        assertEquals(1, db.count("SELECT count(*) FROM project_impact WHERE id = '" + impact
                + "' AND component_id IS NULL AND component_purl = '" + LIB + "@1.2.0'"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> db.exec("UPDATE project_impact SET state = 'CLOS'"));
        assertEquals("23514", ((SQLException) e.getCause()).getSQLState());
        IllegalStateException motif = assertThrows(IllegalStateException.class, () -> db.exec(
                "INSERT INTO project_impact_transition(id, impact_id, workspace_id, from_state, to_state, actor_system,"
                        + " occurred_at) VALUES (gen_random_uuid(), '" + impact + "', '" + spaceA
                        + "', 'OUVERT', 'ECARTE', true, now())"));
        assertEquals("23514", ((SQLException) motif.getCause()).getSQLState(), "écarter sans motif refusé en base");
    }

    @Test
    void contratOpenApiDesTransitions() {
        Api.en("alice").queryParam("format", "json")
                .when().get("/q/openapi")
                .then().statusCode(200)
                .body("paths.'/api/v1/impacts/{id}/transitions'.post.responses.keySet()",
                        hasItems("200", "400", "403", "404", "409"))
                .body("paths.'/api/v1/signals/{id}/impacts'.get.parameters.name", hasItems("etat", "page", "size"))
                .body("components.schemas.DemandeTransitionImpact.properties.keySet()",
                        hasItems("vers", "motif", "commentaire"))
                .body("components.schemas.ImpactView.properties.keySet()",
                        hasItems("level", "etat", "historique", "transitionsPossibles"))
                .body("components.schemas.TransitionView.properties.keySet()",
                        hasItems("de", "vers", "auteur", "motif", "commentaire", "date"));
    }

    private interface Requete<T> {
        T executer(Statement s) throws SQLException;
    }

    /** Exécute la requête sous argos_app avec le compte donné, dans une transaction validée si elle réussit. */
    private <T> T sousArgosApp(UUID compte, Requete<T> requete) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                s.execute("SET LOCAL ROLE argos_app");
                s.execute("SELECT set_config('argos.compte_courant', '" + compte + "', true)");
                T resultat = requete.executer(s);
                c.commit();
                return resultat;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static long compter(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
