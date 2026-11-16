package io.argos.mcp;

import java.net.CookieHandler;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Approbation d'un code d'appareil (RFC 8628) comme le ferait l'utilisateur dans son navigateur : ouverture
 * de la page de vérification du Keycloak de test, saisie éventuelle du code, connexion, consentement
 * éventuel. Utilisé seulement par les tests.
 */
final class FluxAppareil {

    private static final Pattern FORMULAIRE = Pattern.compile("<form[^>]*action=\"([^\"]+)\"[^>]*>(.*?)</form>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAMP = Pattern.compile(
            "<input[^>]*name=\"([^\"]+)\"[^>]*?(?:value=\"([^\"]*)\")?[^>]*>", Pattern.CASE_INSENSITIVE);

    private FluxAppareil() {
    }

    /** Approuve le code ; renvoie le parcours suivi (adresse et extrait de chaque page) pour le diagnostic. */
    static String approuver(String pageVerification, String utilisateur, String motDePasse) throws Exception {
        StringBuilder parcours = new StringBuilder();
        HttpClient client = HttpClient.newBuilder().cookieHandler(new Biscuits())
                .followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(pageVerification)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        for (int etape = 0; etape < 6; etape++) {
            String html = page.body();
            parcours.append(page.statusCode()).append(' ').append(page.uri()).append(" : ")
                    .append(html.replaceAll("\\s+", " "), 0, Math.min(html.length(), 1500)).append('\n');
            Matcher f = FORMULAIRE.matcher(html);
            if (!f.find() || !(html.contains("name=\"username\"") || html.contains("name=\"accept\"")
                    || html.contains("name=\"device_user_code\""))) {
                return parcours.toString();
            }
            String action = f.group(1).replace("&amp;", "&");
            Map<String, String> champs = new LinkedHashMap<>();
            Matcher c = CHAMP.matcher(f.group(2));
            while (c.find()) {
                champs.put(c.group(1), c.group(2) == null ? "" : c.group(2).replace("&amp;", "&"));
            }
            if (champs.containsKey("device_user_code") && champs.get("device_user_code").isEmpty()) {
                champs.put("device_user_code", pageVerification.replaceAll(".*[?&]user_code=([^&]+).*", "$1"));
            }
            if (champs.containsKey("username")) {
                champs.put("username", utilisateur);
                champs.put("password", motDePasse);
            }
            if (html.contains("name=\"accept\"")) {
                champs.put("accept", "Yes");
                champs.remove("cancel");
            }
            String corps = champs.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            page = client.send(HttpRequest.newBuilder(URI.create(page.uri().resolve(action).toString()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corps)).build(), HttpResponse.BodyHandlers.ofString());
        }
        return parcours.toString();
    }

    /**
     * Cookies du Keycloak de test : marqués {@code Secure}, ils seraient écartés par {@code CookieManager}
     * sur la boucle locale en HTTP. Tous les cookies reçus sont renvoyés, sans autre règle.
     */
    private static final class Biscuits extends CookieHandler {

        private final Map<String, String> valeurs = new LinkedHashMap<>();

        @Override
        public synchronized Map<String, List<String>> get(URI uri, Map<String, List<String>> entetes) {
            if (valeurs.isEmpty()) {
                return Map.of();
            }
            String cookie = valeurs.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));
            return Map.of("Cookie", List.of(cookie));
        }

        @Override
        public synchronized void put(URI uri, Map<String, List<String>> entetes) {
            entetes.forEach((nom, lignes) -> {
                if (nom != null && nom.equalsIgnoreCase("Set-Cookie")) {
                    for (String ligne : lignes) {
                        String paire = ligne.split(";", 2)[0];
                        int egal = paire.indexOf('=');
                        if (egal > 0) {
                            valeurs.put(paire.substring(0, egal).trim(), paire.substring(egal + 1).trim());
                        }
                    }
                }
            });
        }
    }
}
