package io.argos.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Serveur HTTP local qui simule toutes les sources externes (OSV, NVD, KEV, EPSS, CERT-FR,
 * GitLab, GitHub, endoflife.date) et l'API Ollama de la passerelle IA : aucun test n'appelle de
 * service externe ni de modèle réel. Les réponses sont programmées par
 * chemin (avec ou sans requête) ; la dernière réponse d'une file reste active.
 */
public class StubServer implements QuarkusTestResourceLifecycleManager {

    /** Réponse programmée. */
    public record Stub(int status, String body, Map<String, String> headers, long delayMillis, byte[] octets) {

        public Stub(int status, String body, Map<String, String> headers, long delayMillis) {
            this(status, body, headers, delayMillis, null);
        }

        /** Réponse binaire (archive), servie telle quelle. */
        public static Stub octets(byte[] corps) {
            return new Stub(200, "", Map.of(), 0, corps);
        }

        public static Stub ok(String body) {
            return new Stub(200, body, Map.of(), 0);
        }

        public static Stub status(int status) {
            return new Stub(status, "", Map.of(), 0);
        }
    }

    /** Requête reçue, avec sa méthode HTTP et son corps (vide pour un GET). */
    public record Received(String pathAndQuery, Map<String, List<String>> headers, Instant at, String method, String body) {
    }

    private static final Map<String, Deque<Stub>> ROUTES = new ConcurrentHashMap<>();
    private static final List<Received> RECEIVED = Collections.synchronizedList(new ArrayList<>());
    private static HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", StubServer::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        return Map.ofEntries(
                Map.entry("argos.sources.osv.export-url", base + "/osv-export"),
                Map.entry("argos.sources.osv.api-url", base + "/osv-api"),
                Map.entry("argos.sources.osv.ecosystems", "Maven"),
                Map.entry("argos.sources.nvd.url", base + "/nvd"),
                Map.entry("argos.sources.kev.url", base + "/kev.json"),
                Map.entry("argos.sources.epss.url", base + "/epss"),
                Map.entry("argos.sources.certfr.feed-url", base + "/certfr/feed/"),
                Map.entry("argos.sources.certfr.base-url", base + "/certfr/"),
                Map.entry("argos.scm.gitlab.url", base + "/gitlab"),
                Map.entry("argos.scm.github.url", base + "/github"),
                Map.entry("argos.sources.http-timeout", "PT1S"),
                // Veille web : seule l'adresse exacte du serveur simulé est exemptée du refus des
                // adresses locales ; toute autre adresse locale reste refusée (WEB-03).
                Map.entry("argos.veille-web.exceptions", "localhost:" + server.getAddress().getPort()),
                Map.entry("argos.veille-web.delai", "PT1S"),
                // Calendrier endoflife.date : réponses enregistrées servies sous /endoflife.
                Map.entry("argos.radar.endoflife.url", base + "/endoflife"),
                Map.entry("argos.radar.endoflife.delai", "PT1S"),
                // Passerelle IA : API Ollama simulée sous /ollama (réponses enregistrées), aucun modèle réel.
                Map.entry("argos.ia.ollama.url", base + "/ollama"),
                Map.entry("argos.ia.delai", "PT2S"),
                // Veille réglementaire : archives JORF enregistrées servies sous /jorf, aucun accès à la DILA.
                Map.entry("argos.reglementaire.jorf.url", base + "/jorf"),
                Map.entry("argos.reglementaire.jorf.delai", "PT2S"));
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Adresse de base du serveur simulé, par exemple {@code http://localhost:41234}. */
    public static String base() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public static void reset() {
        ROUTES.clear();
        RECEIVED.clear();
    }

    /** Programme les réponses successives d'un chemin (ex. {@code /epss?cve=CVE-1}). */
    public static void on(String pathAndQuery, Stub... responses) {
        Deque<Stub> queue = new ArrayDeque<>(List.of(responses));
        ROUTES.put(pathAndQuery, queue);
    }

    public static List<Received> received(String prefix) {
        synchronized (RECEIVED) {
            return RECEIVED.stream().filter(r -> r.pathAndQuery().startsWith(prefix)).toList();
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        String full = query == null ? path : path + "?" + query;
        String corps = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        RECEIVED.add(new Received(full, Map.copyOf(exchange.getRequestHeaders()), Instant.now(), exchange.getRequestMethod(), corps));
        Deque<Stub> queue = ROUTES.getOrDefault(full, ROUTES.get(path));
        Stub stub;
        if (queue == null) {
            stub = Stub.status(404);
        } else {
            synchronized (queue) {
                stub = queue.size() > 1 ? queue.poll() : queue.peek();
            }
        }
        if (stub.delayMillis() > 0) {
            try {
                Thread.sleep(stub.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        stub.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        byte[] body = stub.octets() != null ? stub.octets() : stub.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(stub.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
