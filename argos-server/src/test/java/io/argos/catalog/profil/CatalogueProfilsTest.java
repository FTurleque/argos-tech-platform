package io.argos.catalog.profil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Format du manifeste et catalogue des profils (PRF-03-S1, PRF-04, ADR-0024). */
class CatalogueProfilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static ManifesteProfil valide(String identifiant) {
        return new ManifesteProfil(1, identifiant, "1.0.0", "Libellé", "Description",
                List.of("categorie"), List.of("connecteur"), List.of(new Rubrique("rubrique", "Rubrique")),
                List.of("objet"), List.of(new StrategieDeclaree("strategie", true)),
                List.of(new GardeFou(TypeGardeFou.VALIDATION_HUMAINE, "Validation")));
    }

    @Test
    void lesManifestesInitiauxSontValidesEtPublies() {
        List<ManifesteProfil> manifestes = CatalogueProfils.lire(MAPPER, List.of("developpeur", "expert-metier"));
        CatalogueProfils catalogue = new CatalogueProfils(manifestes, Set.of());

        assertEquals(List.of("developpeur", "expert-metier"),
                catalogue.publies().stream().map(ManifesteProfil::identifiant).toList());
        ManifesteProfil expert = catalogue.trouver("expert-metier").orElseThrow();
        assertTrue(expert.gardeFous().stream().anyMatch(g -> g.type() == TypeGardeFou.VALIDATION_HUMAINE),
                "expert-metier impose la validation par l'expert (ADR-0018)");
        assertTrue(catalogue.publies().stream().allMatch(m -> m.strategies().stream()
                .allMatch(StrategieDeclaree::deterministe)), "stratégies déterministes (ADR-0013)");
    }

    @Test
    void unProfilRetireNEstPlusPublie() {
        CatalogueProfils catalogue = new CatalogueProfils(List.of(valide("a"), valide("b")), Set.of("b"));
        assertTrue(catalogue.estPublie("a"));
        assertFalse(catalogue.estPublie("b"));
        assertFalse(catalogue.estPublie("inconnu"));
        assertEquals(1, catalogue.publies().size());
    }

    @Test
    void ajouterUnProfilNeModifiePasLesAutres() {
        ManifesteProfil a = valide("a");
        CatalogueProfils avant = new CatalogueProfils(List.of(a), Set.of());
        CatalogueProfils apres = new CatalogueProfils(List.of(a, valide("demonstration")), Set.of());
        assertEquals(avant.trouver("a"), apres.trouver("a"));
        assertTrue(apres.estPublie("demonstration"));
    }

    @Test
    void uneStrategieNonDeterministeEstRefusee() {
        ManifesteProfil m = new ManifesteProfil(1, "ia", "1.0.0", "IA", "Rapprochement par l'IA",
                List.of("c"), List.of("k"), List.of(new Rubrique("r", "R")), List.of("o"),
                List.of(new StrategieDeclaree("llm", false)), List.of());
        ManifesteInvalideException e = assertThrows(ManifesteInvalideException.class,
                () -> new CatalogueProfils(List.of(m), Set.of()));
        assertTrue(e.erreurs().stream().anyMatch(err -> err.contains("non déterministe")), e.getMessage());
    }

    @Test
    void unManifesteIncompletEstRefuse() {
        ManifesteProfil m = new ManifesteProfil(2, "Mauvais Id", null, "", null,
                null, null, null, null, null, null);
        List<String> erreurs = ValidateurManifeste.erreurs(m);
        assertTrue(erreurs.size() >= 8, () -> erreurs.toString());
    }

    @Test
    void unProfilDeclareDeuxFoisEstRefuse() {
        assertThrows(ManifesteInvalideException.class,
                () -> new CatalogueProfils(List.of(valide("a"), valide("a")), Set.of()));
    }

    @Test
    void unManifesteDontLeNomDiffereDuFichierEstRefuse() {
        assertThrows(ManifesteInvalideException.class,
                () -> CatalogueProfils.lire(MAPPER, List.of("nom-different")));
    }

    @Test
    void unGardeFouInconnuEstRefuseALaLecture() {
        assertThrows(ManifesteInvalideException.class,
                () -> CatalogueProfils.lire(MAPPER, List.of("garde-fou-inconnu")));
    }

    /** Une rubrique ne range que des catégories de sources déclarées par le profil (PRF-02). */
    @Test
    void uneRubriqueNeRangeQueDesCategoriesDuProfil() {
        ManifesteProfil m = new ManifesteProfil(1, "rubriques", "1.0.0", "R", "Rubriques",
                List.of("c"), List.of("k"), List.of(new Rubrique("r", "R", List.of("c", "autre"))), List.of("o"),
                List.of(new StrategieDeclaree("s", true)), List.of());
        List<String> erreurs = ValidateurManifeste.erreurs(m);
        assertEquals(List.of("catégorie de la rubrique r absente de categoriesSources : autre"), erreurs);
    }

    /** Les rubriques des profils publiés rangent leurs catégories de sources : la veille par profils les utilise. */
    @Test
    void lesRubriquesInitialesRangentLesCategoriesDesProfils() {
        CatalogueProfils catalogue = new CatalogueProfils(
                CatalogueProfils.lire(MAPPER, List.of("developpeur", "expert-metier")), Set.of());
        assertTrue(catalogue.trouver("developpeur").orElseThrow().rubriques().get(0).range("avis-securite"));
        assertTrue(catalogue.trouver("expert-metier").orElseThrow().rubriques().get(0).range("textes-officiels"));
        assertFalse(catalogue.trouver("expert-metier").orElseThrow().diffusable(false), "VALIDATION_HUMAINE");
        assertTrue(catalogue.trouver("developpeur").orElseThrow().diffusable(false));
    }

    @Test
    void unManifesteAbsentEstRefuse() {
        assertThrows(ManifesteInvalideException.class, () -> CatalogueProfils.lire(MAPPER, List.of("absent")));
    }
}
