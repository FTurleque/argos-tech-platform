package io.argos.commun.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Fabrique unique des clients HTTP sortants du serveur ({@code java.net.http}, ADR-0039, arc42 §8.3) : délai de
 * connexion fourni par l'appelant, redirections refusées (une URL reçue n'est jamais suivie, R-12), requêtes
 * {@code GET} identifiées par le même {@code User-Agent}. La lecture des réponses est bornée par
 * {@link LectureBornee}. Chaque client garde sa propre classification des erreurs.
 *
 * <p>Le test d'architecture {@code ClientsHttpCommunsTest} vérifie qu'aucune autre classe ne construit de client.
 */
public final class ClientsHttp {

    /** {@code User-Agent} de toutes les requêtes sortantes. */
    public static final String USER_AGENT = "argos-server";

    /** Taille maximale par défaut d'une réponse lue en entier : 16 Mio. */
    public static final long TAILLE_MAX_PAR_DEFAUT = 16L * 1024 * 1024;

    private ClientsHttp() {
    }

    /** Client sans redirection, au délai de connexion donné, par le proxy du système s'il en a un. */
    public static HttpClient client(Duration delaiConnexion) {
        return constructeur(delaiConnexion).build();
    }

    /** Client sans redirection et sans proxy : service local, joint directement (passerelle IA). */
    public static HttpClient clientSansProxy(Duration delaiConnexion) {
        return constructeur(delaiConnexion).proxy(HttpClient.Builder.NO_PROXY).build();
    }

    /** Requête {@code GET} identifiée, au délai de réponse donné. */
    public static HttpRequest.Builder requete(URI uri, Duration delai) {
        return HttpRequest.newBuilder(uri).timeout(delai).header("User-Agent", USER_AGENT).GET();
    }

    private static HttpClient.Builder constructeur(Duration delaiConnexion) {
        return HttpClient.newBuilder().connectTimeout(delaiConnexion).followRedirects(HttpClient.Redirect.NEVER);
    }
}
