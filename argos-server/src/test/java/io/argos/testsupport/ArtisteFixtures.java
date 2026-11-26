package io.argos.testsupport;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.restassured.http.ContentType;

/**
 * Jeu d'essai du profil {@code artiste} : réponses simulées de l'API data.culture.gouv.fr et d'un flux
 * RSS servies par {@link StubServer}, déclaration et vérification des sources par l'administrateur
 * plateforme ({@code alice}). Aucune requête ne quitte la machine.
 */
public final class ArtisteFixtures {

    /** Chemin simulé d'un jeu de données (API Opendatasoft explore v2.1). */
    public static final String JEU = "/culture/api/explore/v2.1/catalog/datasets/appels-a-projets/records";

    private ArtisteFixtures() {
    }

    /**
     * Retire les préférences des utilisateurs donnés, conservées par {@link TestDb#reset()} ; les données du
     * profil (opportunités, sources, suivis et rappels) sont vidées par la remise à zéro.
     */
    public static void reinitialiser(TestDb baseDeTest, String... utilisateurs) {
        for (String utilisateur : utilisateurs) {
            baseDeTest.exec("DELETE FROM workspace_preference_utilisateur WHERE compte_id = '" + Api.compte(utilisateur) + "'");
        }
    }

    public static void activer(String utilisateur, String... profils) {
        Api.enTantQue(utilisateur).contentType(ContentType.JSON).body(Map.of("profilsActifs", List.of(profils)))
                .when().put("/api/v1/preferences/profils").then().statusCode(200);
    }

    public static void suivre(String utilisateur, List<String> disciplines, List<String> territoires) {
        Api.enTantQue(utilisateur).contentType(ContentType.JSON)
                .body(Map.of("disciplines", disciplines, "territoires", territoires))
                .when().put("/api/v1/profils/artiste/suivis").then().statusCode(200);
    }

    /** Enregistrement d'un jeu de données data.culture.gouv.fr. */
    public static String enregistrement(String id, String titre, String discipline, String region, LocalDate limite) {
        return """
                {"identifiant": "%s", "titre": "%s", "organisme": "DRAC", "criteres": "Artistes professionnels résidant en France.",
                 "lien": "https://www.culture.gouv.fr/%s", "discipline": "%s", "region": "%s", "date_limite": %s}"""
                .formatted(id, titre, id, discipline, region, limite == null ? "null" : "\"" + limite + "\"");
    }

    public static String reponse(String... enregistrements) {
        return "{\"total_count\": " + enregistrements.length + ", \"results\": [" + String.join(",", enregistrements) + "]}";
    }

    /** Déclare une source (administrateur plateforme) et renvoie son identifiant. */
    public static String declarer(String type, String adresse, String rubrique, List<String> disciplines,
            List<String> territoires) {
        return Api.enTantQue("alice").contentType(ContentType.JSON)
                .body(Map.of("type", type, "adresse", adresse, "libelle", "Source " + type, "rubrique", rubrique,
                        "disciplines", disciplines, "territoires", territoires))
                .when().post("/api/v1/profils/artiste/sources")
                .then().statusCode(201).extract().path("id");
    }

    /** Enregistre les conditions d'usage vérifiées d'une source (administrateur plateforme). */
    public static void verifier(String source) {
        Api.enTantQue("alice").contentType(ContentType.JSON)
                .body(Map.of("licence", "Licence Ouverte Etalab 2.0",
                        "conditionsUsage", "Réutilisation libre avec mention de la source.",
                        "conditionsUrl", "https://www.etalab.gouv.fr/licence-ouverte-open-licence/"))
                .when().put("/api/v1/profils/artiste/sources/" + source + "/conditions-usage")
                .then().statusCode(200);
    }

    /** Lance la collecte du profil (administrateur plateforme). */
    public static void collecter() {
        Api.enTantQue("alice").when().post("/api/v1/profils/artiste/collectes").then().statusCode(200);
    }
}
