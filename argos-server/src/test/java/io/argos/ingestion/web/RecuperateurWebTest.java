package io.argos.ingestion.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/**
 * Récupération bornée (WEB-03, WEB-07) : aucune requête vers une adresse interdite, redirections vérifiées une à
 * une, délai et taille bornés ; le transport reçoit les adresses IP de l'unique résolution contrôlée, même si le
 * nom change de résolution ensuite (rebinding DNS). Transport simulé : aucun appel réseau.
 */
class RecuperateurWebTest {

    private static final MinuterieCoupure MINUTERIE = minuterieDemarree();

    private static final Map<String, String> DNS = Map.of("flux.exemple.test", "93.184.216.34",
            "autre.exemple.test", "93.184.216.35");
    private static final String ADRESSE_PUBLIQUE = "93.184.216.34";
    private static final String SECONDE_ADRESSE_PUBLIQUE = "93.184.216.35";
    private static final String BOUCLE_LOCALE = "127.0.0.1";
    private static final String ADRESSE_PRIVEE = "192.168.1.10";
    private static final long TAILLE_MAX = 1000;
    private static final int REDIRECTIONS_MAX = 3;
    private static final long MUET_ATTENTE_MAX_SECONDES = 30;
    private static final long DUREE_MAX_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** Transport simulé qui enregistre chaque destination reçue. */
    private static final class TransportSimule implements RecuperateurWeb.Transport {
        final List<DestinationControlee> destinations = new ArrayList<>();
        final Function<URI, RecuperateurWeb.Reponse> reponses;

        TransportSimule(Function<URI, RecuperateurWeb.Reponse> reponses) {
            this.reponses = reponses;
        }

        List<URI> requetes() {
            return destinations.stream().map(DestinationControlee::uri).toList();
        }

        @Override
        public RecuperateurWeb.Reponse envoyer(DestinationControlee destination, Duration delai) throws HttpTimeoutException {
            destinations.add(destination);
            RecuperateurWeb.Reponse reponse = reponses.apply(destination.uri());
            if (reponse == null) {
                throw new HttpTimeoutException("délai");
            }
            return reponse;
        }
    }

    private static RecuperateurWeb.Reponse succes(String corps) {
        return new RecuperateurWeb.Reponse(200, Optional.empty(), Optional.of("application/atom+xml"),
                new ByteArrayInputStream(corps.getBytes(StandardCharsets.UTF_8)));
    }

    private static RecuperateurWeb.Reponse redirection(String location) {
        return new RecuperateurWeb.Reponse(302, Optional.of(location), Optional.empty(), InputStream.nullInputStream());
    }

    private static RecuperateurWeb recuperateur(TransportSimule transport, long tailleMax) {
        GardeAdresses garde = new GardeAdresses(hote -> {
            String adresseIp = DNS.get(hote);
            if (adresseIp == null) {
                if (hote.matches("[0-9.]+")) {
                    return InetAddress.getAllByName(hote);
                }
                throw new UnknownHostException(hote);
            }
            return new InetAddress[] { InetAddress.getByName(adresseIp) };
        }, List.of());
        return new RecuperateurWeb(garde, transport, bornes(tailleMax), MINUTERIE);
    }

    private static RecuperateurWeb recuperateur(TransportSimule transport, GardeAdresses.Resolveur resolveur) {
        return new RecuperateurWeb(new GardeAdresses(resolveur, List.of()), transport, bornes(TAILLE_MAX), MINUTERIE);
    }

    private static RecuperateurWeb.Bornes bornes(long tailleMax) {
        return new RecuperateurWeb.Bornes(Duration.ofSeconds(1), REDIRECTIONS_MAX, tailleMax);
    }

    private static List<String> adressesIp(DestinationControlee destination) {
        return destination.adresses().stream().map(InetAddress::getHostAddress).toList();
    }

    /** Minuterie de coupure des tests, démarrée comme par le conteneur CDI et arrêtée après la classe. */
    private static MinuterieCoupure minuterieDemarree() {
        MinuterieCoupure minuterie = new MinuterieCoupure();
        minuterie.fils = 1;
        minuterie.demarrer();
        return minuterie;
    }

    @AfterAll
    static void arreterMinuterie() {
        MINUTERIE.arreter();
    }

