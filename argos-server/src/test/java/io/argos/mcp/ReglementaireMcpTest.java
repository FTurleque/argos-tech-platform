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

import io.argos.regintel.FichesReglementaires;
import io.argos.testsupport.Api;
import io.argos.testsupport.Mcp;
import io.argos.testsupport.ReferentielsSimules;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * MCP-07 : garde-fous des profils sur {@code get_regulatory_changes} (tâche 3.3). Les fiches sont
 * simulées ({@link ReferentielsSimules}) tant que la veille réglementaire n'est pas livrée.
 */
@QuarkusTest
class ReglementaireMcpTest {

    @Inject
    TestDb db;

    private UUID espace;
    private UUID espaceAutre;

    @BeforeEach
    void preparer() {
        db.reset();
        espace = Api.nouvelEspace("oscar", "Espace paie");
        espaceAutre = Api.nouvelEspace("marc", "Espace voisin paie");
    }

    private static FichesReglementaires.Fiche fiche(UUID espace, String reference, FichesReglementaires.Etat etat) {
        return new FichesReglementaires.Fiche(UUID.randomUUID(), espace, "paie", etat, "Décret relatif à " + reference,
                reference, "Résumé relu de " + reference, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 1), "legifrance");
    }

    /** MCP-07-S1 : une fiche en attente de validation n'est pas renvoyée. */
    @Test
    void mcp07S1FicheNonValidee() {
        ReferentielsSimules.FichesSimulees.ajouter(fiche(espace, "NOR-VALIDEE", FichesReglementaires.Etat.VALIDEE));
        ReferentielsSimules.FichesSimulees.ajouter(fiche(espace, "NOR-EN-ATTENTE", FichesReglementaires.Etat.EN_ATTENTE_VALIDATION));
        ReferentielsSimules.FichesSimulees.ajouter(fiche(espace, "NOR-REJETEE", FichesReglementaires.Etat.REJETEE));
        ReferentielsSimules.FichesSimulees.ajouter(fiche(espaceAutre, "NOR-AUTRE-ESPACE", FichesReglementaires.Etat.VALIDEE));

        ExtractableResponse<Response> r = Mcp.appeler("oscar", "get_regulatory_changes", Map.of("domain", "paie"));
        String base = "result.structuredContent.";
        assertEquals(Boolean.FALSE, r.path("result.isError"), r.asString());
        assertEquals(1, (int) r.path(base + "nombre"));
        assertEquals("NOR-VALIDEE", r.path(base + "fiches[0].referenceOfficielle"));
        assertEquals("2027-01-01", r.path(base + "fiches[0].effet"));
        assertEquals(TexteExterne.NATURE, r.path(base + "fiches[0].titreOfficiel.nature"));
        assertFalse(r.asString().contains("NOR-EN-ATTENTE"));
        assertFalse(r.asString().contains("NOR-REJETEE"));
        assertFalse(r.asString().contains("NOR-AUTRE-ESPACE"));
        List<String> gardeFous = r.path(base + "gardeFous");
        assertTrue(gardeFous.stream().anyMatch(g -> g.contains("validation par l'expert")), gardeFous.toString());
        assertTrue(gardeFous.stream().anyMatch(g -> g.contains("avis juridique")), gardeFous.toString());
    }

    /** Le garde-fou s'applique même si la veille réglementaire renvoie une fiche non validée. */
    @Test
    void gardeFouIndependantDeLaSource() {
        ReferentielsSimules.FichesSimulees.ajouter(fiche(espace, "NOR-SEULE-EN-ATTENTE", FichesReglementaires.Etat.EN_ATTENTE_VALIDATION));
        ExtractableResponse<Response> r = Mcp.appeler("oscar", "get_regulatory_changes", Map.of());
        assertEquals(0, (int) r.path("result.structuredContent.nombre"), r.asString());
        assertFalse(r.asString().contains("NOR-SEULE-EN-ATTENTE"));
    }
}
