package io.argos.ingestion.web;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/**
 * Protection contre la falsification de requête côté serveur (WEB-03, risque R-12, arc42 §8.3) :
 * une adresse n'est acceptée que si son schéma est {@code http} ou {@code https}, qu'elle ne porte
 * pas d'identifiants et que <strong>toutes</strong> les adresses IP de son hôte, après résolution
 * DNS, sont publiques. Le contrôle s'applique à l'ajout d'une source et à chaque redirection
 * (décision D1 du changement {@code collecter-veille-rss-web}).
 *
 * <p>Plages refusées : non spécifiée, bouclage, réseaux privés (RFC 1918), partage d'adresses
 * (RFC 6598), lien local, multidiffusion, documentation, bancs d'essai, réservées ; en IPv6,
 * bouclage, lien local, adresses uniques locales, multidiffusion, documentation, et toute adresse
 * IPv4 embarquée (compatible, mappée, NAT64) appartenant à une plage refusée.
 *
 * <p>{@code argos.veille-web.exceptions} liste des couples {@code hôte:port} exemptés du contrôle
 * des plages. Vide par défaut, il ne sert qu'au développement et aux tests, où les sources
 * simulées écoutent sur la boucle locale ; une redirection vers un autre port ou un autre hôte
 * local reste refusée.
 */
@ApplicationScoped
public class GardeAdresses {

    /** Résolution DNS, remplaçable dans les tests unitaires. */
    @FunctionalInterface
    interface Resolveur {
        InetAddress[] resoudre(String hote) throws UnknownHostException;
    }

    static final int LONGUEUR_MAX = 2000;

    private final Resolveur resolveur;
    private final Set<String> exceptions;

    @Inject
    public GardeAdresses(@ConfigProperty(name = "argos.veille-web.exceptions") Optional<List<String>> exceptions) {
        this(InetAddress::getAllByName, exceptions.orElse(List.of()));
    }

