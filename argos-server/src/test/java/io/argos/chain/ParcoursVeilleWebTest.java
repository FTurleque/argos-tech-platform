package io.argos.chain;

import static io.argos.chain.VeilleWebTest.ATOM;
import static io.argos.chain.VeilleWebTest.PIEGE;
import static io.argos.chain.VeilleWebTest.ajouter;
import static io.argos.chain.VeilleWebTest.atom;
import static io.argos.chain.VeilleWebTest.contenu;
import static io.argos.chain.VeilleWebTest.entree;
import static io.argos.chain.VeilleWebTest.source;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.InventoryService;
import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;

/**
 * Parcours de bout en bout (tâche 5.2) par l'API, avec des jetons réels et des sources simulées :
 * ajout d'un flux, refus d'une adresse privée, article reçu par deux flux restitué une fois,
 * article piégé sans effet. Contrat OpenAPI publié.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ParcoursVeilleWebTest {

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projects;

    @Inject
    InventoryService inventories;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    @Test
    void parcoursVeilleWeb() {
        UUID espace = Api.nouvelEspace("karim", "Équipe Parcours");
        Api.ajouterMembre("karim", espace.toString(), "nina", "LECTEUR");
        UUID projet = projects.create(espace, "Portail", null, null);
        inventories.importManifests(projet, Map.of("package.json",
                "{\"dependencies\":{\"@angular/core\":\"17.0.0\"}}".getBytes(StandardCharsets.UTF_8)));

        // 1. Ajout d'un flux d'espace par l'administrateur : la source est créée, ses articles apparaissent.
        String commun = entree("Angular 19", "https://blog.exemple.test/angular-19", "Sortie d'Angular 19.");
        StubServer.programmer("/equipe.xml", contenu(ATOM, atom("Flux équipe", commun,
                entree("Note", "https://exemple.test/piege", PIEGE))));
        ajouter("karim", source("ESPACE", espace, "FLUX", StubServer.base() + "/equipe.xml")).statusCode(201);

        // 2. Refus d'une adresse privée, sans source créée.
        ajouter("karim", source("ESPACE", espace, "FLUX", "http://192.168.1.1/flux")).statusCode(400)
                .body("code", equalTo("ADRESSE_INTERDITE"));

        // 3. Le même article par un second flux (personnel) : restitué une fois, avec les deux sources.
        StubServer.programmer("/perso.xml", contenu(ATOM, atom("Flux perso", commun)));
        ajouter("karim", source("PERSONNELLE", null, "FLUX", StubServer.base() + "/perso.xml")).statusCode(201);
        ValidatableResponse liste = Api.enTantQue("karim").when().get("/api/v1/veille-web/actualites").then().statusCode(200)
                .body("", hasSize(2))
                .body("find { it.titre == 'Angular 19' }.sources", hasSize(2));
        // Un lecteur de l'espace voit l'article, mais pas la source personnelle de karim.
        Api.enTantQue("nina").when().get("/api/v1/veille-web/actualites").then().statusCode(200)
                .body("", hasSize(2))
                .body("find { it.titre == 'Angular 19' }.sources", hasSize(1))
                .body("find { it.titre == 'Angular 19' }.sources[0].libelle", equalTo("Flux équipe"));

        // 4. Article piégé : cité comme donnée non fiable, aucun impact modifié ; Angular rapproché « à vérifier ».
        String piege = liste.extract().path("find { it.titre == 'Note' }.id");
        Api.enTantQue("nina").when().get("/api/v1/veille-web/actualites/" + piege).then().statusCode(200)
                .body("resume.extrait.texte", containsString(PIEGE))
                .body("resume.extrait.fiable", equalTo(false))
                .body("rapprochements", hasSize(0));
        String angular = liste.extract().path("find { it.titre == 'Angular 19' }.id");
        Api.enTantQue("nina").when().get("/api/v1/veille-web/actualites/" + angular).then().statusCode(200)
                .body("rapprochements[0].niveau", equalTo("A_VERIFIER"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM project_impact"));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM source_web"));
    }

    @Test
    void contratOpenApiPublie() {
        ValidatableResponse contrat = Api.enTantQue("alice").queryParam("format", "json").when().get("/q/openapi").then()
                .statusCode(200);
        operation(contrat, "/api/v1/veille-web/sources", "post", "201", "400", "401", "403", "404", "409");
        operation(contrat, "/api/v1/veille-web/sources", "get", "200", "401");
        operation(contrat, "/api/v1/veille-web/sources/{id}", "delete", "204", "401", "403", "404");
        operation(contrat, "/api/v1/veille-web/sources/{id}/collectes", "post", "200", "401", "403", "404");
        operation(contrat, "/api/v1/veille-web/actualites", "get", "200", "401", "404");
        operation(contrat, "/api/v1/veille-web/actualites/{id}", "get", "200", "401", "404");
    }

    private static void operation(ValidatableResponse contrat, String chemin, String methode, String... codes) {
        String base = "paths.'" + chemin + "'." + methode;
        contrat.body(base, notNullValue());
        for (String code : codes) {
            contrat.body(base + ".responses", hasKey(code));
        }
    }
}
