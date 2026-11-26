package io.argos.ingestion.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Bornes des plages d'adresses refusées par la garde de la veille web (WEB-03) : première et dernière adresse de
 * chaque plage IPv4 réservée et leurs voisines publiques, plages IPv6 refusées et IPv4 embarquées (6to4, compatible,
 * NAT64) ; redirections et destinations contrôlées.
 */
class PlagesAdressesTest {

    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTPS = 443;
    private static final int PORT_EXPLICITE = 8443;

    @ParameterizedTest
    @ValueSource(strings = {"0.1.2.3", "100.64.0.1", "100.127.255.254", "169.254.1.1", "172.16.0.1", "172.31.255.254",
        "192.0.0.8", "192.0.2.1", "192.88.99.1", "192.168.1.1", "198.18.0.1", "198.19.255.254", "198.51.100.1",
        "203.0.113.1", "224.0.0.1", "240.0.0.1", "255.255.255.255"})
    void plagesIpv4Refusees(String adresse) throws UnknownHostException {
        assertTrue(GardeAdresses.interdite(InetAddress.getByName(adresse)), adresse);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "100.63.255.254", "100.128.0.1", "169.253.1.1", "172.15.255.254", "172.32.0.1",
        "192.0.1.1", "192.0.3.1", "192.88.98.1", "192.167.1.1", "198.17.0.1", "198.20.0.1", "198.51.99.1",
        "203.0.112.1", "223.255.255.254", "193.0.0.1", "199.51.100.1", "204.0.113.1"})
    void voisinesPubliquesAcceptees(String adresse) throws UnknownHostException {
        assertFalse(GardeAdresses.interdite(InetAddress.getByName(adresse)), adresse);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fd00::1", "fc00::1", "2001:db8::1", "2001::1", "100::1", "64:ff9b:1::1", "64:ff9b::a00:1",
        "2002:a00:1::", "::a00:1", "::7f00:1", "fe80::1", "ff02::1", "::1"})
    void plagesIpv6EtIpv4EmbarqueesRefusees(String adresse) throws UnknownHostException {
        assertTrue(GardeAdresses.interdite(InetAddress.getByName(adresse)), adresse);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2606:4700::1", "2002:808:808::", "64:ff9b::808:808", "::808:808", "100:0:0:1::",
        "2001:db9::1", "2001:1::1", "64:ff9a::1"})
    void adressesIpv6PubliquesAcceptees(String adresse) throws UnknownHostException {
        assertFalse(GardeAdresses.interdite(InetAddress.getByName(adresse)), adresse);
    }

    @Test
    void redirectionSansDestinationMalFormeeOuInterditeRefusee() {
        URI courante = URI.create("https://exemple.org/flux");
        for (String location : new String[] {null, " ", "http://[mal", "ftp://exemple.org/", "https://identifiant@exemple.org/"}) {
            RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> GardeAdresses.redirection(courante, location),
                    String.valueOf(location));
            assertEquals(RecuperationRefusee.Motif.REDIRECTION_INTERDITE, refus.motif(), String.valueOf(location));
        }
        assertEquals(URI.create("https://exemple.org/autre"), GardeAdresses.redirection(courante, "/autre"));
    }

    @Test
    void portImpliciteSelonLeSchema() {
        assertEquals(PORT_HTTP, GardeAdresses.port(URI.create("http://exemple.org/")));
        assertEquals(PORT_HTTPS, GardeAdresses.port(URI.create("HTTPS://exemple.org/")));
        assertEquals(PORT_EXPLICITE, GardeAdresses.port(URI.create("https://exemple.org:8443/")));
    }

    @Test
    void destinationControleeCheminRequeteEtChiffrement() throws UnknownHostException {
        List<InetAddress> adresses = List.of(InetAddress.getByName("8.8.8.8"));
        DestinationControlee racine = new DestinationControlee(URI.create("http://exemple.org"), adresses);
        assertEquals("/", racine.cheminEtRequete());
        assertFalse(racine.chiffree());
        assertEquals(PORT_HTTP, racine.port());
        assertEquals("exemple.org", racine.hote());

        DestinationControlee avecRequete = new DestinationControlee(URI.create("https://exemple.org/flux?page=2"), adresses);
        assertEquals("/flux?page=2", avecRequete.cheminEtRequete());
        assertTrue(avecRequete.chiffree());

        assertThrows(IllegalArgumentException.class, () -> new DestinationControlee(URI.create("https://exemple.org/"), List.of()));
    }
}
