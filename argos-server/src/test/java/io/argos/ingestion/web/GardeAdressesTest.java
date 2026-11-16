package io.argos.ingestion.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/**
 * Protection SSRF (WEB-03, R-12, tâche 2.1) : plages refusées après résolution DNS, schémas et
 * identifiants refusés, exceptions limitées au couple hôte:port. Résolution DNS simulée : aucun
 * appel réseau.
 */
class GardeAdressesTest {

    /** Résolveur simulé qui enregistre les noms demandés. */
    private static final class ResolveurSimule implements GardeAdresses.Resolveur {
        final Map<String, String> table;
        final List<String> demandes = new ArrayList<>();

        ResolveurSimule(Map<String, String> table) {
            this.table = table;
        }

        @Override
        public InetAddress[] resoudre(String hote) throws UnknownHostException {
            demandes.add(hote);
            String ip = table.get(hote);
            if (ip == null) {
                // Adresses littérales : aucune requête DNS n'est émise par InetAddress pour une IP.
                if (hote.matches("[0-9.]+") || hote.startsWith("[")) {
                    return InetAddress.getAllByName(hote);
                }
                throw new UnknownHostException(hote);
            }
            return new InetAddress[] { InetAddress.getByName(ip) };
        }
    }

    private static GardeAdresses garde(Map<String, String> table, List<String> exceptions) {
        return new GardeAdresses(new ResolveurSimule(table), exceptions);
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://192.168.1.1/flux", "http://10.0.0.1/", "http://172.16.5.4/", "http://127.0.0.1/",
            "http://127.1.2.3:8080/", "http://169.254.169.254/latest/meta-data/", "http://0.0.0.0/",
            "http://100.64.0.1/", "http://224.0.0.1/", "http://[::1]/", "http://[fe80::1]/", "http://[fd00::1]/",
            "http://[::ffff:192.168.1.1]/", "http://[64:ff9b::a00:1]/", "http://198.18.0.1/", "http://255.255.255.255/" })
    void adressesPriveesLocalesEtReserveesRefusees(String adresse) {
        RecuperationRefusee e = assertThrows(RecuperationRefusee.class, () -> garde(Map.of(), List.of()).verifier(adresse));
        assertEquals(Motif.ADRESSE_INTERDITE, e.motif(), adresse);
    }

    @Test
    void nomPublicQuiPointeVersUnReseauPriveRefuse() {
        GardeAdresses g = garde(Map.of("intranet.exemple.test", "10.1.2.3"), List.of());
        RecuperationRefusee e = assertThrows(RecuperationRefusee.class, () -> g.verifier("https://intranet.exemple.test/flux"));
        assertEquals(Motif.ADRESSE_INTERDITE, e.motif());
    }

    @Test
    void adressePubliqueAcceptee() {
        GardeAdresses g = garde(Map.of("blog.exemple.test", "93.184.216.34"), List.of());
        assertEquals(URI.create("https://blog.exemple.test/feed.xml"), g.verifier("https://blog.exemple.test/feed.xml"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "file:///etc/passwd", "ftp://exemple.test/", "gopher://exemple.test/", "javascript:alert(1)",
            "http://user:secret@exemple.test/", "pas une adresse", "http:///chemin", "" })
    void schemasEtFormesRefuses(String adresse) {
        RecuperationRefusee e = assertThrows(RecuperationRefusee.class, () -> garde(Map.of(), List.of()).verifier(adresse));
        assertEquals(Motif.ADRESSE_INVALIDE, e.motif(), adresse);
    }

    @Test
    void exceptionLimiteeAuCoupleHotePort() {
        GardeAdresses g = garde(Map.of("localhost", "127.0.0.1"), List.of("localhost:8089"));
        assertDoesNotThrow(() -> g.verifier("http://localhost:8089/flux"));
        assertThrows(RecuperationRefusee.class, () -> g.verifier("http://localhost:8090/flux"));
        assertThrows(RecuperationRefusee.class, () -> g.verifier("http://127.0.0.1:8089/flux"));
    }

    @Test
    void redirectionVersUneAdresseLocaleRefuseeAvecSonMotif() {
        GardeAdresses g = garde(Map.of("flux.exemple.test", "93.184.216.34"), List.of());
        URI suivante = GardeAdresses.redirection(URI.create("https://flux.exemple.test/a"), "http://127.0.0.1/");
        RecuperationRefusee e = assertThrows(RecuperationRefusee.class, () -> g.verifier(suivante, Motif.REDIRECTION_INTERDITE));
        assertEquals(Motif.REDIRECTION_INTERDITE, e.motif());
        RecuperationRefusee schema = assertThrows(RecuperationRefusee.class,
                () -> GardeAdresses.redirection(URI.create("https://flux.exemple.test/a"), "file:///etc/passwd"));
        assertEquals(Motif.REDIRECTION_INTERDITE, schema.motif());
    }

    @Test
    void plagesPubliquesNonRefusees() throws Exception {
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("93.184.216.34")));
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946")));
        assertTrue(GardeAdresses.interdite(InetAddress.getByName("192.0.2.10")));
        assertTrue(GardeAdresses.interdite(InetAddress.getByName("2001:db8::1")));
    }
}
