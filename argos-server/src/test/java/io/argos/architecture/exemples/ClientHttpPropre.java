package io.argos.architecture.exemples;

import java.net.http.HttpClient;

/**
 * Client HTTP construit hors de la fabrique commune, contre lequel {@code ClientsHttpCommunsTest} vérifie que sa règle
 * le refuse. Jamais exécuté.
 */
public final class ClientHttpPropre {

    private ClientHttpPropre() {
    }

    public static HttpClient construire() {
        return HttpClient.newBuilder().build();
    }
}