    GardeAdresses(Resolveur resolveur, List<String> exceptions) {
        this.resolveur = resolveur;
        this.exceptions = exceptions.stream().map(e -> e.strip().toLowerCase(Locale.ROOT)).filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Analyse et vérifie une adresse saisie ; lève {@link RecuperationRefusee} si elle est refusée. */
    public URI verifier(String adresse) {
        return verifier(analyser(adresse), Motif.ADRESSE_INTERDITE);
    }

    /** Vérifie une adresse déjà analysée (adresse de redirection : motif {@code REDIRECTION_INTERDITE}). */
    URI verifier(URI uri, Motif motifRefus) {
        String hote = uri.getHost().toLowerCase(Locale.ROOT);
        if (exceptions.contains(hote + ":" + port(uri))) {
            return uri;
        }
        InetAddress[] adresses;
        try {
            adresses = resolveur.resoudre(hote);
        } catch (UnknownHostException e) {
            throw new RecuperationRefusee(motifRefus == Motif.ADRESSE_INTERDITE ? Motif.ADRESSE_INVALIDE : motifRefus,
                    "Nom d'hôte introuvable : " + hote + ".");
        }
        if (adresses == null || adresses.length == 0) {
            throw new RecuperationRefusee(Motif.ADRESSE_INVALIDE, "Nom d'hôte introuvable : " + hote + ".");
        }
        for (InetAddress a : adresses) {
            if (interdite(a)) {
                throw new RecuperationRefusee(motifRefus, (motifRefus == Motif.REDIRECTION_INTERDITE
                        ? "Redirection vers une adresse interdite (réseau privé, local ou réservé) : "
                        : "Adresse interdite (réseau privé, local ou réservé) : ") + hote + ".");
            }
        }
        return uri;
    }

    /** Contrôle syntaxique : schéma http(s), hôte présent, pas d'identifiants, longueur bornée. */
    static URI analyser(String adresse) {
        String brute = adresse == null ? "" : adresse.strip();
        if (brute.isEmpty()) {
            throw new RecuperationRefusee(Motif.ADRESSE_INVALIDE, "Adresse obligatoire.");
        }
        if (brute.length() > LONGUEUR_MAX) {
            throw new RecuperationRefusee(Motif.ADRESSE_INVALIDE, "Adresse trop longue (" + LONGUEUR_MAX + " caractères au plus).");
        }
        URI uri;
        try {
            uri = new URI(brute).normalize();
        } catch (URISyntaxException e) {
            throw new RecuperationRefusee(Motif.ADRESSE_INVALIDE, "Adresse mal formée.");
        }
        return controler(uri);
    }

    /** Résout une adresse de redirection par rapport à l'adresse courante, puis la contrôle. */
    static URI redirection(URI courante, String location) {
        if (location == null || location.isBlank()) {
            throw new RecuperationRefusee(Motif.REDIRECTION_INTERDITE, "Redirection sans adresse de destination.");
        }
        try {
            return controler(courante.resolve(new URI(location.strip())).normalize());
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new RecuperationRefusee(Motif.REDIRECTION_INTERDITE, "Redirection vers une adresse mal formée.");
        } catch (RecuperationRefusee e) {
            throw new RecuperationRefusee(Motif.REDIRECTION_INTERDITE, "Redirection refusée : " + e.getMessage());
        }
    }

    private static URI controler(URI uri) {
        String schema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!schema.equals("http") && !schema.equals("https")) {
            throw new RecuperationRefusee(Motif.ADRESSE_INVALIDE, "Seules les adresses http et https sont acceptées.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new RecuperationRefusee(Motif.ADRESSE_INVALIDE, "Une adresse ne doit pas contenir d'identifiants.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new RecuperationRefusee(Motif.ADRESSE_INVALIDE, "Adresse sans nom d'hôte.");
        }
        return uri;
    }

    static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** Vrai si l'adresse appartient à une plage refusée. */
    static boolean interdite(InetAddress adresse) {
        if (adresse.isAnyLocalAddress() || adresse.isLoopbackAddress() || adresse.isLinkLocalAddress()
                || adresse.isSiteLocalAddress() || adresse.isMulticastAddress()) {
            return true;
        }
        byte[] o = adresse.getAddress();
        if (adresse instanceof Inet4Address) {
            return ipv4Interdite(o);
        }
        if (adresse instanceof Inet6Address) {
            return ipv6Interdite(o);
        }
        return true;
    }

    private static boolean ipv4Interdite(byte[] o) {
        int a = o[0] & 0xff;
        int b = o[1] & 0xff;
        int c = o[2] & 0xff;
        return a == 0                                   // 0.0.0.0/8
                || a == 10                              // 10.0.0.0/8
                || a == 127                             // 127.0.0.0/8
                || (a == 100 && b >= 64 && b <= 127)    // 100.64.0.0/10
                || (a == 169 && b == 254)               // 169.254.0.0/16
                || (a == 172 && b >= 16 && b <= 31)     // 172.16.0.0/12
                || (a == 192 && b == 0 && c == 0)       // 192.0.0.0/24
                || (a == 192 && b == 0 && c == 2)       // 192.0.2.0/24
                || (a == 192 && b == 88 && c == 99)     // 192.88.99.0/24
                || (a == 192 && b == 168)               // 192.168.0.0/16
                || (a == 198 && (b == 18 || b == 19))   // 198.18.0.0/15
                || (a == 198 && b == 51 && c == 100)    // 198.51.100.0/24
                || (a == 203 && b == 0 && c == 113)     // 203.0.113.0/24
                || a >= 224;                            // multidiffusion, réservées, diffusion
    }

    private static boolean ipv6Interdite(byte[] o) {
        int premier = o[0] & 0xff;
        if ((premier & 0xfe) == 0xfc) {                 // fc00::/7 adresses uniques locales
            return true;
        }
        if (premier == 0xfe && (o[1] & 0xc0) == 0xc0) { // fec0::/10 site local (obsolète)
            return true;
        }
        if (premier == 0x20 && o[1] == 0x01 && (o[2] & 0xff) == 0x0d && (o[3] & 0xff) == 0xb8) { // 2001:db8::/32
            return true;
        }
        boolean dixPremiersNuls = true;
        for (int i = 0; i < 10; i++) {
            dixPremiersNuls &= o[i] == 0;
        }
        boolean mappee = dixPremiersNuls && (o[10] & 0xff) == 0xff && (o[11] & 0xff) == 0xff;
        boolean compatible = dixPremiersNuls && o[10] == 0 && o[11] == 0;
        boolean nat64 = (o[0] & 0xff) == 0x00 && (o[1] & 0xff) == 0x64 && (o[2] & 0xff) == 0xff
                && (o[3] & 0xff) == 0x9b;               // 64:ff9b::/96
        if (mappee || compatible || nat64) {
            return ipv4Interdite(new byte[] { o[12], o[13], o[14], o[15] });
        }
        return false;
    }
}
