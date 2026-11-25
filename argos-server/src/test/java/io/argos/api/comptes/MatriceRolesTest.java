package io.argos.api.comptes;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.inventory.ProjectDirectory;
import io.argos.testsupport.Api;
import io.argos.testsupport.ArtisteFixtures;
import io.argos.testsupport.ReglementaireFixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Test matriciel point d'entrée × rôle (AUD-TST-05, design.md D5, invariant I-3) : chaque opération du document
 * OpenAPI ({@code /q/openapi}) est appelée par chaque rôle et le code reçu est comparé à celui de la matrice
 * versionnée {@code securite/matrice-roles.tsv}. Une opération absente de la matrice, ou une ligne de la matrice sans
 * opération, fait échouer le test : toute nouvelle opération force à décider de ses droits.
 *
 * <p>Rôles : {@code ANONYME} (aucun jeton) ; {@code LECTEUR} (jules, lecteur de l'espace E) ; {@code ADMIN_ESPACE}
 * (hugo, administrateur de E) ; {@code EXPLOITANT} (alice, administratrice plateforme, membre d'aucun des espaces
 * de la matrice : aucun accès implicite aux espaces) ; {@code AUTRE_ESPACE} (karim, administrateur d'un autre espace F) ;
 * {@code EXPERT} (ines, contributrice de E et experte du domaine de la fiche). Les administrateurs de la plateforme
 * sont les exploitants (arc42 §8.1).
 *
 * <p>Les opérations à effet sont appelées sur des ressources inexistantes (ou avec un corps vide, refusé) pour qu'un
 * appel autorisé ne modifie pas les ressources de la matrice ; les opérations qui n'atteignent pas leur contrôle de
 * droits sur une ressource inexistante (404 pour tous) sont appelées sur une ressource réelle. La demande d'évolution
 * est appelée avec un ticket valide : le serveur de forge simulé ne reçoit que la requête de l'experte, seule
 * autorisée (REG-09-S1, REG-09-S2).
 *
 * <p>Pour regénérer la matrice après une évolution voulue des droits :
 * {@code mvn -f argos-server/pom.xml test -Dtest=MatriceRolesTest -Dargos.matrice.ecrire=true}, puis relire le
 * fichier {@code target/matrice-roles-observee.tsv} cellule par cellule avant de le copier.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class MatriceRolesTest {

    private static final String RESSOURCE_MATRICE = "/securite/matrice-roles.tsv";
    private static final String PROPRIETE_ECRITURE = "argos.matrice.ecrire";
    private static final String SORTIE_OBSERVEE = "target/matrice-roles-observee.tsv";
    private static final String SEPARATEUR = "\t";
    private static final String ANONYME = "ANONYME";
    private static final int PREMIERE_ERREUR_SERVEUR = 500;
    private static final int DELAI_REPONSE_MS = 30_000;
    private static final String PREFIXE_FORGE = "/github";
    private static final String TICKET = "{\"adresse\":\"https://github.com/argos/app/issues/42\"}";
    private static final String CORPS_VIDE = "{}";
    private static final UUID RESSOURCE_INCONNUE = UUID.fromString("00000000-0000-0000-0000-00000000dead");

    /** Utilisateur de test de chaque rôle ; {@code ANONYME} n'en a pas. */
    private static final Map<String, String> UTILISATEURS = Map.of("LECTEUR", "jules", "ADMIN_ESPACE", "hugo",
            "EXPLOITANT", "alice", "AUTRE_ESPACE", "karim", "EXPERT", "ines");

    /**
     * Opérations dont le corps n'est pas vide : sans lui, la validation précéderait le contrôle des droits. Les corps
     * choisis n'ont aucun effet pour un appel autorisé (ressource inconnue, transition ou décision refusée par l'état de
     * la fiche), sauf l'invitation, que seul l'administrateur de l'espace crée.
     */
    private static final Map<String, String> CORPS = Map.of(
            "PUT /api/v1/reglementaire/fiches/{id}/demande-evolution", TICKET,
            "POST /api/v1/espaces/{espaceId}/invitations", "{\"email\":\"invite-matrice@argos.test\",\"role\":\"LECTEUR\"}",
            "PUT /api/v1/espaces/{espaceId}/membres/{compteId}/role", "{\"role\":\"LECTEUR\"}",
            "POST /api/v1/reglementaire/fiches/{id}/transitions", "{\"etat\":\"NOUVEAU\"}",
            "POST /api/v1/reglementaire/fiches/{id}/rejet", "{\"motif\":\"Rejet de la matrice\"}");

    /** Ressources techniques des tests d'observabilité : absentes de l'application livrée, hors matrice. */
    private static final String PREFIXE_RESSOURCES_DE_TEST = " /api/v1/essai-observabilite/";

    /** Ressource réelle de la matrice substituée à {@code {id}} pour les chemins qui la désignent. */
    private static final String PREFIXE_FICHES = "/api/v1/reglementaire/fiches/";
    private static final String PREFIXE_SOURCES_OPS = "/api/v1/ops/sources/";
    private static final String SOURCE_INCONNUE = "INCONNUE";
    private static final String DOMAINE_INCONNU = "zz-inconnu";
    private static final String PROFIL_DE_DEPART = "developpeur";

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projets;

    @Inject
    ReglementaireFixtures reg;

    /**
     * Ressources de la matrice, créées une seule fois pour toute la classe. Elles ne le sont pas dans un
     * {@code @BeforeAll} : les appels HTTP ont besoin du port de l'application, que Quarkus ne configure
     * qu'avant chaque test.
     */
    private static final Map<String, String> jetons = new LinkedHashMap<>();
    private static List<String> roles;
    private static Map<String, Map<String, Integer>> attendu;
    private static UUID espace;
    private static UUID projet;
    private static UUID fiche;
    private static boolean ressourcesCreees;

    @BeforeEach
    void preparerLesRessources() throws IOException {
        if (ressourcesCreees) {
            return;
        }
        ressourcesCreees = true;
        lireMatrice();
        baseDeTest.reset();
        StubServer.reset();
        Api.compte("alice");
        espace = Api.nouvelEspace("hugo", "Espace E de la matrice");
        Api.ajouterMembre("hugo", espace.toString(), "jules", "LECTEUR");
        Api.ajouterMembre("hugo", espace.toString(), "ines", "CONTRIBUTEUR");
        Api.nouvelEspace("karim", "Espace F de la matrice");
        projet = projets.create(espace, "application de la matrice", null, null);
        UUID hugo = Api.compte("hugo");
        UUID ines = Api.compte("ines");
        reg.domaine(espace, "carriere", "Carrière", hugo);
        reg.expert(espace, "carriere", ines);
        UUID texte = reg.texte("JORFTEXT000099400001", "Décret de la matrice", LocalDate.of(2026, 11, 2),
                LocalDate.of(2027, 1, 1));
        fiche = reg.fiche(texte, espace, "carriere", "VALIDEE", "Résumé relu", ines);
        baseDeTest.exec("UPDATE fiche_reglementaire SET etat_suivi = 'IMPACT_PRODUIT' WHERE id = '" + fiche + "'");
        for (String utilisateur : UTILISATEURS.values()) {
            jetons.put(utilisateur, Api.jeton(utilisateur));
            // Les préférences d'un compte survivent à TestDb.reset() : le profil de départ rend les cellules déterministes.
            ArtisteFixtures.activer(utilisateur, PROFIL_DE_DEPART);
        }
    }

    /**
     * Les comptes de test sont partagés par toutes les classes de test : les espaces et appartenances de la matrice
     * sont retirés pour que les parcours qui supposent un compte sans espace (ParcoursComptesEtAccesTest) passent
     * quel que soit l'ordre d'exécution.
     */
    @AfterAll
    static void retirerLesEspacesDeLaMatrice() {
        TestDb base = Arc.container().instance(TestDb.class).get();
        base.reset();
        base.exec("DELETE FROM invitation WHERE espace_id = ANY (SELECT id FROM espace WHERE nom LIKE '% de la matrice')");
        base.exec("DELETE FROM appartenance WHERE espace_id = ANY (SELECT id FROM espace WHERE nom LIKE '% de la matrice')");
        base.exec("DELETE FROM espace WHERE nom LIKE '% de la matrice'");
        ressourcesCreees = false;
    }

    /** Lit la matrice : en-tête des rôles, puis une ligne par opération (opération, un code par rôle). */
    private static void lireMatrice() throws IOException {
        attendu = new LinkedHashMap<>();
        try (InputStream flux = MatriceRolesTest.class.getResourceAsStream(RESSOURCE_MATRICE)) {
            assertTrue(flux != null, "matrice des rôles absente : " + RESSOURCE_MATRICE);
            List<String> lignes = new String(flux.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(ligne -> !ligne.isBlank() && !ligne.startsWith("#")).toList();
            String[] entete = lignes.get(0).split(SEPARATEUR, -1);
            roles = List.of(entete).subList(1, entete.length);
            for (String ligne : lignes.subList(1, lignes.size())) {
                String[] cellules = ligne.split(SEPARATEUR, -1);
                assertEquals(entete.length, cellules.length, "colonnes de la ligne : " + ligne);
                Map<String, Integer> codes = new LinkedHashMap<>();
                for (int rang = 1; rang < cellules.length; rang++) {
                    codes.put(entete[rang], Integer.valueOf(cellules[rang].strip()));
                }
                attendu.put(cellules[0], codes);
            }
        }
    }

    /** Opérations du document OpenAPI, dans l'ordre du document : « MÉTHODE /chemin ». */
    private Set<String> operationsOpenApi() throws IOException {
        String json = given().auth().oauth2(jetons.get("alice")).when().get("/q/openapi?format=json")
                .then().statusCode(200).extract().asString();
        JsonNode chemins = new ObjectMapper().readTree(json).get("paths");
        Set<String> operations = new LinkedHashSet<>();
        chemins.fields().forEachRemaining(chemin -> chemin.getValue().fieldNames()
                .forEachRemaining(methode -> operations.add(methode.toUpperCase() + " " + chemin.getKey())));
        operations.removeIf(operation -> operation.contains(PREFIXE_RESSOURCES_DE_TEST));
        return operations;
    }

    /** I-3 : toute opération d'OpenAPI figure dans la matrice, et la matrice ne décrit aucune opération disparue. */
    @Test
    void touteOperationFigureDansLaMatrice() throws IOException {
        Set<String> openApi = operationsOpenApi();
        Set<String> manquantes = new LinkedHashSet<>(openApi);
        manquantes.removeAll(attendu.keySet());
        Set<String> disparues = new LinkedHashSet<>(attendu.keySet());
        disparues.removeAll(openApi);
        assertTrue(manquantes.isEmpty(), "opérations absentes de " + RESSOURCE_MATRICE + " : " + manquantes);
        assertTrue(disparues.isEmpty(), "lignes de " + RESSOURCE_MATRICE + " sans opération : " + disparues);
    }

    /** Une cellule par opération et par rôle ; l'écriture de la matrice observée est un mode de maintenance. */
    @TestFactory
    Stream<DynamicTest> chaqueOperationPourChaqueRole() throws IOException {
        Set<String> operations = Boolean.getBoolean(PROPRIETE_ECRITURE) ? operationsOpenApi() : attendu.keySet();
        List<String> observees = new ArrayList<>();
        observees.add("operation" + SEPARATEUR + String.join(SEPARATEUR, roles));
        List<DynamicTest> tests = new ArrayList<>();
        for (String operation : operations) {
            tests.add(DynamicTest.dynamicTest(operation, () -> {
                Map<String, Integer> codes = new LinkedHashMap<>();
                for (String role : roles) {
                    codes.put(role, appeler(operation, role));
                }
                observees.add(operation + SEPARATEUR + String.join(SEPARATEUR,
                        codes.values().stream().map(String::valueOf).toList()));
                Map<String, Integer> prevus = attendu.get(operation);
                if (prevus != null) {
                    assertEquals(prevus, codes, operation);
                }
                codes.values().forEach(code -> assertTrue(code < PREMIERE_ERREUR_SERVEUR, operation + " : erreur serveur"));
            }));
        }
        tests.add(DynamicTest.dynamicTest("matrice observée", () -> {
            if (Boolean.getBoolean(PROPRIETE_ECRITURE)) {
                Files.writeString(Path.of(SORTIE_OBSERVEE), String.join("\n", observees) + "\n");
            }
        }));
        return tests.stream();
    }

    /** REG-09-S1 et REG-09-S2 : seule la requête de l'experte atteint la forge simulée. */
    @Test
    void demandeDEvolutionSansRequeteVersLaForgePourLesAutres() {
        StubServer.reset();
        String operation = "PUT /api/v1/reglementaire/fiches/{id}/demande-evolution";
        for (String role : roles) {
            if (!"EXPERT".equals(role)) {
                appeler(operation, role);
            }
        }
        assertEquals(0, StubServer.received(PREFIXE_FORGE).size(), "aucun autre rôle n'atteint la forge");
        appeler(operation, "EXPERT");
        assertEquals(1, StubServer.received(PREFIXE_FORGE).size(), "l'experte, seule autorisée, atteint la forge");
    }

    private int appeler(String operation, String role) {
        String[] parties = operation.split(" ", 2);
        String methode = parties[0];
        String chemin = resoudre(parties[1]);
        RequestSpecification requete = (ANONYME.equals(role) ? given() : given().auth().oauth2(jetons.get(UTILISATEURS.get(role))))
                .config(RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.socket.timeout", DELAI_REPONSE_MS)
                        .setParam("http.connection.timeout", DELAI_REPONSE_MS)));
        if (!"GET".equals(methode)) {
            requete = requete.contentType(ContentType.JSON).body(CORPS.getOrDefault(operation, CORPS_VIDE));
        }
        return requete.when().request(methode, chemin).then().extract().statusCode();
    }

    /** Remplace les paramètres du chemin : ressources réelles de la matrice, ou ressources inconnues. */
    private String resoudre(String chemin) {
        String resolu = chemin
                .replace("{espaceId}", espace.toString())
                .replace("{projectId}", projet.toString())
                .replace("{code}", DOMAINE_INCONNU)
                .replace("{compteId}", RESSOURCE_INCONNUE.toString())
                .replace("{invitationId}", RESSOURCE_INCONNUE.toString())
                .replace("{suggestionId}", RESSOURCE_INCONNUE.toString())
                .replace("{versionId}", RESSOURCE_INCONNUE.toString());
        if (chemin.startsWith(PREFIXE_FICHES)) {
            return resolu.replace("{id}", fiche.toString());
        }
        if (chemin.startsWith(PREFIXE_SOURCES_OPS)) {
            return resolu.replace("{id}", SOURCE_INCONNUE);
        }
        return resolu.replace("{id}", RESSOURCE_INCONNUE.toString());
    }
}
