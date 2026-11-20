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
 * pas d'identifiants, que son port figure dans {@code argos.veille-web.ports-autorises} (80 et 443
 * par défaut ; port implicite du schéma si l'adresse n'en donne pas) et que <strong>toutes</strong>
 * les adresses IP de son hôte, après résolution DNS, sont publiques. Le contrôle s'applique à
 * l'ajout d'une source, à chaque collecte et à chaque redirection (décision D1 du changement
 * {@code collecter-veille-rss-web}).
 *
 * <p>Plages IPv4 refusées : 0.0.0.0/8, 10.0.0.0/8, 100.64.0.0/10, 127.0.0.0/8, 169.254.0.0/16,
 * 172.16.0.0/12, 192.0.0.0/24, 192.0.2.0/24, 192.88.99.0/24, 192.168.0.0/16, 198.18.0.0/15,
 * 198.51.100.0/24, 203.0.113.0/24, 224.0.0.0/3 (multidiffusion, réservées, diffusion). Plages
 * IPv6 refusées : non spécifiée, bouclage, lien local (fe80::/10), site local (fec0::/10),
 * adresses uniques locales (fc00::/7), multidiffusion (ff00::/8), documentation (2001:db8::/32),
 * rejet (100::/64), Teredo (2001::/32), 64:ff9b::/32 hors du préfixe NAT64 bien connu (dont
 * 64:ff9b:1::/48, NAT64 à usage local), et toute adresse dont l'IPv4 embarquée appartient à une
 * plage IPv4 refusée : compatible (::/96), mappée (::ffff:0:0/96), NAT64 (64:ff9b::/96), 6to4
 * (2002::/16).
 *
 * <p>Un hôte introuvable est refusé avec le même motif qu'un hôte interdit, et les messages des
 * refus ne nomment ni l'hôte ni l'adresse IP en cause : l'utilisateur ne peut pas se servir des
 * réponses pour distinguer un nom interne d'un nom inexistant. Le détail reste dans le message
 * technique de {@link RecuperationRefusee}, écrit dans le journal applicatif.
 *
 * <p>{@code argos.veille-web.exceptions} liste des couples {@code hôte:port} exemptés du contrôle
 * des plages et des ports. Vide par défaut, il ne sert qu'au développement et aux tests, où les
 * sources simulées écoutent sur la boucle locale ; une redirection vers un autre port ou un autre
 * hôte local reste refusée.
 */
@ApplicationScoped
public class GardeAdresses {

    /** Résolution DNS, remplaçable dans les tests unitaires. */
    @FunctionalInterface
    interface Resolveur {
        InetAddress[] resoudre(String hote) throws UnknownHostException;
    }

    static final int LONGUEUR_MAX = 2000;

    /** Ports autorisés par défaut. */
    static final List<Integer> PORTS_PAR_DEFAUT = List.of(80, 443);

    private final Resolveur resolveur;
    private final Set<String> exceptions;
    private final Set<Integer> portsAutorises;

    @Inject
    public GardeAdresses(@ConfigProperty(name = "argos.veille-web.exceptions") Optional<List<String>> exceptions,
            @ConfigProperty(name = "argos.veille-web.ports-autorises", defaultValue = "80,443") List<Integer> ports) {
        this(InetAddress::getAllByName, exceptions.orElse(List.of()), ports);
    }

    GardeAdresses(Resolveur resolveur, List<String> exceptions) {
        this(resolveur, exceptions, PORTS_PAR_DEFAUT);
    }

