package io.argos.api.catalogue;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import io.argos.ingestion.SourceSynchronizer;
import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.argos.workspace.preference.PreferencesRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

/**
 * Publication d'un profil de démonstration (PRF-04-S1) et union de deux profils qui rangent le même
 * élément (PRF-02-S1, de bout en bout).
 *
 * <p>Le manifeste de test {@code catalog/profils/demonstration.json} range les avis de sécurité, comme
 * {@code developpeur}, et déclare un garde-fou propre. Le profil apparaît au catalogue ; la veille des
 * utilisateurs qui ne l'activent pas reste celle de leurs profils (aucune rubrique, aucun profil ni aucune
 * mention de démonstration, mêmes éléments), et un utilisateur qui l'active avec {@code developpeur} reçoit
 * l'avis une seule fois, avec les rubriques des deux profils. Jetons réels, lectures sous la RLS.
 */
@QuarkusTest
@TestProfile(ProfilDemonstrationTest.AvecDemonstration.class)
@QuarkusTestResource(StubServer.class)
class ProfilDemonstrationTest {

    /** Catalogue enrichi du manifeste de test {@code catalog/profils/demonstration.json}. */
    public static class AvecDemonstration implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.catalogue.profils.manifestes", "developpeur,expert-metier,demonstration");
        }
    }

    private static final String LIB = "pkg:maven/org.exemple/demolib";
    private static final String AVIS = "GHSA-DEMO-PRF04";
    private static final String FICHE = "JORFTEXT000097200001";

    @Inject
    PreferencesRepository repository;

    @Inject
    TestDb baseDeTest;

    @Inject
    ReglementaireFixtures reg;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @Inject
    SourceSynchronizer synchronizer;

    @Test
    @TestSecurity(user = "catalogue-lecteur")
    void prf04S1LeProfilDeDemonstrationApparaitAuCatalogue() {

        given().when().get("/api/v1/catalogue/profils")
            .then().statusCode(200)
            .body("identifiant", contains("developpeur", "expert-metier", "demonstration"))
            .body("find { it.identifiant == 'developpeur' }.version", equalTo("1.0.0"));

    }

    @Test
    void prf04S1LesProfilsActifsDesAutresUtilisateursSontInchanges() {
        repository.enregistrerProfils(Api.compte("ines"), Set.of("developpeur"));

        Api.enTantQue("ines").when().get("/api/v1/preferences")
            .then().statusCode(200)
            .body("profilsActifs", contains("developpeur"));
    }

    /** PRF-04-S1 : la veille des utilisateurs developpeur ou expert-metier ignore le profil publié. */
    @Test
    void prf04S1LaVeilleDesAutresUtilisateursEstInchangee() {
        preparerVeille();
        activer("ines", "developpeur");
        activer("oscar", "expert-metier");
        activer("jules", "developpeur", "expert-metier");

        JsonPath ines = veille("ines");
        assertEquals(List.of(AVIS), ines.getList("elements.identifiant"));
        assertEquals(List.of("developpeur"), ines.getList("elements[0].profils"));
        assertEquals(List.of("securite"), ines.getList("elements[0].rubriques.identifiant"));

        JsonPath oscar = veille("oscar");
        assertEquals(List.of(FICHE), oscar.getList("elements.identifiant"));
        assertEquals(List.of("reglementation"), oscar.getList("elements[0].rubriques.identifiant"));

        JsonPath jules = veille("jules");
        assertEquals(List.of(FICHE, AVIS), jules.getList("elements.identifiant"));
        for (JsonPath veille : List.of(ines, oscar, jules)) {
            assertFalse(veille.getList("elements.profils.flatten()").isEmpty(), veille.prettify());
            assertFalse(veille.getList("profilsActifs").contains("demonstration"), veille.prettify());
            assertFalse(veille.getList("elements.profils.flatten()").contains("demonstration"), veille.prettify());
            assertFalse(veille.getList("elements.rubriques.profil.flatten()").contains("demonstration"), veille.prettify());
            assertFalse(veille.<String>getList("elements.gardeFous.flatten()").stream()
                    .anyMatch(gardeFou -> gardeFou.startsWith("Mention de démonstration")), veille.prettify());
        }
    }

    /** PRF-02-S1 : un élément qui relève de deux profils actifs est restitué une fois, avec leurs deux rubriques. */
    @Test
    void prf02S1ElementCommunADeuxProfilsActifs() {
        preparerVeille();
        activer("ines", "developpeur", "demonstration");

        JsonPath veille = veille("ines");
        assertEquals(List.of(AVIS), veille.getList("elements.identifiant"), "une seule occurrence");
        assertEquals(List.of("developpeur", "demonstration"), veille.getList("elements[0].profils"));
        assertEquals(List.of("developpeur", "demonstration"), veille.getList("elements[0].rubriques.profil"));
        assertEquals(List.of("securite", "demonstration"), veille.getList("elements[0].rubriques.identifiant"));
        assertEquals(true, veille.getList("elements[0].gardeFous", String.class).stream()
                .anyMatch(gardeFou -> gardeFou.startsWith("Mention de démonstration")));
    }

    private void preparerVeille() {
        baseDeTest.reset();
        for (String utilisateur : List.of("ines", "jules", "oscar")) {
            baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = '" + Api.compte(utilisateur) + "'");
        }
        UUID espace = Api.nouvelEspace("ines", "Démonstration des profils");
        Api.ajouterMembre("ines", espace.toString(), "jules", "LECTEUR");
        Api.ajouterMembre("ines", espace.toString(), "oscar", "LECTEUR");
        UUID projet = projects.create(espace, "demo-app", null, null);
        inventories.importSbom(projet, Fixtures.cyclonedx("1.6", Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                java.util.Collections.singletonList(new String[] {"c", LIB + "@1.2.0"}), List.of("c"))
                .getBytes(StandardCharsets.UTF_8), "ci");
        Fixtures.stubOsv(AVIS, "2026-11-11T00:00:00Z",
                Fixtures.osv(AVIS, "2026-11-11T00:00:00Z", "Maven", "org.exemple:demolib", LIB, "0", "1.3.0"));
        synchronizer.synchronize("OSV");
        UUID ines = Api.compte("ines");
        reg.domaine(espace, "paie", "Paie", ines);
        reg.fiche(reg.texte(FICHE, "Décret de test des profils", LocalDate.of(2026, 11, 4), null),
                espace, "paie", "VALIDEE", "Résumé relu.", ines);
    }

    private static void activer(String utilisateur, String... profils) {
        Api.enTantQue(utilisateur).contentType(ContentType.JSON).body(Map.of("profilsActifs", List.of(profils)))
                .when().put("/api/v1/preferences/profils").then().statusCode(200);
    }

    private static JsonPath veille(String utilisateur) {
        return Api.enTantQue(utilisateur).when().get("/api/v1/me/veille").then().statusCode(200).extract().jsonPath();
    }
}