    /** AUD-QUA-11 : la minuterie appartient à son bean et s'arrête avec lui. */
    @Test
    void laMinuterieSArreteALaDestructionDuBean() {
        MinuterieCoupure minuterie = minuterieDemarree();
        assertFalse(minuterie.arretee());
        minuterie.arreter();
        assertTrue(minuterie.arretee());
    }

    @Test
    void adressePriveeRefuseeSansAucuneRequete() {
        TransportSimule transport = new TransportSimule(uri -> succes("jamais"));
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class,
                () -> recuperateur(transport, TAILLE_MAX).recuperer("http://192.168.1.1/flux"));
        assertEquals(Motif.ADRESSE_INTERDITE, refus.motif());
        assertTrue(transport.destinations.isEmpty(), "aucune requête vers 192.168.1.1");
    }

    @Test
    void redirectionVersLaBoucleLocaleInterrompue() {
        TransportSimule transport = new TransportSimule(uri -> redirection("http://127.0.0.1/"));
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class,
                () -> recuperateur(transport, TAILLE_MAX).recuperer("https://flux.exemple.test/atom.xml"));
        assertEquals(Motif.REDIRECTION_INTERDITE, refus.motif());
        assertEquals(List.of(URI.create("https://flux.exemple.test/atom.xml")), transport.requetes(),
                "seule l'adresse publique a été appelée");
    }

    @Test
    void redirectionPubliqueSuivieEtNombreDeRedirectionsBorne() {
        TransportSimule suivie = new TransportSimule(uri -> uri.getHost().equals("flux.exemple.test")
                ? redirection("https://autre.exemple.test/flux") : succes("<feed/>"));
        RecuperateurWeb.Contenu contenu = recuperateur(suivie, TAILLE_MAX).recuperer("https://flux.exemple.test/");
        assertEquals(URI.create("https://autre.exemple.test/flux"), contenu.adresseFinale());
        assertArrayEquals("<feed/>".getBytes(StandardCharsets.UTF_8), contenu.octets());

        TransportSimule boucle = new TransportSimule(uri -> redirection("https://flux.exemple.test/encore"));
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class,
                () -> recuperateur(boucle, TAILLE_MAX).recuperer("https://flux.exemple.test/"));
        assertEquals(Motif.TROP_DE_REDIRECTIONS, refus.motif());
        assertEquals(REDIRECTIONS_MAX + 1, boucle.destinations.size(), "requête initiale et 3 redirections au plus");
    }

    @Test
    void delaiEtTailleBornes() {
        TransportSimule lent = new TransportSimule(uri -> null);
        assertEquals(Motif.DELAI_DEPASSE, assertThrows(RecuperationRefusee.class,
                () -> recuperateur(lent, TAILLE_MAX).recuperer("https://flux.exemple.test/")).motif());

        TransportSimule gros = new TransportSimule(uri -> succes("x".repeat(5000)));
        assertEquals(Motif.CONTENU_TROP_VOLUMINEUX, assertThrows(RecuperationRefusee.class,
                () -> recuperateur(gros, TAILLE_MAX).recuperer("https://flux.exemple.test/")).motif());
    }

    @Test
    void reponseHttpEnErreurRefusee() {
        TransportSimule absent = new TransportSimule(uri -> new RecuperateurWeb.Reponse(404, Optional.empty(),
                Optional.empty(), InputStream.nullInputStream()));
        assertEquals(Motif.REPONSE_HTTP, assertThrows(RecuperationRefusee.class,
                () -> recuperateur(absent, TAILLE_MAX).recuperer("https://flux.exemple.test/")).motif());
    }

    /** Un serveur qui envoie ses en-têtes puis se tait : la lecture est interrompue à l'échéance. */
    @Test
    void corpsMuetInterrompuALEcheance() {
        CountDownLatch ferme = new CountDownLatch(1);
        InputStream muet = new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    // Bloqué jusqu'à des données ou la fermeture, comme le corps d'une réponse réseau.
                    if (ferme.await(MUET_ATTENTE_MAX_SECONDES, TimeUnit.SECONDS)) {
                        throw new IOException("flux fermé");
                    }
                    return -1;
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interruption);
                }
            }

            @Override
            public void close() {
                ferme.countDown();
            }
        };
        TransportSimule silencieux = new TransportSimule(uri -> new RecuperateurWeb.Reponse(200, Optional.empty(),
                Optional.of("application/atom+xml"), muet));
        long debut = System.nanoTime();
        assertEquals(Motif.DELAI_DEPASSE, assertThrows(RecuperationRefusee.class,
                () -> recuperateur(silencieux, TAILLE_MAX).recuperer("https://flux.exemple.test/")).motif());
        assertTrue(System.nanoTime() - debut < DUREE_MAX_NANOS);
    }

    /** WEB-07-S1 : la connexion vise l'adresse publique contrôlée, pas la boucle locale d'une résolution suivante. */
    @Test
    void rebindingVersLaBoucleLocaleSansEffet() {
        ResolveurChangeant resolveur = ResolveurChangeant.successives(
                Map.of("rebind.exemple.test", List.of(ADRESSE_PUBLIQUE, BOUCLE_LOCALE)));
        TransportSimule transport = new TransportSimule(uri -> succes("<feed/>"));
        recuperateur(transport, resolveur).recuperer("https://rebind.exemple.test/flux");
        assertEquals(1, transport.destinations.size());
        assertEquals(List.of(ADRESSE_PUBLIQUE), adressesIp(transport.destinations.get(0)));
        assertEquals(1, resolveur.appels("rebind.exemple.test"), "une seule résolution par saut");
    }

    /** WEB-07-S2 : après une redirection, la connexion vise l'adresse contrôlée du second hôte. */
    @Test
    void rebindingApresRedirectionSansEffet() {
        ResolveurChangeant resolveur = ResolveurChangeant.successives(Map.of(
                "flux.exemple.test", List.of(ADRESSE_PUBLIQUE),
                "second.exemple.test", List.of(SECONDE_ADRESSE_PUBLIQUE, ADRESSE_PRIVEE)));
        TransportSimule transport = new TransportSimule(uri -> uri.getHost().equals("flux.exemple.test")
                ? redirection("https://second.exemple.test/flux") : succes("<feed/>"));
        RecuperateurWeb.Contenu contenu = recuperateur(transport, resolveur).recuperer("https://flux.exemple.test/");
        assertEquals(URI.create("https://second.exemple.test/flux"), contenu.adresseFinale());
        assertEquals(List.of(SECONDE_ADRESSE_PUBLIQUE), adressesIp(transport.destinations.get(1)));
        assertFalse(transport.destinations.stream().flatMap(destination -> adressesIp(destination).stream())
                .anyMatch(ADRESSE_PRIVEE::equals), "aucune connexion vers l'adresse privée");
        assertEquals(1, resolveur.appels("second.exemple.test"));
    }

    /** WEB-07-S3 : redirection vers un hôte résolu vers une adresse privée dès le contrôle. */
    @Test
    void redirectionResolueVersUneAdressePriveeInterrompue() {
        ResolveurChangeant resolveur = ResolveurChangeant.successives(Map.of(
                "flux.exemple.test", List.of(ADRESSE_PUBLIQUE),
                "interne.exemple.test", List.of(ADRESSE_PRIVEE)));
        TransportSimule transport = new TransportSimule(uri -> redirection("https://interne.exemple.test/"));
        RecuperationRefusee refus = assertThrows(RecuperationRefusee.class,
                () -> recuperateur(transport, resolveur).recuperer("https://flux.exemple.test/"));
        assertEquals(Motif.REDIRECTION_INTERDITE, refus.motif());
        assertFalse(refus.motifPublic().contains("interne.exemple.test"), "message sans hôte");
        assertFalse(refus.motifPublic().contains(ADRESSE_PRIVEE), "message sans adresse IP");
        assertEquals(List.of(URI.create("https://flux.exemple.test/")), transport.requetes(),
                "aucune connexion vers l'hôte interdit");
    }

    /** I-2 : un résolveur qui échoue à tout appel suivant n'empêche pas la récupération, faite sans nouvelle résolution. */
    @Test
    void aucuneSecondeResolutionDuNom() {
        ResolveurChangeant resolveur = ResolveurChangeant.premierAppelSeulement("unique.exemple.test", ADRESSE_PUBLIQUE);
        TransportSimule transport = new TransportSimule(uri -> succes("<feed/>"));
        RecuperateurWeb.Contenu contenu = recuperateur(transport, resolveur).recuperer("https://unique.exemple.test/");
        assertArrayEquals("<feed/>".getBytes(StandardCharsets.UTF_8), contenu.octets());
        assertEquals(1, resolveur.appels("unique.exemple.test"));
    }
}
