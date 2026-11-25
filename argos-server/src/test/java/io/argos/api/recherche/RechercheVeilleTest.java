package io.argos.api.recherche;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.impact.RechercheVeille;
import io.argos.testsupport.Api;
import io.argos.testsupport.JeuRecherche;
import io.argos.testsupport.TestDb;
import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Recherche plein texte, filtres combinables, droits et vues enregistrées (tâches 1.1, 1.2 et 2.1 de
 * {@code rechercher-et-filtrer}) par l'API REST, avec des jetons réels du Keycloak de test et des
 * espaces réels. Les contrôles de cloisonnement s'exécutent sous le rôle {@code argos_app}.
 */
@QuarkusTest
class RechercheVeilleTest {

    private static final Instant MAINTENANT = Instant.now();
    /** Statut observé pour {@code horizon=abc} (caractérisation). */
    private static final int STATUT_HORIZON_NON_ENTIER = 404;

    @Inject
    TestDb baseDeTest;

    @Inject
    JeuRecherche jeu;

    @Inject
    RechercheVeille recherche;

    @Inject
    ContexteBase base;

    private UUID espaceOscar;
    private UUID espaceMarc;
    private UUID oscar;
    private UUID marc;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espaceOscar = Api.nouvelEspace("oscar", "Espace Paie");
        espaceMarc = Api.nouvelEspace("marc", "Espace Portail");
        oscar = Api.compte("oscar");
        marc = Api.compte("marc");
    }

    private static Instant ilYA(int jours) {
        return MAINTENANT.minus(Duration.ofDays(jours));
    }

    private static JsonPath rechercher(String utilisateur, Map<String, ?> parametres) {
        return Api.enTantQue(utilisateur).queryParams(parametres).when().get("/api/v1/recherche")
                .then().statusCode(200).extract().jsonPath();
    }

    private static List<String> ids(JsonPath resultats) {
        return resultats.getList("elements.id", String.class);
    }

    /** RCH-01-S1 : « Oracle correctif » renvoie les éléments correspondants, les plus pertinents d'abord. */
    @Test
    void rch01S1MotTrouve() {
        UUID fort = jeu.signal("GHSA-RCH1-0001", "Oracle correctif critique de la base",
                "Le correctif Oracle corrige une faille de la base Oracle.", "HIGH", ilYA(2), null);
        UUID faible = jeu.signal("GHSA-RCH1-0002", "Mise à jour d'un pilote",
                "Ce pilote accompagne un correctif publié pour Oracle.", "LOW", ilYA(1), null);
        UUID horsSujet = jeu.signal("GHSA-RCH1-0003", "Faille d'un serveur web", "Aucun rapport.", "LOW", ilYA(1), null);

        JsonPath resultats = rechercher("oscar", Map.of("q", "Oracle correctif"));
        assertEquals(List.of(fort.toString(), faible.toString()), ids(resultats), resultats.prettify());
        assertEquals(2, resultats.getInt("total"));
        assertNull(resultats.get("message"));
        assertTrue(resultats.getDouble("elements[0].pertinence") > resultats.getDouble("elements[1].pertinence"));
        assertFalse(ids(resultats).contains(horsSujet.toString()));
        assertEquals("AVIS_SECURITE", resultats.getString("elements[0].type"));
        assertEquals("GHSA-RCH1-0001", resultats.getString("elements[0].identifiant"));
    }

    /** Q-1 : racines françaises et anglaises, identifiants d'avis ; actualités web comprises. */
    @Test
    void rch01RechercheBilingue() {
        UUID source = jeu.sourceEspace(espaceOscar, oscar);
        UUID anglais = jeu.actualite(source, "Security patches released", "Several patches fix vulnerabilities.",
                "https://exemple.test/patches", ilYA(3));
        UUID francais = jeu.signal("GHSA-RCH1-FR01", "Correctifs de sécurité publiés", "Plusieurs correctifs.", "LOW",
                ilYA(3), null);

        assertEquals(List.of(anglais.toString()), ids(rechercher("oscar", Map.of("q", "patch"))));
        assertEquals(List.of(francais.toString()), ids(rechercher("oscar", Map.of("q", "correctif"))));
        assertEquals(List.of(francais.toString()), ids(rechercher("oscar", Map.of("q", "GHSA-RCH1-FR01"))));
        JsonPath article = rechercher("oscar", Map.of("q", "patches"));
        assertEquals("ARTICLE", article.getString("elements[0].type"));
        assertEquals("WEB", article.getString("elements[0].source"));
        assertEquals("https://exemple.test/patches", article.getString("elements[0].lien"));
    }

    /** RCH-01-S2 : un mot absent de tout élément donne une liste vide et un message explicite. */
    @Test
    void rch01S2AucunResultat() {
        jeu.signal("GHSA-RCH1-0004", "Oracle correctif", "Correctif.", "LOW", ilYA(1), null);
        JsonPath resultats = rechercher("oscar", Map.of("q", "motabsentdetoutelement"));
        assertEquals(0, resultats.getList("elements").size());
        assertEquals(0, resultats.getInt("total"));
        assertEquals(RechercheVeille.AUCUN_RESULTAT, resultats.getString("message"));
    }

    /** RCH-02-S1 : projet Paie, technologie Oracle et horizon 30 jours : seuls les éléments satisfaisant les trois. */
    @Test
    void rch02S1FiltresCombines() {
        UUID paie = jeu.projet(espaceOscar, "Paie");
        UUID autre = jeu.projet(espaceOscar, "Comptabilité");
        UUID attendu = jeu.signal("GHSA-RCH2-0001", "Oracle Database : faille corrigée", "Correctif.", "HIGH", ilYA(5), null);
        UUID ancien = jeu.signal("GHSA-RCH2-0002", "Oracle Database : faille ancienne", "Correctif.", "HIGH", ilYA(60), null);
        UUID autreProjet = jeu.signal("GHSA-RCH2-0003", "Oracle Java : faille", "Correctif.", "HIGH", ilYA(5), null);
        UUID autreTechno = jeu.signal("GHSA-RCH2-0004", "PostgreSQL : faille", "Correctif.", "HIGH", ilYA(5), null);
        UUID parPaquet = jeu.signal("GHSA-RCH2-0005", "Faille d'une bibliothèque", "Correctif.", "LOW", ilYA(5),
                "pkg:maven/com.oracle/ojdbc");
        jeu.impact(attendu, paie);
        jeu.impact(ancien, paie);
        jeu.impact(autreProjet, autre);
        jeu.impact(autreTechno, paie);
        jeu.impact(parPaquet, paie);
        UUID source = jeu.sourceEspace(espaceOscar, oscar);
        UUID article = jeu.actualite(source, "Nouvelle version majeure", "Annonce sans le nom.", null, ilYA(4));
        jeu.rapprochement(article, paie, "Oracle");

        JsonPath resultats = rechercher("oscar", Map.of("projet", paie.toString(), "technologie", "Oracle", "horizon", 30));
        assertEquals(Set.of(attendu.toString(), article.toString()), Set.copyOf(ids(resultats)), resultats.prettify());
        assertTrue(resultats.getList("elements.projets.projet").stream().allMatch(projet -> projet.toString().contains("Paie")));

        // Technologie reconnue par l'écosystème du paquet affecté (java → Maven).
        assertEquals(List.of(parPaquet.toString()), ids(rechercher("oscar",
                Map.of("projet", paie.toString(), "technologie", "java", "horizon", 30))));
        // Gravité minimale : les actualités, sans gravité, sont écartées.
        assertEquals(List.of(attendu.toString()), ids(rechercher("oscar",
                Map.of("projet", paie.toString(), "technologie", "Oracle", "horizon", 30, "gravite", "HIGH"))));
        // Type : seulement les articles.
        assertEquals(List.of(article.toString()), ids(rechercher("oscar",
                Map.of("projet", paie.toString(), "horizon", 30, "type", "ARTICLE"))));
        // Profil : « developpeur » ne retient que les avis de sécurité, « expert-metier » aucun élément publié.
        assertFalse(ids(rechercher("oscar", Map.of("projet", paie.toString(), "profil", "developpeur"))).contains(article.toString()));
        assertEquals(0, rechercher("oscar", Map.of("profil", "expert-metier")).getInt("total"));
        // Mots et filtres combinés.
        assertEquals(List.of(attendu.toString()), ids(rechercher("oscar",
                Map.of("q", "Database", "projet", paie.toString(), "horizon", 30))));
    }

    /** RCH-03-S1 : un impact de l'espace B contenant « Portail » n'est pas renvoyé à un utilisateur du seul espace A. */
    @Test
    void rch03S1MotPresentDansUnAutreEspace() {
        UUID portail = jeu.projet(espaceMarc, "Portail");
        UUID signal = jeu.signal("GHSA-RCH3-0001", "Faille d'une bibliothèque de journalisation", "Sans le mot.", "HIGH",
                ilYA(2), null);
        jeu.impact(signal, portail);
        UUID sourceMarc = jeu.sourceEspace(espaceMarc, marc);
        UUID actualiteMarc = jeu.actualite(sourceMarc, "Refonte du Portail client", "Le Portail change.", null, ilYA(1));
        UUID personnelle = jeu.actualite(jeu.sourcePersonnelle(marc), "Portail personnel", "Note du Portail.", null, ilYA(1));

        JsonPath oscarVoit = rechercher("oscar", Map.of("q", "Portail"));
        assertEquals(0, oscarVoit.getInt("total"), oscarVoit.prettify());
        String brut = Api.enTantQue("oscar").queryParam("q", "Portail").get("/api/v1/recherche").asString();
        for (UUID invisible : List.of(signal, actualiteMarc, personnelle, portail)) {
            assertFalse(brut.contains(invisible.toString()), invisible.toString());
        }

        JsonPath marcVoit = rechercher("marc", Map.of("q", "Portail"));
        assertEquals(Set.of(signal.toString(), actualiteMarc.toString(), personnelle.toString()), Set.copyOf(ids(marcVoit)));
        assertTrue(marcVoit.getList("elements.projets.projet").toString().contains("Portail"));

        // Filtre sur un projet d'un autre espace : refus non révélé, journalisé.
        long refus = baseDeTest.count("SELECT count(*) FROM refus_acces WHERE motif = 'NON_MEMBRE'");
        Api.enTantQue("oscar").queryParam("projet", portail.toString()).get("/api/v1/recherche").then().statusCode(404)
                .body("code", equalTo("INTROUVABLE"));
        assertEquals(refus + 1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE motif = 'NON_MEMBRE'"));
    }

    /**
     * RCH-03 sous argos_app : même si un service transmettait à tort l'espace B, ni la sélection ni la
     * restitution n'exposent ses éléments ou ses projets à un non-membre.
     */
    @Test
    void rch03CloisonnementSousArgosApp() {
        UUID portail = jeu.projet(espaceMarc, "Portail");
        UUID signal = jeu.signal("GHSA-RCH3-0002", "Faille publique", "Texte public.", "HIGH", ilYA(2), null);
        jeu.impact(signal, portail);
        UUID actualiteMarc = jeu.actualite(jeu.sourceEspace(espaceMarc, marc), "Portail interne", "Texte.", null, ilYA(1));

        RechercheVeille.Resultats resultats = recherche.rechercher(new RechercheVeille.Criteres("Portail or publique", null, null,
                null, null, null, null, null), oscar, Set.of(espaceOscar, espaceMarc), 0, 20);
        assertEquals(List.of(signal), resultats.elements().stream().map(RechercheVeille.Element::id).toList());
        assertEquals(List.of(), resultats.elements().get(0).projets(), "projet d'un autre espace cité");

        List<UUID> selection = base.executer(new Contexte(oscar, null, null), connexion -> {
            List<UUID> trouves = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT current_user, id FROM argos_rechercher_veille(argos_requete_recherche('Portail'), NULL, NULL,
                        NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                    """); ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    assertEquals("argos_app", resultat.getString(1));
                    trouves.add(resultat.getObject(2, UUID.class));
                }
            }
            return trouves;
        });
        assertFalse(selection.contains(actualiteMarc));
        assertFalse(selection.contains(signal), "rattaché seulement par le nom d'un projet invisible");

        // Sans compte courant, la sélection ne renvoie rien.
        long sansContexte = base.executer(Contexte.aucun(), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT count(*) FROM argos_rechercher_veille(NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                    """); ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getLong(1);
            }
        });
        assertEquals(0, sansContexte);
    }

    /** RCH-04-S1 : une vue enregistrée rappelée applique sa recherche et ses filtres. */
    @Test
    void rch04S1VueRappelee() {
        UUID paie = jeu.projet(espaceOscar, "Paie");
        UUID attendu = jeu.signal("GHSA-RCH4-0001", "Oracle : faille de sécurité", "Correctif.", "HIGH", ilYA(3), null);
        UUID ecarte = jeu.signal("GHSA-RCH4-0002", "Oracle : faille hors projet", "Correctif.", "HIGH", ilYA(3), null);
        jeu.impact(attendu, paie);

        Map<String, Object> criteres = Map.of("texte", "Oracle", "projets", List.of(paie.toString()), "horizonJours", 30);
        String id = Api.enTantQue("oscar").contentType(ContentType.JSON)
                .body(Map.of("nom", "Sécurité de mes projets", "criteres", criteres))
                .post("/api/v1/vues").then().statusCode(201)
                .body("nom", equalTo("Sécurité de mes projets"))
                .body("criteres.texte", equalTo("Oracle"))
                .extract().path("id");

        Api.enTantQue("oscar").get("/api/v1/vues").then().statusCode(200).body("size()", equalTo(1)).body("[0].id", equalTo(id));
        Api.enTantQue("oscar").get("/api/v1/vues/" + id).then().statusCode(200).body("criteres.horizonJours", equalTo(30));
        JsonPath resultats = Api.enTantQue("oscar").get("/api/v1/vues/" + id + "/resultats").then().statusCode(200).extract().jsonPath();
        assertEquals(List.of(attendu.toString()), ids(resultats), resultats.prettify());
        assertFalse(ids(resultats).contains(ecarte.toString()));
        assertEquals(ids(rechercher("oscar", Map.of("q", "Oracle", "projet", paie.toString(), "horizon", 30))), ids(resultats));

        // Nom unique par utilisateur ; suppression par l'auteur.
        Api.enTantQue("oscar").contentType(ContentType.JSON).body(Map.of("nom", "Sécurité de mes projets", "criteres", criteres))
                .post("/api/v1/vues").then().statusCode(409).body("code", equalTo("VUE_EXISTANTE"));
        Api.enTantQue("oscar").delete("/api/v1/vues/" + id).then().statusCode(204);
        Api.enTantQue("oscar").get("/api/v1/vues/" + id).then().statusCode(404);
    }

    /** RCH-04-S2 : la vue d'un autre utilisateur est refusée, le refus journalisé ; elle reste intacte. */
    @Test
    void rch04S2VueDUnAutreUtilisateur() {
        String id = Api.enTantQue("oscar").contentType(ContentType.JSON)
                .body(Map.of("nom", "Ma veille", "criteres", Map.of("texte", "Oracle")))
                .post("/api/v1/vues").then().statusCode(201).extract().path("id");

        long avant = baseDeTest.count("SELECT count(*) FROM refus_acces WHERE motif = 'NON_MEMBRE' AND ressource LIKE '%/api/v1/vues/%'");
        String refusee = Api.enTantQue("marc").get("/api/v1/vues/" + id).then().statusCode(404)
                .body("code", equalTo("INTROUVABLE")).extract().asString();
        Api.enTantQue("marc").get("/api/v1/vues/" + id + "/resultats").then().statusCode(404);
        Api.enTantQue("marc").delete("/api/v1/vues/" + id).then().statusCode(404);
        assertEquals(avant + 3, baseDeTest.count(
                "SELECT count(*) FROM refus_acces WHERE motif = 'NON_MEMBRE' AND ressource LIKE '%/api/v1/vues/%'"));
        Api.enTantQue("marc").get("/api/v1/vues").then().statusCode(200).body("size()", equalTo(0));
        Api.enTantQue("oscar").get("/api/v1/vues/" + id).then().statusCode(200);

        // Une vue inexistante est seulement introuvable, sans refus journalisé.
        String inexistante = Api.enTantQue("marc").get("/api/v1/vues/" + UUID.randomUUID()).then().statusCode(404)
                .extract().asString();
        // Même réponse dans les deux cas : l'existence de la vue n'est pas révélée.
        assertEquals(refusee, inexistante);
        assertEquals(avant + 3, baseDeTest.count(
                "SELECT count(*) FROM refus_acces WHERE motif = 'NON_MEMBRE' AND ressource LIKE '%/api/v1/vues/%'"));

        // Sous argos_app, marc ne lit ni ne supprime la vue d'oscar.
        long visibles = base.executer(new Contexte(marc, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM workspace_vue WHERE id = ?::uuid")) {
                requete.setString(1, id);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("SELECT count(*) FROM workspace_vue");
                    ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getLong(1);
            }
        });
        assertEquals(0, visibles);
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM workspace_vue WHERE id = '" + id + "'"));
    }

    /**
     * Caractérisation : un horizon non entier n'est pas converti en {@code Integer} par RESTEasy Reactive ; la réponse
     * est figée telle qu'observée avant le regroupement des paramètres dans {@link ParametresRecherche}.
     */
    @Test
    void horizonNonEntier() {
        Api.enTantQue("oscar").queryParam("horizon", "abc").get("/api/v1/recherche").then().statusCode(STATUT_HORIZON_NON_ENTIER);
    }

    /** Entrées non fiables : filtres refusés, saisie jamais interprétée comme SQL, liens non http écartés. */
    @Test
    void entreesNonFiables() {
        Api.enTantQue("oscar").queryParam("type", "INCONNU").get("/api/v1/recherche").then().statusCode(400)
                .body("code", equalTo("CRITERE_INVALIDE")).body("attribut", equalTo("types"));
        Api.enTantQue("oscar").queryParam("gravite", "EXTREME").get("/api/v1/recherche").then().statusCode(400);
        Api.enTantQue("oscar").queryParam("horizon", 0).get("/api/v1/recherche").then().statusCode(400);
        Api.enTantQue("oscar").queryParam("projet", "pas-un-uuid").get("/api/v1/recherche").then().statusCode(400);
        Api.enTantQue("oscar").queryParam("profil", "inconnu").get("/api/v1/recherche").then().statusCode(400);
        Api.enTantQue("oscar").queryParam("q", "x".repeat(501)).get("/api/v1/recherche").then().statusCode(400);
        Api.enTantQue("oscar").queryParam("taille", 1000).get("/api/v1/recherche").then().statusCode(400);
        Api.enTantQue("oscar").contentType(ContentType.JSON).body(Map.of("nom", " ", "criteres", Map.of()))
                .post("/api/v1/vues").then().statusCode(400);

        UUID signal = jeu.signal("GHSA-RCH-SQL", "Oracle", "Texte.", "LOW", ilYA(1), null);
        for (String saisie : List.of("'; DROP TABLE signal; --", "\" or 1=1 --", "a & | ! ( ) : *", "\\")) {
            Api.enTantQue("oscar").queryParam("q", saisie).get("/api/v1/recherche").then().statusCode(200);
        }
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal WHERE id = '" + signal + "'"));

        UUID source = jeu.sourceEspace(espaceOscar, oscar);
        jeu.actualite(source, "Lien piégé", "Texte.", "javascript:alert(1)", ilYA(1));
        JsonPath resultats = rechercher("oscar", Map.of("q", "piégé"));
        assertEquals(1, resultats.getInt("total"));
        assertNull(resultats.get("elements[0].lien"));
    }

    /** Pagination : pages successives sans doublon, total constant. */
    @Test
    void pagination() {
        for (int rang = 0; rang < 5; rang++) {
            jeu.signal("GHSA-RCH-PAGE-" + rang, "Oracle faille " + rang, "Correctif.", "LOW", ilYA(rang + 1), null);
        }
        List<String> vus = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonPath resultats = rechercher("oscar", Map.of("q", "Oracle", "taille", 2, "page", page));
            assertEquals(5, resultats.getInt("total"));
            vus.addAll(ids(resultats));
        }
        assertEquals(5, Set.copyOf(vus).size());
        assertEquals(5, rechercher("oscar", Map.of("q", "Oracle", "taille", 2, "page", 9)).getInt("total"));
    }
}
