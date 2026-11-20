package io.argos.api.veille;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.argos.api.veille.UnionProfils.Candidat;
import io.argos.api.veille.UnionProfils.CandidatAbonnement;
import io.argos.api.veille.UnionProfils.Retenu;
import io.argos.api.veille.UnionProfils.RubriqueRetenue;
import io.argos.catalog.profil.ManifesteProfil;

/**
 * Union des profils actifs avec les manifestes publiés {@code developpeur} et {@code expert-metier}
 * (PRF-02-S1, PRF-02-S2, PRF-04-S1, PRF-06-S1). Le catalogue publié ne contient aucun élément qui relève
 * à la fois des deux profils : le scénario PRF-02-S1 est vérifié ici sur un élément de deux catégories,
 * et de bout en bout par {@code ProfilDemonstrationTest} avec deux profils qui rangent les avis de sécurité.
 */
class UnionProfilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final ManifesteProfil DEVELOPPEUR = manifeste("developpeur");
    private static final ManifesteProfil EXPERT = manifeste("expert-metier");
    private static final ManifesteProfil DEMONSTRATION = manifeste("demonstration");

    private static ManifesteProfil manifeste(String identifiant) {
        try (InputStream flux = UnionProfilsTest.class.getResourceAsStream("/catalog/profils/" + identifiant + ".json")) {
            return MAPPER.readValue(flux, ManifesteProfil.class);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    private static Candidat<String> candidat(String cle, boolean valide, String... categories) {
        return new Candidat<>(cle, Set.of(categories), valide, "contenu " + cle);
    }

    /** PRF-02-S1 : un élément qui relève des deux profils est restitué une fois, avec les rubriques des deux. */
    @Test
    void prf02S1ElementCommunADeuxProfils() {
        List<Retenu<String>> retenus = UnionProfils.unir(List.of(DEVELOPPEUR, EXPERT),
                List.of(candidat("commun", true, "avis-securite", "textes-officiels")));

        assertEquals(1, retenus.size());
        Retenu<String> retenu = retenus.get(0);
        assertEquals("contenu commun", retenu.contenu());
        assertEquals(List.of("developpeur", "expert-metier"), retenu.profils());
        assertEquals(List.of(new RubriqueRetenue("developpeur", "securite", "Sécurité"),
                new RubriqueRetenue("expert-metier", "reglementation", "Réglementation")), retenu.rubriques());
        assertTrue(retenu.gardeFous().stream().anyMatch(gardeFou -> gardeFou.contains("avis juridique")), retenu.gardeFous().toString());
        assertTrue(retenu.gardeFous().stream().anyMatch(gardeFou -> gardeFou.contains("ne décide jamais d'un impact")),
                retenu.gardeFous().toString());
    }

    /** Le même élément atteint deux fois (deux candidats de même clé) n'est restitué qu'une fois. */
    @Test
    void prf02S1MemeElementAtteintParDeuxCategories() {
        List<Retenu<String>> retenus = UnionProfils.unir(List.of(DEVELOPPEUR, EXPERT),
                List.of(candidat("e", true, "avis-securite"), candidat("autre", true, "avis-securite"),
                        candidat("e", true, "textes-officiels")));

        assertEquals(List.of("contenu e", "contenu autre"), retenus.stream().map(Retenu::contenu).toList());
        assertEquals(List.of("securite", "reglementation"),
                retenus.get(0).rubriques().stream().map(RubriqueRetenue::identifiant).toList());
        assertEquals(List.of("securite"), retenus.get(1).rubriques().stream().map(RubriqueRetenue::identifiant).toList());
    }

    /** PRF-02-S2 : un élément qui ne relève que d'un profil inactif n'est pas restitué. */
    @Test
    void prf02S2ElementDUnProfilInactif() {
        List<Retenu<String>> retenus = UnionProfils.unir(List.of(DEVELOPPEUR),
                List.of(candidat("fiche", true, "textes-officiels"), candidat("avis", false, "avis-securite")));

        assertEquals(List.of("contenu avis"), retenus.stream().map(Retenu::contenu).toList());
        assertEquals(List.of("developpeur"), retenus.get(0).profils());
    }

    /** Un élément d'une catégorie qu'aucune rubrique active ne range n'est pas restitué. */
    @Test
    void elementHorsDesCategoriesActives() {
        assertTrue(UnionProfils.unir(List.of(DEVELOPPEUR, EXPERT),
                List.of(candidat("article", true, "actualites-web"))).isEmpty());
    }

    /**
     * PRF-06-S1 : le garde-fou VALIDATION_HUMAINE d'expert-metier écarte un élément non validé qui relève de
     * ce profil, même s'il relève aussi de developpeur.
     */
    @Test
    void prf06S1ElementNonValideDUnProfilAGardeFou() {
        assertTrue(UnionProfils.unir(List.of(DEVELOPPEUR, EXPERT),
                List.of(candidat("fiche", false, "textes-officiels"))).isEmpty());
        assertTrue(UnionProfils.unir(List.of(DEVELOPPEUR, EXPERT),
                List.of(candidat("commun", false, "avis-securite", "textes-officiels"))).isEmpty());
        // Une occurrence non validée suffit : la fusion ne valide pas un élément.
        assertTrue(UnionProfils.unir(List.of(EXPERT),
                List.of(candidat("f", true, "textes-officiels"), candidat("f", false, "textes-officiels"))).isEmpty());
    }

    /** PRF-06-S2 : validé, l'élément est restitué avec les garde-fous du profil. */
    @Test
    void prf06S2ElementValide() {
        List<Retenu<String>> retenus = UnionProfils.unir(List.of(EXPERT), List.of(candidat("fiche", true, "textes-officiels")));
        assertEquals(1, retenus.size());
        assertTrue(retenus.get(0).gardeFous().stream().anyMatch(gardeFou -> gardeFou.contains("validation par l'expert")));
    }

    /**
     * PRF-04 : l'union ne dépend que des profils actifs. Le profil de démonstration, qui range lui aussi les
     * avis de sécurité, n'ajoute ses rubriques et ses mentions qu'à qui l'active.
     */
    @Test
    void prf04SeulsLesProfilsActifsComptent() {
        List<Candidat<String>> candidats = List.of(candidat("avis", false, "avis-securite"),
                candidat("fiche", true, "textes-officiels"));

        List<Retenu<String>> sans = UnionProfils.unir(List.of(DEVELOPPEUR), candidats);
        assertEquals(List.of("contenu avis"), sans.stream().map(Retenu::contenu).toList());
        assertEquals(List.of(new RubriqueRetenue("developpeur", "securite", "Sécurité")), sans.get(0).rubriques());
        assertTrue(sans.get(0).gardeFous().stream().noneMatch(gardeFou -> gardeFou.contains("démonstration")));

        List<Retenu<String>> avec = UnionProfils.unir(List.of(DEVELOPPEUR, DEMONSTRATION), candidats);
        assertEquals(1, avec.size());
        assertEquals(List.of("developpeur", "demonstration"), avec.get(0).profils());
        assertEquals(List.of("securite", "demonstration"),
                avec.get(0).rubriques().stream().map(RubriqueRetenue::identifiant).toList());
    }

    private static final UUID ABO_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ABO_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static CandidatAbonnement<String> parAbonnement(String cle, boolean valide, Set<UUID> abonnements,
            String... categories) {
        return new CandidatAbonnement<>(candidat(cle, valide, categories), abonnements);
    }

    /**
     * Q-3 : un élément retenu par un profil et par des abonnements PERSONAL est restitué une fois, avec la rubrique
     * du profil et tous les abonnements ; un élément que seul un abonnement retient s'ajoute sans rubrique.
     */
    @Test
    void q3UnionDesProfilsEtDesAbonnementsSansDoublon() {
        List<Retenu<String>> retenus = UnionProfils.unir(List.of(EXPERT),
                List.of(candidat("fiche", true, "textes-officiels")),
                List.of(parAbonnement("avis", false, Set.of(ABO_2), "avis-securite"),
                        parAbonnement("fiche", true, Set.of(ABO_1), "textes-officiels"),
                        parAbonnement("avis", false, Set.of(ABO_1), "avis-securite")));

        assertEquals(List.of("fiche", "avis"), retenus.stream().map(Retenu::cle).toList());
        Retenu<String> fiche = retenus.get(0);
        assertEquals(List.of("expert-metier"), fiche.profils());
        assertEquals(List.of("reglementation"), fiche.rubriques().stream().map(RubriqueRetenue::identifiant).toList());
        assertEquals(List.of(ABO_1), fiche.abonnements());
        Retenu<String> avis = retenus.get(1);
        assertEquals(List.of(), avis.profils());
        assertEquals(List.of(), avis.rubriques());
        assertEquals(List.of(), avis.gardeFous(), "aucun profil actif ne range les avis de sécurité");
        assertEquals(List.of(ABO_1, ABO_2), avis.abonnements());
    }

    /** Sans abonnement, l'union est celle des seuls profils actifs, sans abonnement porté. */
    @Test
    void q3SansAbonnementLUnionDesProfilsEstInchangee() {
        List<Candidat<String>> candidats = List.of(candidat("avis", false, "avis-securite"));
        List<Retenu<String>> retenus = UnionProfils.unir(List.of(DEVELOPPEUR), candidats, List.of());
        assertEquals(UnionProfils.unir(List.of(DEVELOPPEUR), candidats), retenus);
        assertEquals(List.of(), retenus.get(0).abonnements());
    }

    /**
     * Q-3 et PRF-06 : les garde-fous des profils actifs qui rangent un élément d'abonnement s'appliquent. Non
     * validé, un élément rangé par expert-metier (VALIDATION_HUMAINE) n'est restitué par aucune voie ; un élément
     * d'abonnement rangé par developpeur porte sa mention sans en prendre la rubrique.
     */
    @Test
    void q3LesGardeFousDesProfilsActifsSAppliquentAuxAbonnements() {
        assertTrue(UnionProfils.unir(List.of(EXPERT), List.of(),
                List.of(parAbonnement("texte", false, Set.of(ABO_1), "textes-officiels"))).isEmpty());
        assertTrue(UnionProfils.unir(List.of(DEVELOPPEUR, EXPERT), List.of(candidat("commun", true, "avis-securite")),
                List.of(parAbonnement("commun", false, Set.of(ABO_1), "textes-officiels"))).isEmpty(),
                "une occurrence non validée rangée par expert-metier écarte l'élément, même retenu par un profil");

        List<Retenu<String>> retenus = UnionProfils.unir(List.of(DEVELOPPEUR), List.of(),
                List.of(parAbonnement("avis", false, Set.of(ABO_1), "avis-securite")));
        assertEquals(1, retenus.size());
        assertEquals(List.of(), retenus.get(0).rubriques());
        assertTrue(retenus.get(0).gardeFous().stream().anyMatch(gardeFou -> gardeFou.contains("ne décide jamais d'un impact")));
    }
}
