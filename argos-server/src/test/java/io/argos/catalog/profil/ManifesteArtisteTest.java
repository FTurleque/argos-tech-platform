package io.argos.catalog.profil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Manifeste du pack {@code artiste} (ajouter-profil-artiste, tâche 2.1 ; ART-01, ART-02, ART-05, ART-06). */
class ManifesteArtisteTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static CatalogueProfils catalogue() {
        return new CatalogueProfils(CatalogueProfils.lire(MAPPER, List.of("developpeur", "expert-metier", "artiste")),
                Set.of());
    }

    /** ART-01 : le pack est publié avec ses cinq rubriques et une stratégie déterministe. */
    @Test
    void art01LePackArtisteEstPublieAvecSesCinqRubriques() {
        ManifesteProfil artiste = catalogue().trouver("artiste").orElseThrow();
        assertEquals(List.of("appels-residences", "aides-creation", "droits-remunerations", "statut-social-fiscal",
                "evenements"), artiste.rubriques().stream().map(Rubrique::identifiant).toList());
        assertEquals(List.of(new StrategieDeclaree("discipline-territoire-date-limite", true)), artiste.strategies());
        assertTrue(artiste.impose(TypeGardeFou.AUCUN_AVIS_ELIGIBILITE), "ART-06");
        assertTrue(artiste.impose(TypeGardeFou.SOURCES_VERIFIEES), "ART-05");
        assertTrue(artiste.impose(TypeGardeFou.IA_SANS_DECISION), "ADR-0013");
    }

    /** Q-2, Q-3 : six disciplines et les territoires de France (France entière et les 18 régions). */
    @Test
    void lesListesReprennentLesArbitragesQ2EtQ3() {
        ManifesteProfil artiste = catalogue().trouver("artiste").orElseThrow();
        assertEquals(List.of("arts-visuels", "musique", "spectacle-vivant", "litterature", "cinema-audiovisuel",
                "design-metiers-art"), artiste.liste("discipline").orElseThrow().identifiants());
        List<String> territoires = artiste.liste("territoire").orElseThrow().identifiants();
        assertEquals(19, territoires.size());
        assertTrue(territoires.containsAll(List.of("france", "occitanie", "ile-de-france", "la-reunion", "mayotte")));
    }

    /** PRF-04 : publier le pack artiste ne modifie pas les manifestes existants. */
    @Test
    void ajouterLePackArtisteNeModifiePasLesAutresProfils() {
        CatalogueProfils avant = new CatalogueProfils(
                CatalogueProfils.lire(MAPPER, List.of("developpeur", "expert-metier")), Set.of());
        CatalogueProfils apres = catalogue();
        assertEquals(avant.trouver("developpeur"), apres.trouver("developpeur"));
        assertEquals(avant.trouver("expert-metier"), apres.trouver("expert-metier"));
        assertTrue(apres.trouver("developpeur").orElseThrow().listes().isEmpty());
    }

    @Test
    void uneListeDUnTypeNonDeclareOuEnDoubleEstRefusee() {
        ManifesteProfil manifeste = new ManifesteProfil(1, "listes", "1.0.0", "L", "D", List.of("c"), List.of("k"),
                List.of(new Rubrique("r", "R")), List.of("discipline"), List.of(new StrategieDeclaree("s", true)),
                List.of(), List.of(
                        new ListeObjets("territoire", List.of(new ValeurListe("a", "A"))),
                        new ListeObjets("discipline", List.of(new ValeurListe("a", "A"), new ValeurListe("a", "B"),
                                new ValeurListe("Mal Formé", "C"))),
                        new ListeObjets("discipline", List.of())));
        List<String> erreurs = ValidateurManifeste.erreurs(manifeste);
        assertTrue(erreurs.stream().anyMatch(erreur -> erreur.contains("non déclaré")), erreurs::toString);
        assertTrue(erreurs.stream().anyMatch(erreur -> erreur.contains("valeur en double")), erreurs::toString);
        assertTrue(erreurs.stream().anyMatch(erreur -> erreur.contains("sans identifiant valide")), erreurs::toString);
        assertTrue(erreurs.stream().anyMatch(erreur -> erreur.contains("liste en double")), erreurs::toString);
        assertTrue(erreurs.stream().anyMatch(erreur -> erreur.contains("liste vide")), erreurs::toString);
    }
}
