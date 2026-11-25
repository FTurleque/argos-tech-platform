package io.argos.api.comptes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ACC-11-S4 et ACC-13 : le royaume livré ({@code deploiement/keycloak/argos-realm.json}) importé par un vrai Keycloak,
 * avec les variables que {@code compose.yaml} lui transmet. Keycloak tourne en mode développement derrière des
 * en-têtes {@code X-Forwarded-*} : le test choisit l'adresse source vue par le royaume pour éprouver l'exigence de TLS.
 * Aucune requête vers Internet : le seul trafic est celui du conteneur de test.
 */
class RoyaumeLivreTest {

    private static final String REGEX_VERSION_KEYCLOAK = "ARGOS_KEYCLOAK_VERSION:-([0-9.]+)";
    private static final int PORT_KEYCLOAK = 8080;
    private static final Duration DELAI_DEMARRAGE = Duration.ofMinutes(4);
    private static final Duration DELAI_REQUETE = Duration.ofSeconds(30);

    private static final String ADRESSE_PUBLIQUE = "http://argos.test:8080";
    private static final String COURRIEL_ADMIN = "admin@argos.test";
    private static final String MOT_DE_PASSE_PROVISOIRE = "provisoire-0123456789";
    private static final String MOT_DE_PASSE_ADMIN_KEYCLOAK = "administration-0123456789";
    private static final String MOT_DE_PASSE_CONFORME = "conforme-0123456789";
    private static final String MOT_DE_PASSE_ERRONE = "mauvais-mot-de-passe";

    private static final String ADRESSE_LOCALE_VUE = "192.168.1.20";
    private static final String ADRESSE_PUBLIQUE_VUE = "203.0.113.9";

    private static final int ECHECS_AVANT_BLOCAGE = 5;
    private static final Duration DELAI_ENTRE_ESSAIS = Duration.ofMillis(1100);
    private static final int STATUT_OK = 200;
    private static final int STATUT_REQUETE_INVALIDE = 400;
    private static final int STATUT_CREE = 201;
    private static final int STATUT_SANS_CONTENU = 204;

    private static final Pattern ACTION_FORMULAIRE = Pattern.compile("<form[^>]*action=\"([^\"]+)\"");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static GenericContainer<?> keycloak;
    private static String base;

    @BeforeAll
    static void demarrerKeycloak() throws IOException {
        Path royaume = Path.of("..", "deploiement", "keycloak", "argos-realm.json").toAbsolutePath().normalize();
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:" + versionKeycloak())
                .withCopyFileToContainer(MountableFile.forHostPath(royaume),
                        "/opt/keycloak/data/import/argos-realm.json")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", MOT_DE_PASSE_ADMIN_KEYCLOAK)
                .withEnv("ARGOS_PUBLIC_URL", ADRESSE_PUBLIQUE)
                .withEnv("ARGOS_ADMIN_EMAIL", COURRIEL_ADMIN)
                .withEnv("ARGOS_ADMIN_MOT_DE_PASSE_INITIAL", MOT_DE_PASSE_PROVISOIRE)
                .withCommand("start-dev", "--import-realm", "--proxy-headers=xforwarded",
                        "--hostname-strict=false", "--http-enabled=true")
                .withExposedPorts(PORT_KEYCLOAK)
                .waitingFor(Wait.forHttp("/realms/argos").forPort(PORT_KEYCLOAK).forStatusCode(STATUT_OK)
                        .withStartupTimeout(DELAI_DEMARRAGE));
        keycloak.start();
        base = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(PORT_KEYCLOAK);
    }

    @AfterAll
    static void arreterKeycloak() {
        if (keycloak != null) {
            keycloak.stop();
        }
    }

    /** Version de l'image Keycloak : celle que {@code compose.yaml} construit par défaut. */
    private static String versionKeycloak() throws IOException {
        String compose = Files.readString(Path.of("..", "compose.yaml"));
        Matcher correspondance = Pattern.compile(REGEX_VERSION_KEYCLOAK).matcher(compose);
        assertTrue(correspondance.find(), "version de Keycloak introuvable dans compose.yaml");
        return correspondance.group(1);
    }

