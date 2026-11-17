package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * MCP-07 : garde-fous des profils sur {@code get_regulatory_changes}, avec les fiches réelles de la
 * veille réglementaire ({@code FichesEnBase}, lecture sous la RLS) : seules les fiches validées des
 * espaces de l'appelant sont renvoyées, même à un expert qui voit les fiches en attente.
 */
@QuarkusTest
class ReglementaireMcpTest {

    @Inject
    TestDb db;

    @Inject
    ReglementaireFixtures reg;

    private UUID espace;
    private UUID espaceAutre;
    private UUID oscar;

    @BeforeEach
    void preparer() {
        db.reset();
        espace = Api.nouvelEspace("oscar", "Espace paie");
        espaceAutre = Api.nouvelEspace("marc", "Espace voisin paie");
        oscar = Api.compte("oscar");
        reg.domaine(espace, "paie", "Paie", oscar);
        reg.domaine(espaceAutre, "paie", "Paie", Api.compte("marc"));
    }

    private UUID fiche(UUID esp, String identifiant, String etat) {
        UUID texte = reg.texte(identifiant, "Décret relatif à " + identifiant, LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 1, 1));
        return reg.fiche(texte, esp, "paie", etat, "Résumé relu de " + identifiant, oscar);
    }

    /** MCP-07-S1 : une fiche en attente de validation n'est pas renvoyée. */
    @Test
    void mcp07S1FicheNonValidee() {
        fiche(espace, "JORFTEXT000099100001", "VALIDEE");
        fiche(espace, "JORFTEXT000099100002", "EN_ATTENTE_VALIDATION");
        fiche(espace, "JORFTEXT000099100003", "REJETEE");
        fiche(espaceAutre, "JORFTEXT000099100004", "VALIDEE");

        ExtractableResponse<Response> r = Mcp.appeler("oscar", "get_regulatory_changes", Map.of("domain", "paie"));
        String base = "result.structuredContent.";
        assertEquals(Boolean.FALSE, r.path("result.isError"), r.asString());
        assertEquals(1, (int) r.path(base + "nombre"));
        assertEquals("JORFTEXT000099100001", r.path(base + "fiches[0].referenceOfficielle"));
        assertEquals("2027-01-01", r.path(base + "fiches[0].effet"));
        assertEquals("paie", r.path(base + "fiches[0].domaine"));
        assertEquals("https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000099100001", r.path(base + "fiches[0].lienOfficiel"));
        assertEquals(TexteExterne.NATURE, r.path(base + "fiches[0].titreOfficiel.nature"));
        assertFalse(r.asString().contains("JORFTEXT000099100002"));
        assertFalse(r.asString().contains("JORFTEXT000099100003"));
        assertFalse(r.asString().contains("JORFTEXT000099100004"));
        List<String> gardeFous = r.path(base + "gardeFous");
        assertTrue(gardeFous.stream().anyMatch(g -> g.contains("validation par l'expert")), gardeFous.toString());
        assertTrue(gardeFous.stream().anyMatch(g -> g.contains("avis juridique")), gardeFous.toString());
    }

    /** Le garde-fou s'applique même à un expert du domaine, à qui la base restitue la fiche en attente. */
    @Test
    void gardeFouIndependantDeLaSource() {
        reg.expert(espace, "paie", oscar);
        fiche(espace, "JORFTEXT000099100005", "EN_ATTENTE_VALIDATION");
        assertEquals(1, Api.en("oscar").queryParam("etat", "EN_ATTENTE_VALIDATION").when().get("/api/v1/reglementaire/fiches")
                .then().statusCode(200).extract().jsonPath().getList("$").size(), "l'expert voit la fiche en attente");

        ExtractableResponse<Response> r = Mcp.appeler("oscar", "get_regulatory_changes", Map.of());
        assertEquals(0, (int) r.path("result.structuredContent.nombre"), r.asString());
        assertFalse(r.asString().contains("JORFTEXT000099100005"));
    }

    /** Les fiches en attente d'un expert n'occupent pas la limite de lecture : la fiche validée reste servie. */
    @Test
    void fichesEnAttenteNEvincentPasLesFichesValidees() {
        reg.expert(espace, "paie", oscar);
        for (int i = 0; i < 200; i++) {
            fiche(espace, String.format("JORFTEXT0000992%05d", i), "EN_ATTENTE_VALIDATION");
        }
        fiche(espace, "JORFTEXT000099300001", "VALIDEE");

        ExtractableResponse<Response> r = Mcp.appeler("oscar", "get_regulatory_changes", Map.of());
        assertEquals(1, (int) r.path("result.structuredContent.nombre"), r.asString());
        assertEquals("JORFTEXT000099300001", r.path("result.structuredContent.fiches[0].referenceOfficielle"));
    }
}
