package io.argos.ingestion.web;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * Résultat du contrôle d'une adresse par {@link GardeAdresses} (WEB-03, WEB-07) : l'adresse elle-même et les
 * adresses IP de l'unique résolution DNS contrôlée de son hôte, dans l'ordre de la résolution. Le transport de
 * la veille web se connecte à l'une de ces adresses IP, sans résoudre le nom de nouveau ; le nom d'hôte de
 * l'adresse sert à l'en-tête {@code Host}, à l'indication SNI et à la vérification du certificat.
 *
 * @param uri adresse contrôlée (schéma http ou https, hôte, port, chemin)
 * @param adresses adresses IP autorisées de l'hôte, au moins une
 */
public record DestinationControlee(URI uri, List<InetAddress> adresses) {

    private static final String SCHEMA_CHIFFRE = "https";

    public DestinationControlee {
        adresses = List.copyOf(adresses);
        if (adresses.isEmpty()) {
            throw new IllegalArgumentException("Destination sans adresse IP : " + uri.getHost());
        }
    }

    /** Nom d'hôte de l'adresse, tel qu'il figure dans l'URI. */
    public String hote() {
        return uri.getHost();
    }

    /** Port de l'adresse, implicite selon le schéma s'il n'est pas donné. */
    public int port() {
        return GardeAdresses.port(uri);
    }

    /** Vrai pour une adresse {@code https}. */
    public boolean chiffree() {
        return SCHEMA_CHIFFRE.equalsIgnoreCase(uri.getScheme());
    }

    /** Chemin et requête à transmettre au serveur ({@code /} si l'adresse n'a pas de chemin). */
    public String cheminEtRequete() {
        String chemin = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? chemin : chemin + "?" + uri.getRawQuery();
    }
}
