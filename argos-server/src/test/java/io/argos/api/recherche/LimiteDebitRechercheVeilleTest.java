package io.argos.api.recherche;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * RCH-06 et PRF-08 : limites de débit par compte de la recherche (compteur commun avec le rappel d'une vue) et de
 * « Ma veille » (compteur distinct), avec des limites basses de test. La fenêtre glissante est éprouvée avec une
 * horloge injectée par {@link LimiteRechercheTest} et {@code LimiteurDebitTest}.
 */
@QuarkusTest
@TestProfile(LimiteDebitRechercheVeilleTest.LimitesBasses.class)
class LimiteDebitRechercheVeilleTest {

    private static final String MESSAGE_LIMITE = "Nombre maximal de requêtes par minute atteint, réessayez plus tard.";
    private static final int LIMITE_RECHERCHE = 3;
    private static final int LIMITE_VEILLE = 2;

    /** Trois recherches et deux lectures de « Ma veille » par minute (60 chacune en exploitation). */
    public static class LimitesBasses implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("argos.recherche.debit.requetes-par-minute", String.valueOf(LIMITE_RECHERCHE),
                    "argos.veille.debit.requetes-par-minute", String.valueOf(LIMITE_VEILLE));
        }
    }

    @Inject
    TestDb baseDeTest;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
    }

    private static ValidatableResponse rechercher(String utilisateur) {
        return Api.enTantQue(utilisateur).queryParam("texte", "faille").when().get("/api/v1/recherche").then();
    }

    private static ValidatableResponse maVeille(String utilisateur) {
        return Api.enTantQue(utilisateur).when().get("/api/v1/me/veille").then();
    }

    private static ValidatableResponse resultatsDeVue(String utilisateur, String vue) {
        return Api.enTantQue(utilisateur).when().get("/api/v1/vues/" + vue + "/resultats").then();
    }

    private static String creerVue(String utilisateur) {
        return Api.enTantQue(utilisateur).contentType(ContentType.JSON)
                .body(Map.of("nom", "Vue de test", "criteres", Map.of("texte", "faille")))
                .when().post("/api/v1/vues").then().statusCode(201).extract().path("id");
    }

    private static void refusLimite(ValidatableResponse reponse) {
        reponse.statusCode(429)
                .header("Retry-After", "60")
                .body("code", equalTo("LIMITE_DEBIT"))
                .body("message", equalTo(MESSAGE_LIMITE))
                .body("$", not(hasKey("elements")))
                .body("$", not(hasKey("resultats")));
    }

    private long refus(UUID compte, String ressource) {
        return baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + compte
                + "' AND motif = 'LIMITE_DEBIT' AND ressource = '" + ressource + "'");
    }

    /**
     * RCH-06-S1 et RCH-06-S2 : limite atteinte sur la recherche, 429 avec délai, refus journalisé, aucun résultat ;
     * le rappel d'une vue enregistrée partage le compteur ; un autre compte n'est pas affecté. Chaque test emploie ses
     * propres utilisateurs : les compteurs vivent aussi longtemps que l'application de test.
     */
    @Test
    void rch06RechercheEtRappelDeVueLimitesParCompte() {
        UUID nina = Api.compte("nina");
        String vue = creerVue("nina");
        rechercher("nina").statusCode(200);
        rechercher("nina").statusCode(200);
        resultatsDeVue("nina", vue).statusCode(200);

        refusLimite(resultatsDeVue("nina", vue));
        refusLimite(rechercher("nina"));
        assertEquals(1, refus(nina, "GET /api/v1/vues/" + vue + "/resultats"));
        assertEquals(1, refus(nina, "GET /api/v1/recherche"));
        Api.enTantQue("nina").when().get("/api/v1/vues/" + vue).then().statusCode(200);

        Api.assurerCompte("oscar");
        rechercher("oscar").statusCode(200);
    }

    /** PRF-08-S1 et PRF-08-S2 : limite atteinte sur « Ma veille », refus journalisé, un autre compte non affecté. */
    @Test
    void prf08MaVeilleLimiteeParCompte() {
        UUID karim = Api.compte("karim");
        for (int appel = 0; appel < LIMITE_VEILLE; appel++) {
            maVeille("karim").statusCode(200);
        }
        refusLimite(maVeille("karim"));
        assertEquals(1, refus(karim, "GET /api/v1/me/veille"));

        Api.assurerCompte("jules");
        maVeille("jules").statusCode(200);
    }

    /** PRF-08-S3 : les compteurs de la recherche et de « Ma veille » sont indépendants. */
    @Test
    void prf08S3CompteursIndependants() {
        Api.compte("ines");
        for (int appel = 0; appel < LIMITE_VEILLE; appel++) {
            maVeille("ines").statusCode(200);
        }
        refusLimite(maVeille("ines"));
        for (int appel = 0; appel < LIMITE_RECHERCHE; appel++) {
            rechercher("ines").statusCode(200);
        }
        refusLimite(rechercher("ines"));
        refusLimite(maVeille("ines"));
    }
}
