package io.argos.ingestion.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
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
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/**
 * Récupération bornée (WEB-03, tâche 2.1) : aucune requête vers une adresse interdite, redirections
 * vérifiées une à une, délai et taille bornés. Transport simulé : aucun appel réseau.
 */
class RecuperateurWebTest {

    private static final Map<String, String> DNS = Map.of("flux.exemple.test", "93.184.216.34",
            "autre.exemple.test", "93.184.216.35");

    /** Transport simulé qui enregistre chaque requête émise. */
    private static final class TransportSimule implements RecuperateurWeb.Transport {
        final List<URI> requetes = new ArrayList<>();
        final Function<URI, RecuperateurWeb.Reponse> reponses;

        TransportSimule(Function<URI, RecuperateurWeb.Reponse> reponses) {
            this.reponses = reponses;
        }

        @Override
        public RecuperateurWeb.Reponse envoyer(URI uri, Duration delai) throws HttpTimeoutException {
            requetes.add(uri);
            RecuperateurWeb.Reponse r = reponses.apply(uri);
            if (r == null) {
                throw new HttpTimeoutException("délai");
            }
            return r;
        }
    }

    private static RecuperateurWeb.Reponse ok(String corps) {
        return new RecuperateurWeb.Reponse(200, Optional.empty(), Optional.of("application/atom+xml"),
                new ByteArrayInputStream(corps.getBytes(StandardCharsets.UTF_8)));
    }

    private static RecuperateurWeb.Reponse redirection(String location) {
        return new RecuperateurWeb.Reponse(302, Optional.of(location), Optional.empty(), InputStream.nullInputStream());
    }

    private static RecuperateurWeb recuperateur(TransportSimule transport, long tailleMax) {
        GardeAdresses garde = new GardeAdresses(hote -> {
            String ip = DNS.get(hote);
            if (ip == null) {
                if (hote.matches("[0-9.]+")) {
                    return InetAddress.getAllByName(hote);
                }
                throw new UnknownHostException(hote);
            }
            return new InetAddress[] { InetAddress.getByName(ip) };
        }, List.of());
        return new RecuperateurWeb(garde, transport, Duration.ofSeconds(1), 3, tailleMax);
    }

    @Test
    void adressePriveeRefuseeSansAucuneRequete() {
        TransportSimule transport = new TransportSimule(uri -> ok("jamais"));
        RecuperationRefusee e = assertThrows(RecuperationRefusee.class,
                () -> recuperateur(transport, 1000).recuperer("http://192.168.1.1/flux"));
        assertEquals(Motif.ADRESSE_INTERDITE, e.motif());
        assertTrue(transport.requetes.isEmpty(), "aucune requête vers 192.168.1.1");
    }

    @Test
    void redirectionVersLaBoucleLocaleInterrompue() {
        TransportSimule transport = new TransportSimule(uri -> redirection("http://127.0.0.1/"));
        RecuperationRefusee e = assertThrows(RecuperationRefusee.class,
                () -> recuperateur(transport, 1000).recuperer("https://flux.exemple.test/atom.xml"));
        assertEquals(Motif.REDIRECTION_INTERDITE, e.motif());
        assertEquals(List.of(URI.create("https://flux.exemple.test/atom.xml")), transport.requetes,
                "seule l'adresse publique a été appelée");
    }

    @Test
    void redirectionPubliqueSuivieEtNombreDeRedirectionsBorne() {
        TransportSimule suivie = new TransportSimule(uri -> uri.getHost().equals("flux.exemple.test")
                ? redirection("https://autre.exemple.test/flux") : ok("<feed/>"));
        RecuperateurWeb.Contenu contenu = recuperateur(suivie, 1000).recuperer("https://flux.exemple.test/");
        assertEquals(URI.create("https://autre.exemple.test/flux"), contenu.adresseFinale());
        assertArrayEquals("<feed/>".getBytes(StandardCharsets.UTF_8), contenu.octets());

        TransportSimule boucle = new TransportSimule(uri -> redirection("https://flux.exemple.test/encore"));
        RecuperationRefusee e = assertThrows(RecuperationRefusee.class,
                () -> recuperateur(boucle, 1000).recuperer("https://flux.exemple.test/"));
        assertEquals(Motif.TROP_DE_REDIRECTIONS, e.motif());
        assertEquals(4, boucle.requetes.size(), "requête initiale et 3 redirections au plus");
    }

    @Test
    void delaiEtTailleBornes() {
        TransportSimule lent = new TransportSimule(uri -> null);
        assertEquals(Motif.DELAI_DEPASSE, assertThrows(RecuperationRefusee.class,
                () -> recuperateur(lent, 1000).recuperer("https://flux.exemple.test/")).motif());

        TransportSimule gros = new TransportSimule(uri -> ok("x".repeat(5000)));
        assertEquals(Motif.CONTENU_TROP_VOLUMINEUX, assertThrows(RecuperationRefusee.class,
                () -> recuperateur(gros, 1000).recuperer("https://flux.exemple.test/")).motif());
    }

    @Test
    void reponseHttpEnErreurRefusee() {
        TransportSimule absent = new TransportSimule(uri -> new RecuperateurWeb.Reponse(404, Optional.empty(),
                Optional.empty(), InputStream.nullInputStream()));
        assertEquals(Motif.REPONSE_HTTP, assertThrows(RecuperationRefusee.class,
                () -> recuperateur(absent, 1000).recuperer("https://flux.exemple.test/")).motif());
    }
}
