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
            String adresseIp = table.get(hote);
            if (adresseIp == null) {
                // Adresses littérales : aucune requête DNS n'est émise par InetAddress pour une IP.
                if (hote.matches("[0-9.]+") || hote.startsWith("[")) {
                    return InetAddress.getAllByName(hote);
                }
                throw new UnknownHostException(hote);
            }
            return new InetAddress[] { InetAddress.getByName(adresseIp) };
        }
    }

    private static GardeAdresses garde(Map<String, String> table, List<String> exceptions) {
        return new GardeAdresses(new ResolveurSimule(table), exceptions);
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://192.168.1.1/flux", "http://10.0.0.1/", "http://172.16.5.4/", "http://127.0.0.1/",
            "http://127.1.2.3:8080/", "http://169.254.169.254/latest/meta-data/", "http://0.0.0.0/",
            "http://100.64.0.1/", "http://224.0.0.1/", "http://[::1]/", "http://[fe80::1]/", "http://[fd00::1]/",
            "http://[::ffff:192.168.1.1]/", "http://[64:ff9b::a00:1]/", "http://198.18.0.1/", "http://255.255.255.255/",
            // 6to4 (IPv4 embarquée 192.168.1.1, 127.0.0.1, 10.0.0.1), Teredo, NAT64 local, rejet, NAT64 vers la boucle.
            "http://[2002:c0a8:101::1]/", "http://[2002:7f00:1::]/", "http://[2002:a00:1:1::1]/",
            "http://[2001:0:4136:e378:8000:63bf:3fff:fdd2]/", "http://[64:ff9b:1::a00:1]/", "http://[64:ff9b:1:2::5db8:d822]/",
            "http://[100::1]/", "http://[100::ffff:1]/", "http://[64:ff9b::7f00:1]/", "http://[64:ff9b::c0a8:101]/" })
    void adressesPriveesLocalesEtReserveesRefusees(String adresse) {
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> garde(Map.of(), List.of()).verifier(adresse));
        assertEquals(Motif.ADRESSE_INTERDITE, refus.motif(), adresse);
    }

    @Test
    void nomPublicQuiPointeVersUnReseauPriveRefuse() {
        GardeAdresses garde = garde(Map.of("intranet.exemple.test", "10.1.2.3"), List.of());
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> garde.verifier("https://intranet.exemple.test/flux"));
        assertEquals(Motif.ADRESSE_INTERDITE, refus.motif());
    }

    @Test
    void adressePubliqueAcceptee() {
        GardeAdresses garde = garde(Map.of("blog.exemple.test", "93.184.216.34"), List.of());
        assertEquals(URI.create("https://blog.exemple.test/feed.xml"), garde.verifier("https://blog.exemple.test/feed.xml").uri());
    }

    @ParameterizedTest
    @ValueSource(strings = { "file:///etc/passwd", "ftp://exemple.test/", "gopher://exemple.test/", "javascript:alert(1)",
            "http://user:secret@exemple.test/", "pas une adresse", "http:///chemin", "" })
    void schemasEtFormesRefuses(String adresse) {
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> garde(Map.of(), List.of()).verifier(adresse));
        assertEquals(Motif.ADRESSE_INVALIDE, refus.motif(), adresse);
    }

    @Test
    void exceptionLimiteeAuCoupleHotePort() {
        GardeAdresses garde = garde(Map.of("localhost", "127.0.0.1"), List.of("localhost:8089"));
        assertDoesNotThrow(() -> garde.verifier("http://localhost:8089/flux"));
        assertThrows(RecuperationRefusee.class, () -> garde.verifier("http://localhost:8090/flux"));
        assertThrows(RecuperationRefusee.class, () -> garde.verifier("http://127.0.0.1:8089/flux"));
    }

    @Test
    void redirectionVersUneAdresseLocaleRefuseeAvecSonMotif() {
        GardeAdresses garde = garde(Map.of("flux.exemple.test", "93.184.216.34"), List.of());
        URI suivante = GardeAdresses.redirection(URI.create("https://flux.exemple.test/a"), "http://127.0.0.1/");
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> garde.verifier(suivante, Motif.REDIRECTION_INTERDITE));
        assertEquals(Motif.REDIRECTION_INTERDITE, refus.motif());
        RecuperationRefusee schema = assertThrows(RecuperationRefusee.class,
                () -> GardeAdresses.redirection(URI.create("https://flux.exemple.test/a"), "file:///etc/passwd"));
        assertEquals(Motif.REDIRECTION_INTERDITE, schema.motif());
    }

    /** Ports : 80 et 443 par défaut ; tout autre port est refusé, avant toute résolution DNS. */
    @Test
    void portsLimitesAuxPortsAutorises() {
        ResolveurSimule resolveur = new ResolveurSimule(Map.of("blog.exemple.test", "93.184.216.34"));
        GardeAdresses garde = new GardeAdresses(resolveur, List.of());
        assertDoesNotThrow(() -> garde.verifier("http://blog.exemple.test/flux"));
        assertDoesNotThrow(() -> garde.verifier("https://blog.exemple.test:443/flux"));
        assertDoesNotThrow(() -> garde.verifier("http://blog.exemple.test:80/flux"));
        int demandes = resolveur.demandes.size();
        for (String adresse : List.of("http://blog.exemple.test:8080/", "https://blog.exemple.test:22/",
                "http://blog.exemple.test:6379/", "https://blog.exemple.test:8443/")) {
            RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> garde.verifier(adresse));
            assertEquals(Motif.ADRESSE_INTERDITE, refus.motif(), adresse);
        }
        assertEquals(demandes, resolveur.demandes.size(), "aucune résolution DNS pour un port refusé");
        // Liste paramétrable.
        GardeAdresses g8080 = new GardeAdresses(resolveur, List.of(), List.of(8080));
        assertDoesNotThrow(() -> g8080.verifier("http://blog.exemple.test:8080/"));
        assertThrows(RecuperationRefusee.class, () -> g8080.verifier("http://blog.exemple.test/"));
        // Une redirection vers un port non autorisé est refusée avec le motif de la redirection.
        URI suivante = GardeAdresses.redirection(URI.create("https://blog.exemple.test/a"), "http://blog.exemple.test:6379/");
        assertEquals(Motif.REDIRECTION_INTERDITE, assertThrows(RecuperationRefusee.class,
                () -> garde.verifier(suivante, Motif.REDIRECTION_INTERDITE)).motif());
    }

    /**
     * Un hôte introuvable et un hôte privé reçoivent le même motif et le même message, sans nom
     * d'hôte ni adresse IP : la réponse ne permet pas de sonder les noms internes.
     */
    @Test
    void hoteIntrouvableIndiscernableDUnHoteInterdit() {
        GardeAdresses garde = garde(Map.of("intranet.exemple.test", "10.1.2.3"), List.of());
        RecuperationRefusee prive = assertThrows(RecuperationRefusee.class, () -> garde.verifier("https://intranet.exemple.test/"));
        RecuperationRefusee inconnu = assertThrows(RecuperationRefusee.class, () -> garde.verifier("https://inconnu.exemple.test/"));
        assertEquals(Motif.ADRESSE_INTERDITE, prive.motif());
        assertEquals(Motif.ADRESSE_INTERDITE, inconnu.motif());
        assertEquals(prive.message(), inconnu.message());
        for (RecuperationRefusee refus : List.of(prive, inconnu)) {
            assertFalse(refus.message().contains("exemple.test"), refus.message());
            assertFalse(refus.message().contains("10.1.2.3"), refus.message());
            assertTrue(refus.getMessage().contains("exemple.test"), "détail conservé pour le journal applicatif");
        }
    }

    @Test
    void plagesPubliquesNonRefusees() throws Exception {
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("93.184.216.34")));
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946")));
        assertTrue(GardeAdresses.interdite(InetAddress.getByName("192.0.2.10")));
        assertTrue(GardeAdresses.interdite(InetAddress.getByName("2001:db8::1")));
        // 6to4 et NAT64 bien connu vers une IPv4 publique (93.184.216.34) : acceptés.
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("2002:5db8:d822::1")));
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("64:ff9b::5db8:d822")));
        // 2001:4860:: (hors Teredo 2001::/32) : public.
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("2001:4860:4860::8888")));
        // 100::/64 seulement : 100:0:0:1:: est hors de la plage de rejet.
        assertFalse(GardeAdresses.interdite(InetAddress.getByName("100:0:0:1::1")));
    }
}
