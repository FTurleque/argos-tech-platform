package io.argos.observabilite;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.testsupport.Api;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * Journaux structurés et corrélés (EXP-13, AUD-DEP-07) : chaque ligne du journal JSON est un objet valide ; les
 * événements d'une requête partagent l'identifiant restitué dans {@code X-Request-Id}, distinct de celui d'une requête
 * concurrente ; une exécution planifiée a son propre identifiant et le nom de sa tâche ; une erreur porte sa pile et
 * sa cause ; aucun jeton n'est écrit.
 */
@QuarkusTest
@TestProfile(ProfilObservabilite.class)
class JournauxJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int REQUETES_CONCURRENTES = 6;
    private static final Duration ATTENTE_MAX = Duration.ofSeconds(20);
    private static final String RESSOURCE = "/api/v1/essai-observabilite";

    @Inject
    TacheEssai tache;

    @Test
    void exp13S1EvenementsDUneRequeteCorreles() throws InterruptedException, ExecutionException {
        String jeton = Api.jeton("alice");
        ExecutorService appelants = Executors.newFixedThreadPool(REQUETES_CONCURRENTES);
        try {
            List<Future<Appel>> appels = new ArrayList<>();
            for (int rang = 0; rang < REQUETES_CONCURRENTES; rang++) {
                String marqueur = "marqueur-" + UUID.randomUUID();
                Callable<Appel> appel = () -> new Appel(marqueur, given().auth().oauth2(jeton)
                        .queryParam("marqueur", marqueur).when().get(RESSOURCE + "/etapes"));
                appels.add(appelants.submit(appel));
            }
            Set<String> identifiants = new HashSet<>();
            for (Future<Appel> resultat : appels) {
                Appel appel = resultat.get();
                assertEquals(200, appel.reponse().statusCode());
                String identifiant = appel.reponse().header(Correlation.EN_TETE_REPONSE);
                List<JsonNode> evenements = attendreEvenements(ligne -> ligne.path("message").asText()
                        .contains(appel.marqueur()), 2);
                assertEquals(2, evenements.size(), "deux événements par requête");
                evenements.forEach(evenement -> assertEquals(identifiant,
                        evenement.path("mdc").path(Correlation.CLE_CORRELATION).asText(), "identifiant de la requête"));
                identifiants.add(identifiant);
            }
            assertEquals(REQUETES_CONCURRENTES, identifiants.size(), "un identifiant distinct par requête concurrente");
        } finally {
            appelants.shutdownNow();
        }
    }

    @Test
    void exp13S2ExecutionPlanifieeCorrelee() {
        tache.executer();
        tache.executer();
        List<JsonNode> executions = attendreEvenements(ligne -> TacheEssai.MESSAGE.equals(ligne.path("message").asText())
                && ligne.path("mdc").has(Correlation.CLE_CORRELATION), 2);
        Set<String> identifiants = executions.stream()
                .map(evenement -> evenement.path("mdc").path(Correlation.CLE_CORRELATION).asText())
                .collect(Collectors.toSet());
        assertEquals(executions.size(), identifiants.size(), "un identifiant par exécution");
        executions.forEach(evenement -> assertEquals(TacheEssai.IDENTITE,
                evenement.path("mdc").path(Correlation.CLE_TACHE).asText(), "nom de la tâche"));
        assertFalse(identifiants.contains(""), "identifiant présent");
    }

    @Test
    void exp13S3ErreurAvecSaCauseEtSaPile() {
        String marqueur = "marqueur-" + UUID.randomUUID();
        String identifiant = Api.enTantQue("alice").queryParam("marqueur", marqueur).when().get(RESSOURCE + "/erreur")
                .then().statusCode(200).extract().header(Correlation.EN_TETE_REPONSE);
        JsonNode evenement = attendreEvenements(ligne -> ligne.path("message").asText().contains(marqueur), 1).get(0);
        assertEquals("ERROR", evenement.path("level").asText());
        assertEquals(identifiant, evenement.path("mdc").path(Correlation.CLE_CORRELATION).asText());
        JsonNode exception = evenement.path("exception");
        assertEquals(IllegalStateException.class.getName(), exception.path("exceptionType").asText());
        assertTrue(exception.path("frames").size() > 0, "pile de l'exception");
        String texte = exception.toString();
        assertTrue(texte.contains(UncheckedIOException.class.getName()) && texte.contains("cause simulée " + marqueur),
                "causes de l'exception : " + texte);
    }

    @Test
    void exp13S4JetonAbsentDesJournaux() {
        String jeton = Api.jeton("alice");
        String marqueur = "marqueur-" + UUID.randomUUID();
        given().auth().oauth2(jeton).queryParam("marqueur", marqueur)
                .when().get(RESSOURCE + "/erreur").then().statusCode(200);
        given().auth().oauth2(jeton + "altere").queryParam("marqueur", marqueur)
                .when().get(RESSOURCE + "/etapes").then().statusCode(401);
        attendreEvenements(ligne -> ligne.path("message").asText().contains(marqueur), 1);
        String journal = lireJournal();
        assertFalse(journal.contains(jeton.substring(jeton.lastIndexOf('.') + 1)), "signature du jeton absente");
        assertFalse(journal.contains(jeton), "jeton absent");
    }

    @Test
    void chaqueLigneEstUnObjetJson() {
        Api.enTantQue("alice").queryParam("marqueur", "lignes").when().get(RESSOURCE + "/etapes").then().statusCode(200);
        List<String> lignes = lireJournal().lines().filter(ligne -> !ligne.isBlank()).toList();
        assertFalse(lignes.isEmpty());
        for (String ligne : lignes) {
            assertTrue(analyser(ligne).isObject(), "objet JSON : " + ligne);
        }
    }

    @Test
    void lIdentifiantRecuNEstPasRepris() {
        String impose = "identifiant-impose-par-l-appelant";
        String identifiant = Api.enTantQue("alice").header(Correlation.EN_TETE_REPONSE, impose)
                .queryParam("marqueur", "impose").when().get(RESSOURCE + "/etapes")
                .then().statusCode(200).extract().header(Correlation.EN_TETE_REPONSE);
        assertNotEquals(impose, identifiant);
    }

    private record Appel(String marqueur, Response reponse) {
    }

    /** Événements du journal satisfaisant le critère, attendus jusqu'à en compter {@code minimum}. */
    private static List<JsonNode> attendreEvenements(Predicate<JsonNode> critere, int minimum) {
        return Awaitility.await().atMost(ATTENTE_MAX).until(() -> lireJournal().lines()
                .filter(ligne -> !ligne.isBlank()).map(JournauxJsonTest::analyser).filter(critere).toList(),
                evenements -> evenements.size() >= minimum);
    }

    private static String lireJournal() {
        try {
            Path journal = Path.of(ProfilObservabilite.JOURNAL);
            return Files.exists(journal) ? Files.readString(journal) : "";
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    private static JsonNode analyser(String ligne) {
        try {
            return JSON.readTree(ligne);
        } catch (JsonProcessingException erreur) {
            throw new AssertionError("ligne de journal non JSON : " + ligne, erreur);
        }
    }
}