    GardeAdresses(Resolveur resolveur, List<String> exceptions, List<Integer> ports) {
        this.resolveur = resolveur;
        this.exceptions = exceptions.stream().map(exception -> exception.strip().toLowerCase(Locale.ROOT))
                .filter(exception -> !exception.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.portsAutorises = Set.copyOf(ports);
    }

    /** Analyse et vérifie une adresse saisie ; lève {@link RecuperationRefusee} si elle est refusée. */
    public URI verifier(String adresse) {
        return verifier(analyser(adresse), Motif.ADRESSE_INTERDITE);
    }

    /** Vérifie une adresse déjà analysée (adresse de redirection : motif {@code REDIRECTION_INTERDITE}). */
    URI verifier(URI uri, Motif motifRefus) {
        String hote = uri.getHost().toLowerCase(Locale.ROOT);
        int port = port(uri);
        if (exceptions.contains(hote + ":" + port)) {
            return uri;
        }
        if (!portsAutorises.contains(port)) {
            throw new RecuperationRefusee(motifRefus, "port non autorisé : " + hote + ":" + port);
        }
        InetAddress[] adresses;
        try {
            adresses = resolveur.resoudre(hote);
        } catch (UnknownHostException erreur) {
            throw new RecuperationRefusee(motifRefus, "nom d'hôte introuvable : " + hote, erreur);
        }
        if (adresses == null || adresses.length == 0) {
            throw new RecuperationRefusee(motifRefus, "nom d'hôte sans adresse : " + hote);
        }
        for (InetAddress adresse : adresses) {
            if (interdite(adresse)) {
                throw new RecuperationRefusee(motifRefus, "adresse interdite : " + hote + " -> " + adresse.getHostAddress());
            }
        }
        return uri;
    }

    /** Contrôle syntaxique : schéma http(s), hôte présent, pas d'identifiants, longueur bornée. */
    public static URI analyser(String adresse) {
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
        } catch (URISyntaxException malFormee) {
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
        } catch (URISyntaxException | IllegalArgumentException malFormee) {
            throw new RecuperationRefusee(Motif.REDIRECTION_INTERDITE, "Redirection vers une adresse mal formée.");
        } catch (RecuperationRefusee refus) {
            throw new RecuperationRefusee(Motif.REDIRECTION_INTERDITE, "redirection refusée : " + refus.getMessage(), refus);
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
        byte[] octets = adresse.getAddress();
        if (adresse instanceof Inet4Address) {
            return ipv4Interdite(octets);
        }
        if (adresse instanceof Inet6Address) {
            return ipv6Interdite(octets);
        }
        return true;
    }

    private static boolean ipv4Interdite(byte[] octets) {
        int premier = octets[0] & 0xff;
        int deuxieme = octets[1] & 0xff;
        int troisieme = octets[2] & 0xff;
        return premier == 0                                                  // 0.0.0.0/8
                || premier == 10                                             // 10.0.0.0/8
                || premier == 127                                            // 127.0.0.0/8
                || (premier == 100 && deuxieme >= 64 && deuxieme <= 127)     // 100.64.0.0/10
                || (premier == 169 && deuxieme == 254)                       // 169.254.0.0/16
                || (premier == 172 && deuxieme >= 16 && deuxieme <= 31)      // 172.16.0.0/12
                || (premier == 192 && deuxieme == 0 && troisieme == 0)       // 192.0.0.0/24
                || (premier == 192 && deuxieme == 0 && troisieme == 2)       // 192.0.2.0/24
                || (premier == 192 && deuxieme == 88 && troisieme == 99)     // 192.88.99.0/24
                || (premier == 192 && deuxieme == 168)                       // 192.168.0.0/16
                || (premier == 198 && (deuxieme == 18 || deuxieme == 19))    // 198.18.0.0/15
                || (premier == 198 && deuxieme == 51 && troisieme == 100)    // 198.51.100.0/24
                || (premier == 203 && deuxieme == 0 && troisieme == 113)     // 203.0.113.0/24
                || premier >= 224;                                           // multidiffusion, réservées, diffusion
    }

    private static boolean ipv6Interdite(byte[] octets) {
        if (plageIpv6Refusee(octets)) {
            return true;
        }
        byte[] ipv4 = ipv4Embarquee(octets);
        return ipv4 != null && ipv4Interdite(ipv4);
    }

    /** Plages IPv6 refusées quelle que soit l'adresse IPv4 qu'elles pourraient porter. */
    private static boolean plageIpv6Refusee(byte[] octets) {
        int premier = octets[0] & 0xff;
        int deuxieme = octets[1] & 0xff;
        return (premier & 0xfe) == 0xfc                                            // fc00::/7 adresses uniques locales
                || (premier == 0xfe && (deuxieme & 0xc0) == 0xc0)                  // fec0::/10 site local (obsolète)
                || prefixe32(octets, 0x2001, 0x0db8)                               // 2001:db8::/32 documentation
                || prefixe32(octets, 0x2001, 0x0000)                               // 2001::/32 Teredo
                || (prefixe32(octets, 0x0100, 0x0000) && nuls(octets, 4, 8))       // 100::/64 rejet
                || (prefixe32(octets, 0x0064, 0xff9b) && !nuls(octets, 4, 12));    // 64:ff9b::/32 hors /96, dont 64:ff9b:1::/48
    }

    /**
     * IPv4 embarquée d'une adresse 6to4 (2002::/16, octets 2 à 5), mappée (::ffff:0:0/96),
     * compatible (::/96) ou NAT64 bien connue (64:ff9b::/96, octets 12 à 15) ; {@code null} sinon.
     */
    private static byte[] ipv4Embarquee(byte[] octets) {
        if ((octets[0] & 0xff) == 0x20 && (octets[1] & 0xff) == 0x02) {
            return new byte[] { octets[2], octets[3], octets[4], octets[5] };
        }
        boolean dixPremiersNuls = nuls(octets, 0, 10);
        boolean mappee = dixPremiersNuls && (octets[10] & 0xff) == 0xff && (octets[11] & 0xff) == 0xff;
        boolean compatible = dixPremiersNuls && octets[10] == 0 && octets[11] == 0;
        if (mappee || compatible || prefixe32(octets, 0x0064, 0xff9b)) {
            return new byte[] { octets[12], octets[13], octets[14], octets[15] };
        }
        return null;
    }

    /** Vrai si les deux premiers groupes de 16 bits valent {@code groupe1} et {@code groupe2}. */
    private static boolean prefixe32(byte[] octets, int groupe1, int groupe2) {
        return (((octets[0] & 0xff) << 8) | (octets[1] & 0xff)) == groupe1
                && (((octets[2] & 0xff) << 8) | (octets[3] & 0xff)) == groupe2;
    }

    /** Vrai si les octets de {@code debut} (inclus) à {@code fin} (exclu) sont nuls. */
    private static boolean nuls(byte[] octets, int debut, int fin) {
        for (int indice = debut; indice < fin; indice++) {
            if (octets[indice] != 0) {
                return false;
            }
        }
        return true;
    }
}