    /** ACC-11-S4 : seule l'adresse de retour déclarée par l'adresse publique est acceptée. */
    @Test
    void retourNonDeclareRefuse() throws Exception {
        ClientHttp client = new ClientHttp(ADRESSE_LOCALE_VUE);
        assertEquals(STATUT_OK, client.get(urlAutorisation(ADRESSE_PUBLIQUE + "/")).statusCode(),
                "l'adresse de retour déclarée est acceptée");
        for (String retourEtranger : new String[] {ADRESSE_PUBLIQUE + "/autre", ADRESSE_PUBLIQUE + "/index.html",
                "http://autre.test:8080/", "http://localhost:8088/", "https://evil.example/", ADRESSE_PUBLIQUE + "/*"}) {
            HttpResponse<String> reponse = client.get(urlAutorisation(retourEtranger));
            assertEquals(STATUT_REQUETE_INVALIDE, reponse.statusCode(), "retour refusé : " + retourEtranger);
            assertTrue(reponse.body().contains("redirect_uri"), reponse.body());
            assertFalse(reponse.headers().firstValue("Location").isPresent(), "aucun code ne part vers " + retourEtranger);
        }
    }

    /** ACC-11-S4 : le retour du client desktop (bouclage) reste déclaré, l'adresse de l'hôte local seulement. */
    @Test
    void retourDuDesktopDeclare() throws Exception {
        ClientHttp client = new ClientHttp(ADRESSE_LOCALE_VUE);
        assertEquals(STATUT_OK, client.get(urlAutorisation("http://127.0.0.1/argos/retour")).statusCode());
    }

    /** ACC-13-S1 : cinq mots de passe erronés bloquent le compte, le bon mot de passe est alors refusé. */
    @Test
    void forceBruteBloqueLeCompte() throws Exception {
        String courriel = "force-brute@argos.test";
        String identifiant = creerUtilisateur(courriel, MOT_DE_PASSE_CONFORME);
        ClientHttp client = new ClientHttp(ADRESSE_LOCALE_VUE);
        for (int essai = 0; essai < ECHECS_AVANT_BLOCAGE; essai++) {
            HttpResponse<String> reponse = client.connecter(courriel, MOT_DE_PASSE_ERRONE);
            assertEquals(STATUT_OK, reponse.statusCode(), reponse.body());
            assertTrue(contientFormulaireDeConnexion(reponse.body()), "échec n° " + (essai + 1));
            attendreFinDeLaDetectionDeConnexionRapide();
        }
        JsonNode etat = administration("GET", "/attack-detection/brute-force/users/" + identifiant, null);
        assertEquals(ECHECS_AVANT_BLOCAGE, etat.get("numFailures").asInt());
        assertTrue(etat.get("disabled").asBoolean(), "le compte est temporairement bloqué");
        HttpResponse<String> bonMotDePasse = client.connecter(courriel, MOT_DE_PASSE_CONFORME);
        assertTrue(contientFormulaireDeConnexion(bonMotDePasse.body()),
                "le bon mot de passe est refusé pendant le blocage");
        assertFalse(bonMotDePasse.headers().firstValue("Location").isPresent());
    }

    /** ACC-13-S2 : un mot de passe hors politique est refusé, le mot de passe provisoire reste à changer. */
    @Test
    void motDePasseHorsPolitiqueRefuse() throws Exception {
        ClientHttp client = new ClientHttp(ADRESSE_LOCALE_VUE);
        HttpResponse<String> connexion = client.connecter(COURRIEL_ADMIN, MOT_DE_PASSE_PROVISOIRE);
        if (connexion.body().contains("name=\"firstName\"")) {
            // Le profil d'utilisateur de Keycloak demande d'abord le nom, avant le changement du mot de passe.
            connexion = client.soumettre(connexion, Map.of("firstName", "Administrateur", "lastName", "Plateforme",
                    "email", COURRIEL_ADMIN));
        }
        assertTrue(connexion.body().contains("password-new"),
                "le mot de passe provisoire exige un changement : " + connexion.statusCode()
                        + connexion.headers().firstValue("Location").orElse(""));
        for (String horsPolitique : new String[] {"onze-car-11", COURRIEL_ADMIN}) {
            HttpResponse<String> reponse = client.soumettre(connexion, Map.of("password-new", horsPolitique,
                    "password-confirm", horsPolitique));
            assertTrue(reponse.body().contains("password-new"),
                    "le changement reste demandé après le refus de « " + horsPolitique + " »");
            assertTrue(reponse.body().toLowerCase().contains("invalid password"), reponse.body());
        }
        String identifiant = identifiantUtilisateur(COURRIEL_ADMIN);
        JsonNode refus = administration("PUT", "/users/" + identifiant + "/reset-password",
                "{\"type\":\"password\",\"value\":\"onze-car-11\",\"temporary\":false}", STATUT_REQUETE_INVALIDE);
        assertTrue(refus.toString().contains("12"), refus.toString());
    }

