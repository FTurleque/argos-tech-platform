package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * REG-09 : la demande d'évolution est contrôlée avant tout appel à la forge. Le serveur de forge simulé compte les
 * requêtes qu'il reçoit : un appelant non membre, un membre qui n'est pas l'expert et une fiche qui n'est pas à l'état
 * « impact produit » n'en provoquent aucune ; l'expert autorisé obtient les réponses de REG-08.
 *
 * <p>Utilisateurs : hugo administre l'espace ; ines (contributrice) est experte du domaine ; jules (lecteur) n'est pas
 * expert ; karim n'est pas membre.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class DemandeEvolutionControleeTest {

    private static final String TICKET_EXISTANT = "https://github.com/argos/app/issues/42";
    private static final String TICKET_INEXISTANT = "https://github.com/argos/app/issues/404";
    private static final String PREFIXE_FORGE = "/github";

    @Inject
    TestDb baseDeTest;

    @Inject
    ReglementaireFixtures reg;

    private UUID espace;
    private UUID ines;
    private String urlFiche;
    private UUID fiche;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        StubServer.reset();
        espace = Api.nouvelEspace("hugo", "Espace réglementaire");
        Api.ajouterMembre("hugo", espace.toString(), "ines", "CONTRIBUTEUR");
        Api.ajouterMembre("hugo", espace.toString(), "jules", "LECTEUR");
        Api.assurerCompte("karim");
        ines = Api.compte("ines");
        reg.domaine(espace, "carriere", "Carrière", Api.compte("hugo"));
        reg.expert(espace, "carriere", ines);
        UUID texte = reg.texte("JORFTEXT000099300001", "Décret de test validé", LocalDate.of(2026, 11, 2),
                LocalDate.of(2027, 1, 1));
        fiche = reg.fiche(texte, espace, "carriere", "VALIDEE", "Résumé relu", ines);
        urlFiche = "/api/v1/reglementaire/fiches/" + fiche;
        StubServer.programmer("/github/repos/argos/app/issues/42", Stub.succes("{\"number\": 42, \"state\": \"open\"}"));
    }

    private void passerEnImpactProduit() {
        Api.enTantQue("ines").contentType(ContentType.JSON).body(Map.of("etat", "IMPACT_PRODUIT"))
                .when().post(urlFiche + "/transitions").then().statusCode(200);
    }

    private static ValidatableResponse associer(String utilisateur, String url, String adresse) {
        return Api.enTantQue(utilisateur).contentType(ContentType.JSON).body(Map.of("adresse", adresse))
                .when().put(url + "/demande-evolution").then();
    }

    private static int requetesVersLaForge() {
        return StubServer.received(PREFIXE_FORGE).size();
    }

    private String demandeEnregistree() {
        return baseDeTest.string("SELECT demande_url FROM fiche_reglementaire WHERE id = ?", fiche);
    }

    /** REG-09-S1 : un non-membre reçoit la réponse d'une fiche inexistante, quel que soit le ticket, sans appel à la forge. */
    @Test
    void reg09S1NonMembreSansRequeteVersLaForge() {
        passerEnImpactProduit();
        String inexistante = "/api/v1/reglementaire/fiches/" + UUID.randomUUID();
        String reponseFicheInexistante = associer("karim", inexistante, TICKET_EXISTANT).statusCode(404)
                .extract().asString();

        String reponseTicketExistant = associer("karim", urlFiche, TICKET_EXISTANT).statusCode(404).extract().asString();
        String reponseTicketInexistant = associer("karim", urlFiche, TICKET_INEXISTANT).statusCode(404).extract().asString();

        assertEquals(reponseFicheInexistante, reponseTicketExistant);
        assertEquals(reponseFicheInexistante, reponseTicketInexistant);
        assertEquals(0, requetesVersLaForge(), "aucune requête envoyée à la forge");
        assertNull(demandeEnregistree(), "la fiche n'est pas modifiée");
    }

    /** REG-09-S2 : un membre qui n'est pas l'expert est refusé pour rôle insuffisant, refus journalisé, sans appel. */
    @Test
    void reg09S2MembreNonExpertSansRequeteVersLaForge() {
        passerEnImpactProduit();
        for (String adresse : List.of(TICKET_EXISTANT, TICKET_INEXISTANT)) {
            associer("jules", urlFiche, adresse).statusCode(403).body("code", equalTo("INTERDIT"));
        }
        assertEquals(0, requetesVersLaForge(), "aucune requête envoyée à la forge");
        UUID jules = Api.compte("jules");
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = ?"
                + " AND motif = 'ROLE_INSUFFISANT' AND ressource = ?", jules,
                "PUT /api/v1/reglementaire/fiches/" + fiche + "/demande-evolution"));
        assertNull(demandeEnregistree(), "la fiche n'est pas modifiée");
    }

    /** REG-09-S3 : avant « impact produit », la demande est refusée (409) sans appel à la forge. */
    @Test
    void reg09S3FicheHorsImpactProduitSansRequeteVersLaForge() {
        for (String adresse : List.of(TICKET_EXISTANT, TICKET_INEXISTANT)) {
            associer("ines", urlFiche, adresse).statusCode(409).body("code", equalTo("IMPACT_PRODUIT_REQUIS"));
        }
        assertEquals(0, requetesVersLaForge(), "aucune requête envoyée à la forge");
    }

    /** REG-09-S4 : l'expert autorisé obtient DEMANDE_INTROUVABLE, puis l'association historisée avec l'état lu. */
    @Test
    void reg09S4ExpertAutorise() {
        passerEnImpactProduit();
        associer("ines", urlFiche, TICKET_INEXISTANT).statusCode(400).body("code", equalTo("DEMANDE_INTROUVABLE"));
        assertEquals(1, requetesVersLaForge(), "la forge est interrogée pour l'expert autorisé");

        associer("ines", urlFiche, TICKET_EXISTANT).statusCode(200)
                .body("demande.adresse", equalTo(TICKET_EXISTANT))
                .body("demande.forge", equalTo("GITHUB"))
                .body("demande.etat", equalTo("open"))
                .body("historique.action", hasItem("DEMANDE_EVOLUTION"));
        assertEquals(2, requetesVersLaForge());
    }
}
