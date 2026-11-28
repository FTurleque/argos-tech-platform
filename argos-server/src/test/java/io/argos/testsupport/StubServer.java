package io.argos.testsupport;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Serveur HTTP local qui simule toutes les sources externes (OSV, NVD, KEV, EPSS, CERT-FR,
 * GitLab, GitHub, endoflife.date) et l'API Ollama de la passerelle IA pour les tests {@code @QuarkusTest} : aucun test
 * n'appelle de service externe ni de modèle réel. Ressource de test Quarkus qui tient une instance de
 * {@link ServeurHttpSimule}, partagée par l'application de test démarrée, et en injecte les adresses dans la
 * configuration. Un test unitaire, sans Quarkus, démarre sa propre instance de {@link ServeurHttpSimule}.
 */
public class StubServer implements QuarkusTestResourceLifecycleManager {

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

        /** Réponse retenue jusqu'à {@link StubServer#liberer()} ou {@link StubServer#reset()}. */
        public static Stub retenue(int status, String body, Map<String, String> headers) {
            return new Stub(status, body, headers, true, null);
        }

        ServeurHttpSimule.Stub simule() {
            return new ServeurHttpSimule.Stub(status, body, headers, retenue, octets);
        }
    }

    /** Requête reçue, avec sa méthode HTTP et son corps (vide pour un GET). */
    public record Received(String pathAndQuery, Map<String, List<String>> headers, Instant instant, String method, String body) {
    }

    private static final int SUCCES = 200;

    private static ServeurHttpSimule serveur;

    @Override
    public Map<String, String> start() {
        serveur = ServeurHttpSimule.demarrer();
        String base = serveur.base();
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
                Map.entry("argos.veille-web.exceptions", "localhost:" + serveur.port()),
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
        if (serveur != null) {
            serveur.close();
        }
    }

    /** Adresse de base du serveur simulé, par exemple {@code http://localhost:41234}. */
    public static String base() {
        return serveur.base();
    }

    public static void reset() {
        serveur.reset();
    }

    /** Envoie les réponses retenues en cours ; les suivantes seront retenues à leur tour. */
    public static void liberer() {
        serveur.liberer();
    }

    /** Programme les réponses successives d'un chemin (ex. {@code /epss?cve=CVE-1}). */
    public static void programmer(String pathAndQuery, Stub... responses) {
        serveur.programmer(pathAndQuery, Arrays.stream(responses).map(Stub::simule).toArray(ServeurHttpSimule.Stub[]::new));
    }

    public static List<Received> received(String prefix) {
        return serveur.received(prefix).stream()
                .map(recue -> new Received(recue.pathAndQuery(), recue.headers(), recue.instant(), recue.method(), recue.body()))
                .toList();
    }
}