    /** ACC-13-S3 : TLS exigé pour une adresse publique, connexion en http acceptée depuis un réseau privé. */
    @Test
    void tlsExigePourUneAdressePublique() throws Exception {
        HttpResponse<String> publique = new ClientHttp(ADRESSE_PUBLIQUE_VUE).get(urlAutorisation(ADRESSE_PUBLIQUE + "/"));
        assertEquals(STATUT_REQUETE_INVALIDE, publique.statusCode(), publique.body());
        assertTrue(publique.body().contains("HTTPS required"), publique.body());
        HttpResponse<String> privee = new ClientHttp(ADRESSE_LOCALE_VUE).get(urlAutorisation(ADRESSE_PUBLIQUE + "/"));
        assertEquals(STATUT_OK, privee.statusCode());
    }

    /** ACC-13 : réglages du royaume importé, tels que les lit l'API d'administration. */
    @Test
    void reglagesDuRoyaumeImporte() throws Exception {
        JsonNode royaume = administration("GET", "", null);
        assertEquals("external", royaume.get("sslRequired").asText());
        assertTrue(royaume.get("bruteForceProtected").asBoolean());
        assertEquals(5, royaume.get("failureFactor").asInt());
        assertEquals(60, royaume.get("waitIncrementSeconds").asInt());
        assertEquals(900, royaume.get("maxFailureWaitSeconds").asInt());
        assertEquals("length(12) and notUsername and notEmail and passwordHistory(3)",
                royaume.get("passwordPolicy").asText());
    }

    /**
     * Keycloak bloque une minute dès que deux échecs se suivent en moins d'une seconde (réglage par défaut du
     * royaume, utile contre les outils automatiques) : les essais d'une personne sont espacés d'au moins une seconde.
     */
    private static void attendreFinDeLaDetectionDeConnexionRapide() {
        Awaitility.await().pollDelay(DELAI_ENTRE_ESSAIS).atMost(DELAI_ENTRE_ESSAIS.plusSeconds(1))
                .until(() -> true);
    }

    private static String urlAutorisation(String retour) {
        Map<String, String> parametres = Map.of(
                "client_id", "argos-ui",
                "response_type", "code",
                "scope", "openid",
                "state", "etat-de-test",
                "code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                "code_challenge_method", "S256",
                "redirect_uri", retour);
        return base + "/realms/argos/protocol/openid-connect/auth?" + parametres.entrySet().stream()
                .map(entree -> entree.getKey() + "=" + URLEncoder.encode(entree.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static boolean contientFormulaireDeConnexion(String page) {
        return page.contains("kc-form-login");
    }

    private static String creerUtilisateur(String courriel, String motDePasse) throws Exception {
        String corps = JSON.writeValueAsString(Map.of("username", courriel, "email", courriel, "enabled", true,
                "emailVerified", true, "credentials",
                new Object[] {Map.of("type", "password", "value", motDePasse, "temporary", false)}));
        HttpResponse<String> reponse = envoyerAdministration("POST", "/users", corps);
        assertEquals(STATUT_CREE, reponse.statusCode(), reponse.body());
        return identifiantUtilisateur(courriel);
    }

    private static String identifiantUtilisateur(String courriel) throws Exception {
        JsonNode utilisateurs = administration("GET",
                "/users?exact=true&username=" + URLEncoder.encode(courriel, StandardCharsets.UTF_8), null);
        assertEquals(1, utilisateurs.size(), "utilisateur " + courriel);
        return utilisateurs.get(0).get("id").asText();
    }

    private static JsonNode administration(String methode, String chemin, String corps) throws Exception {
        return administration(methode, chemin, corps, STATUT_OK);
    }

    private static JsonNode administration(String methode, String chemin, String corps, int statutAttendu)
            throws Exception {
        HttpResponse<String> reponse = envoyerAdministration(methode, chemin, corps);
        int attendu = reponse.statusCode() == STATUT_SANS_CONTENU && statutAttendu == STATUT_OK
                ? STATUT_SANS_CONTENU : statutAttendu;
        assertEquals(attendu, reponse.statusCode(), reponse.body());
        return reponse.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(reponse.body());
    }

    private static HttpResponse<String> envoyerAdministration(String methode, String chemin, String corps)
            throws Exception {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(base + "/admin/realms/argos" + chemin))
                .timeout(DELAI_REQUETE)
                .header("Authorization", "Bearer " + jetonAdministration())
                .header("Content-Type", "application/json");
        requete.method(methode, corps == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corps));
        return HttpClient.newHttpClient().send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jetonAdministration() throws Exception {
        String corps = "client_id=admin-cli&grant_type=password&username=admin&password="
                + URLEncoder.encode(MOT_DE_PASSE_ADMIN_KEYCLOAK, StandardCharsets.UTF_8);
        HttpRequest requete = HttpRequest.newBuilder(URI.create(base + "/realms/master/protocol/openid-connect/token"))
                .timeout(DELAI_REQUETE)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corps)).build();
        HttpResponse<String> reponse = HttpClient.newHttpClient().send(requete, HttpResponse.BodyHandlers.ofString());
        assertEquals(STATUT_OK, reponse.statusCode(), reponse.body());
        return JSON.readTree(reponse.body()).get("access_token").asText();
    }

