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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serveur HTTP local à réponses programmées, sans Quarkus : un test unitaire qui joint une source simulée en démarre
 * une instance ({@link #demarrer()}) et la ferme à la fin ({@link #close()}). Les réponses sont programmées par chemin
 * (avec ou sans requête) ; la dernière réponse d'une file reste active ; un chemin non programmé répond 404. Une
 * réponse « retenue » simule un serveur qui tarde : elle n'est envoyée qu'à la libération ({@link #liberer()}) ou à la
 * réinitialisation, sans sommeil ; un client qui borne son attente abandonne de lui-même.
 *
 * <p>{@link StubServer} en tient une instance, partagée par les tests {@code @QuarkusTest} : un test unitaire n'y
 * touche jamais, l'application de test démarrée en dépend.
 */
public final class ServeurHttpSimule implements AutoCloseable {

    /**
     * Réponse programmée.
     *
     * @param retenue réponse envoyée seulement à la libération ou à la réinitialisation du serveur simulé
     */
    public record Stub(int status, String body, Map<String, String> headers, boolean retenue, byte[] octets) {

        public Stub(int status, String body, Map<String, String> headers) {
            this(status, body, headers, false, null);
        }

        /** Réponse binaire (archive), servie telle quelle. */
        public static Stub octets(byte[] corps) {
            return new Stub(SUCCES, "", Map.of(), false, corps);
        }

        public static Stub succes(String body) {
            return new Stub(SUCCES, body, Map.of());
        }

        public static Stub status(int status) {
            return new Stub(status, "", Map.of());
        }

        /** Réponse retenue jusqu'à {@link ServeurHttpSimule#liberer()} ou {@link ServeurHttpSimule#reset()}. */
        public static Stub retenue(int status, String body, Map<String, String> headers) {
            return new Stub(status, body, headers, true, null);
        }
    }

    /** Requête reçue, avec sa méthode HTTP et son corps (vide pour un GET). */
    public record Received(String pathAndQuery, Map<String, List<String>> headers, Instant instant, String method, String body) {
    }

    private static final int SUCCES = 200;
    private static final int NON_TROUVE = 404;

    private final Map<String, Deque<Stub>> routes = new ConcurrentHashMap<>();
    private final List<Received> recues = Collections.synchronizedList(new ArrayList<>());
    private final HttpServer serveur;
    private final ExecutorService executeur = Executors.newCachedThreadPool();
    private volatile CountDownLatch liberation = new CountDownLatch(1);

    private ServeurHttpSimule() {
        try {
            serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException erreur) {
            throw new IllegalStateException("serveur simulé impossible à ouvrir", erreur);
        }
        serveur.createContext("/", this::repondre);
        serveur.setExecutor(executeur);
    }

    /** Démarre un serveur simulé sur un port libre de la boucle locale. */
    public static ServeurHttpSimule demarrer() {
        ServeurHttpSimule simule = new ServeurHttpSimule();
        simule.serveur.start();
        return simule;
    }

    /** Port d'écoute. */
    public int port() {
        return serveur.getAddress().getPort();
    }

    /** Adresse de base, par exemple {@code http://localhost:41234}. */
    public String base() {
        return "http://localhost:" + port();
    }

    /** Oublie les réponses programmées et les requêtes reçues, et envoie les réponses retenues. */
    public void reset() {
        routes.clear();
        recues.clear();
        liberer();
    }

    /** Envoie les réponses retenues en cours ; les suivantes seront retenues à leur tour. */
    public void liberer() {
        CountDownLatch courante = liberation;
        liberation = new CountDownLatch(1);
        courante.countDown();
    }

    /** Programme les réponses successives d'un chemin (ex. {@code /epss?cve=CVE-1}). */
    public void programmer(String pathAndQuery, Stub... responses) {
        routes.put(pathAndQuery, new ArrayDeque<>(List.of(responses)));
    }

    /** Requêtes reçues dont le chemin commence par {@code prefix}, dans l'ordre d'arrivée. */
    public List<Received> received(String prefix) {
        synchronized (recues) {
            return recues.stream().filter(requete -> requete.pathAndQuery().startsWith(prefix)).toList();
        }
    }

    @Override
    public void close() {
        liberer();
        serveur.stop(0);
        executeur.shutdownNow();
    }

    /** Enregistre la requête reçue puis envoie la réponse programmée pour son chemin (404 sinon). */
    private void repondre(HttpExchange echange) throws IOException {
        String chemin = echange.getRequestURI().getRawPath();
        String requete = echange.getRequestURI().getRawQuery();
        String complet = requete == null ? chemin : chemin + "?" + requete;
        String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        recues.add(new Received(complet, Map.copyOf(echange.getRequestHeaders()), Instant.now(), echange.getRequestMethod(),
                corps));
        Deque<Stub> file = routes.getOrDefault(complet, routes.get(chemin));
        Stub reponse;
        if (file == null) {
            reponse = Stub.status(NON_TROUVE);
        } else {
            synchronized (file) {
                reponse = file.size() > 1 ? file.poll() : file.peek();
            }
        }
        if (reponse.retenue()) {
            attendreLiberation();
        }
        reponse.headers().forEach((nom, valeur) -> echange.getResponseHeaders().add(nom, valeur));
        byte[] contenu = reponse.octets() != null ? reponse.octets() : reponse.body().getBytes(StandardCharsets.UTF_8);
        echange.sendResponseHeaders(reponse.status(), contenu.length == 0 ? -1 : contenu.length);
        if (contenu.length > 0) {
            try (OutputStream sortie = echange.getResponseBody()) {
                sortie.write(contenu);
            }
        }
        echange.close();
    }

    private void attendreLiberation() {
        try {
            liberation.await();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }
}