    /** Navigateur simulé : cookies de session, aucune redirection suivie, adresse source vue par Keycloak imposée. */
    private static final class ClientHttp {

        private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private final String adresseSource;

        ClientHttp(String adresseSource) {
            this.adresseSource = adresseSource;
        }

        HttpResponse<String> get(String url) throws Exception {
            return envoyer(requete(url).GET().build());
        }

        private HttpResponse<String> envoyer(HttpRequest requete) throws Exception {
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            for (String cookie : reponse.headers().allValues("Set-Cookie")) {
                String paire = cookie.substring(0, cookie.indexOf(';') < 0 ? cookie.length() : cookie.indexOf(';'));
                int separateur = paire.indexOf('=');
                String nom = paire.substring(0, separateur);
                String valeur = paire.substring(separateur + 1);
                if (valeur.isEmpty()) {
                    cookies.remove(nom);
                } else {
                    cookies.put(nom, valeur);
                }
            }
            return reponse;
        }

        /** Ouvre la page de connexion puis soumet l'identifiant et le mot de passe. */
        HttpResponse<String> connecter(String courriel, String motDePasse) throws Exception {
            HttpResponse<String> page = get(urlAutorisation(ADRESSE_PUBLIQUE + "/"));
            assertEquals(STATUT_OK, page.statusCode(), page.body());
            return soumettre(page, Map.of("username", courriel, "password", motDePasse));
        }

        /** Soumet le formulaire de la page donnée, en suivant les redirections internes à Keycloak. */
        HttpResponse<String> soumettre(HttpResponse<String> page, Map<String, String> champs) throws Exception {
            Matcher action = ACTION_FORMULAIRE.matcher(page.body());
            assertTrue(action.find(), "formulaire introuvable : " + page.body());
            String url = action.group(1).replace("&amp;", "&");
            String corps = champs.entrySet().stream()
                    .map(entree -> entree.getKey() + "=" + URLEncoder.encode(entree.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            HttpResponse<String> reponse = envoyer(requete(url)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corps)).build());
            return suivreRedirectionsInternes(reponse);
        }

        private HttpResponse<String> suivreRedirectionsInternes(HttpResponse<String> reponse) throws Exception {
            HttpResponse<String> courante = reponse;
            while (courante.statusCode() / 100 == 3) {
                String destination = URI.create(base).resolve(courante.headers().firstValue("Location").orElseThrow())
                        .toString();
                if (!destination.startsWith(base)) {
                    return courante;
                }
                courante = get(destination);
            }
            return courante;
        }

        private HttpRequest.Builder requete(String url) {
            assertNotNull(url);
            HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(url)).timeout(DELAI_REQUETE);
            if (!cookies.isEmpty()) {
                requete.header("Cookie", cookies.entrySet().stream()
                        .map(cookie -> cookie.getKey() + "=" + cookie.getValue()).collect(Collectors.joining("; ")));
            }
            return requete
                    .header("X-Forwarded-For", adresseSource)
                    .header("X-Forwarded-Proto", "http")
                    .header("X-Forwarded-Host", URI.create(base).getAuthority());
        }
    }
}
