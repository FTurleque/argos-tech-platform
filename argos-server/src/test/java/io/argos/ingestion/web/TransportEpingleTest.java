package io.argos.ingestion.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.StandardConstants;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import io.argos.ingestion.web.RecuperationRefusee.Motif;
import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;

/**
 * Transport épinglé de production (WEB-07, tâches 2.3 et 2.4 de {@code epingler-adresse-veille-web}) sur des
 * serveurs HTTP et HTTPS locaux, sans requête vers Internet : la connexion vise l'adresse IP contrôlée même si le
 * nom se résout ensuite ailleurs (rebinding DNS, avec et sans redirection), aucune résolution n'est faite par le
 * transport, les adresses suivantes ne sont essayées qu'après un échec de connexion, les bornes de délai et de
 * taille tiennent, et le certificat est vérifié pour le nom d'hôte d'origine, porté par SNI et l'en-tête Host.
 *
 * <p>Deux adresses de la boucle locale servent de « bonne » et de « mauvaise » destination : 127.0.0.1 et
 * 127.0.0.2, sur un même port. Leurs couples {@code hôte:port} sont exemptés du refus des adresses locales, qui
 * n'est pas l'objet de ces tests (il est couvert par {@code GardeAdressesTest} et {@code RecuperateurWebTest}).
 */
class TransportEpingleTest {

    private static final String BONNE_ADRESSE = "127.0.0.1";
    private static final String MAUVAISE_ADRESSE = "127.0.0.2";
    private static final String FLUX = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>Argos</title></feed>";
    private static final Duration DELAI = Duration.ofSeconds(2);
    private static final long TAILLE_MAX = 1000;
    private static final int REDIRECTIONS_MAX = 5;
    private static final int ESSAIS_PORT = 20;
    private static final long ATTENTE_SERVEUR_MUET_SECONDES = 30;
    private static final long ARRET_VERTX_SECONDES = 10;
    private static final String MOT_DE_PASSE_MAGASIN = "magasin-de-test";
    private static final String NOM_CERTIFIE = "flux.exemple.test";
    private static final String AUTRE_NOM_CERTIFIE = "autre.exemple.test";
    private static final int STATUT_OK = 200;
    private static final int STATUT_REDIRECTION = 302;
    private static final Duration DELAI_COURT = Duration.ofMillis(300);
    private static final long SONDAGE_MS = 50;
    private static final Duration ATTENTE_APRES_NEGOCIATION = Duration.ofSeconds(1);

    private static Vertx vertx;

    @BeforeAll
    static void demarrerVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void arreterVertx() throws InterruptedException, ExecutionException, TimeoutException {
        vertx.close().toCompletionStage().toCompletableFuture().get(ARRET_VERTX_SECONDES, TimeUnit.SECONDS);
    }

    /** Serveur local qui enregistre l'en-tête Host de chaque requête reçue. */
    private static final class ServeurLocal implements AutoCloseable {
        final HttpServer serveur;
        final ExecutorService executeur = Executors.newCachedThreadPool();
        final List<String> hotesRecus = new CopyOnWriteArrayList<>();
        final CountDownLatch liberation = new CountDownLatch(1);

        ServeurLocal(HttpServer serveur, Map<String, HttpHandler> chemins) {
            this.serveur = serveur;
            chemins.forEach((chemin, traitement) -> serveur.createContext(chemin, echange -> {
                hotesRecus.add(echange.getRequestHeaders().getFirst("Host"));
                traitement.handle(echange);
            }));
            serveur.setExecutor(executeur);
            serveur.start();
        }

        int port() {
            return serveur.getAddress().getPort();
        }

        /** Attend la fermeture du serveur (traitement d'un serveur qui ne répond pas). */
        void attendre() {
            try {
                liberation.await(ATTENTE_SERVEUR_MUET_SECONDES, TimeUnit.SECONDS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            liberation.countDown();
            serveur.stop(0);
            executeur.shutdownNow();
        }
    }

    private static ServeurLocal http(String adresse, int port, Map<String, HttpHandler> chemins) throws IOException {
        return new ServeurLocal(HttpServer.create(new InetSocketAddress(InetAddress.getByName(adresse), port), 0), chemins);
    }

    /** Deux serveurs sur le même port, l'un sur la bonne adresse, l'autre sur la mauvaise. */
    private static ServeurLocal[] paire(Map<String, HttpHandler> chemins) throws IOException {
        for (int essai = 0; essai < ESSAIS_PORT; essai++) {
            ServeurLocal bon = http(BONNE_ADRESSE, 0, chemins);
            try {
                return new ServeurLocal[] { bon, http(MAUVAISE_ADRESSE, bon.port(), chemins) };
            } catch (BindException portPris) {
                bon.close();
            }
        }
        throw new IllegalStateException("aucun port libre à la fois sur " + BONNE_ADRESSE + " et " + MAUVAISE_ADRESSE);
    }

    private static HttpHandler contenu(String corps) {
        return echange -> repondre(echange, STATUT_OK, corps.getBytes(StandardCharsets.UTF_8));
    }

    private static void repondre(HttpExchange echange, int statut, byte[] corps) throws IOException {
        echange.getResponseHeaders().set("Content-Type", "application/atom+xml");
        echange.sendResponseHeaders(statut, corps.length);
        try (OutputStream sortie = echange.getResponseBody()) {
            sortie.write(corps);
        }
    }

    private static RecuperateurWeb recuperateur(GardeAdresses.Resolveur resolveur, List<String> exceptions,
            Optional<TrustOptions> confiance) {
        GardeAdresses garde = new GardeAdresses(resolveur, exceptions);
        return new RecuperateurWeb(garde, new TransportEpingle(vertx, TAILLE_MAX, confiance), DELAI, REDIRECTIONS_MAX,
                TAILLE_MAX);
    }

    /** WEB-07-S1 : le nom se résout vers la bonne adresse au contrôle, puis vers la mauvaise ; seule la bonne est jointe. */
    @Test
    void rebindingSansEffetSurLaConnexion() throws IOException {
        ServeurLocal[] serveurs = paire(Map.of("/", contenu(FLUX)));
        try (ServeurLocal bon = serveurs[0]; ServeurLocal mauvais = serveurs[1]) {
            String hote = "rebind.exemple.test";
            ResolveurChangeant resolveur = ResolveurChangeant.successives(
                    Map.of(hote, List.of(BONNE_ADRESSE, MAUVAISE_ADRESSE)));
            RecuperateurWeb.Contenu contenu = recuperateur(resolveur, List.of(hote + ":" + bon.port()), Optional.empty())
                    .recuperer("http://" + hote + ":" + bon.port() + "/flux");
            assertArrayEquals(FLUX.getBytes(StandardCharsets.UTF_8), contenu.octets());
            assertEquals(List.of(hote + ":" + bon.port()), bon.hotesRecus, "Host d'origine porté par la requête");
            assertTrue(mauvais.hotesRecus.isEmpty(), "aucune requête reçue à " + MAUVAISE_ADRESSE);
            assertEquals(1, resolveur.appels(hote));
        }
    }

    /** WEB-07-S2 : après une redirection, la connexion vise l'adresse contrôlée du second hôte. */
    @Test
    void rebindingApresRedirectionSansEffet() throws IOException {
        String premier = "flux.exemple.test";
        String second = "second.exemple.test";
        int[] port = new int[1];
        HttpHandler redirection = echange -> {
            echange.getResponseHeaders().set("Location", "http://" + second + ":" + port[0] + "/flux");
            echange.sendResponseHeaders(STATUT_REDIRECTION, -1);
            echange.close();
        };
        ServeurLocal[] serveurs = paire(Map.of("/", redirection, "/flux", contenu(FLUX)));
        try (ServeurLocal bon = serveurs[0]; ServeurLocal mauvais = serveurs[1]) {
            port[0] = bon.port();
            ResolveurChangeant resolveur = ResolveurChangeant.successives(Map.of(
                    premier, List.of(BONNE_ADRESSE), second, List.of(BONNE_ADRESSE, MAUVAISE_ADRESSE)));
            RecuperateurWeb.Contenu contenu = recuperateur(resolveur,
                    List.of(premier + ":" + bon.port(), second + ":" + bon.port()), Optional.empty())
                    .recuperer("http://" + premier + ":" + bon.port() + "/");
            assertEquals(URI.create("http://" + second + ":" + bon.port() + "/flux"), contenu.adresseFinale());
            assertEquals(List.of(premier + ":" + bon.port(), second + ":" + bon.port()), bon.hotesRecus);
            assertTrue(mauvais.hotesRecus.isEmpty(), "aucune requête reçue à " + MAUVAISE_ADRESSE);
            assertEquals(1, resolveur.appels(second));
        }
    }

    /** I-2 : un nom qui n'existe pas dans le DNS est joint à l'adresse contrôlée, sans résolution par le transport. */
    @Test
    void aucuneResolutionParLeTransport() throws IOException, InterruptedException {
        try (ServeurLocal serveur = http(BONNE_ADRESSE, 0, Map.of("/", contenu(FLUX)))) {
            String introuvable = "nom-sans-dns.invalid";
            DestinationControlee destination = new DestinationControlee(
                    URI.create("http://" + introuvable + ":" + serveur.port() + "/flux?page=1"),
                    List.of(InetAddress.getByName(BONNE_ADRESSE)));
            try (TransportEpingle transport = new TransportEpingle(vertx, TAILLE_MAX, Optional.empty())) {
                RecuperateurWeb.Reponse reponse = transport.envoyer(destination, DELAI);
                assertEquals(STATUT_OK, reponse.statut());
                assertArrayEquals(FLUX.getBytes(StandardCharsets.UTF_8), reponse.corps().readAllBytes());
            }
            assertEquals(List.of(introuvable + ":" + serveur.port()), serveur.hotesRecus);
        }
    }

    /** Décision D3 : la première adresse refuse la connexion, la suivante de la même résolution est essayée. */
    @Test
    void premiereAdresseInjoignableSuivanteEssayee() throws IOException, InterruptedException {
        try (ServeurLocal serveur = http(MAUVAISE_ADRESSE, 0, Map.of("/", contenu(FLUX)))) {
            DestinationControlee destination = new DestinationControlee(
                    URI.create("http://double.exemple.test:" + serveur.port() + "/"),
                    List.of(InetAddress.getByName(BONNE_ADRESSE), InetAddress.getByName(MAUVAISE_ADRESSE)));
            try (TransportEpingle transport = new TransportEpingle(vertx, TAILLE_MAX, Optional.empty())) {
                assertEquals(STATUT_OK, transport.envoyer(destination, Duration.ofSeconds(10)).statut());
            }
            assertEquals(1, serveur.hotesRecus.size());
        }
    }

    @Test
    void serveurSansReponseInterrompuAuDelai() throws IOException {
        ServeurLocal[] attente = new ServeurLocal[1];
        try (ServeurLocal serveur = http(BONNE_ADRESSE, 0, Map.of("/", echange -> attente[0].attendre()))) {
            attente[0] = serveur;
            RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> recuperateurLocal(serveur)
                    .recuperer("http://lent.exemple.test:" + serveur.port() + "/"));
            assertEquals(Motif.DELAI_DEPASSE, refus.motif());
        }
    }

    @Test
    void corpsMuetApresLesEnTetesInterrompuAuDelai() throws IOException {
        ServeurLocal[] attente = new ServeurLocal[1];
        HttpHandler muet = echange -> {
            echange.sendResponseHeaders(STATUT_OK, 0);
            OutputStream sortie = echange.getResponseBody();
            sortie.write("<feed>".getBytes(StandardCharsets.UTF_8));
            sortie.flush();
            attente[0].attendre();
        };
        try (ServeurLocal serveur = http(BONNE_ADRESSE, 0, Map.of("/", muet))) {
            attente[0] = serveur;
            RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> recuperateurLocal(serveur)
                    .recuperer("http://muet.exemple.test:" + serveur.port() + "/"));
            assertEquals(Motif.DELAI_DEPASSE, refus.motif());
        }
    }

    @Test
    void contenuTropVolumineuxRefuse() throws IOException {
        try (ServeurLocal serveur = http(BONNE_ADRESSE, 0, Map.of("/", contenu("x".repeat(5000))))) {
            RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> recuperateurLocal(serveur)
                    .recuperer("http://gros.exemple.test:" + serveur.port() + "/"));
            assertEquals(Motif.CONTENU_TROP_VOLUMINEUX, refus.motif());
        }
    }

    /** Récupérateur dont tout nom se résout vers la bonne adresse, exempté pour le port du serveur. */
    private static RecuperateurWeb recuperateurLocal(ServeurLocal serveur) {
        GardeAdresses.Resolveur resolveur = hote -> new InetAddress[] { InetAddress.getByName(BONNE_ADRESSE) };
        List<String> exceptions = List.of("lent.exemple.test", "muet.exemple.test", "gros.exemple.test").stream()
                .map(hote -> hote + ":" + serveur.port()).toList();
        return recuperateur(resolveur, exceptions, Optional.empty());
    }

    /** WEB-07-S4 : certificat valide pour le nom d'hôte accepté (SNI et Host d'origine), certificat d'un autre nom refusé. */
    @Test
    void certificatVerifiePourLeNomDHote(@TempDir Path dossier) throws IOException, GeneralSecurityException,
            InterruptedException {
        KeyStore magasinFlux = magasin(dossier, NOM_CERTIFIE);
        KeyStore magasinAutre = magasin(dossier, AUTRE_NOM_CERTIFIE);
        Optional<TrustOptions> certificatsDeTest = confiance(magasinFlux, magasinAutre);
        GardeAdresses.Resolveur resolveur = hote -> new InetAddress[] { InetAddress.getByName(BONNE_ADRESSE) };

        List<String> nomsSni = new CopyOnWriteArrayList<>();
        try (ServeurLocal conforme = https(magasinFlux, nomsSni)) {
            String adresse = "https://" + NOM_CERTIFIE + ":" + conforme.port() + "/flux";
            RecuperateurWeb.Contenu contenu = recuperateur(resolveur, List.of(NOM_CERTIFIE + ":" + conforme.port()),
                    certificatsDeTest).recuperer(adresse);
            assertArrayEquals(FLUX.getBytes(StandardCharsets.UTF_8), contenu.octets());
            assertEquals(List.of(NOM_CERTIFIE), nomsSni, "indication SNI du nom d'hôte d'origine");
            assertEquals(List.of(NOM_CERTIFIE + ":" + conforme.port()), conforme.hotesRecus);
        }
        try (ServeurLocal autreNom = https(magasinAutre, new CopyOnWriteArrayList<>())) {
            String adresse = "https://" + NOM_CERTIFIE + ":" + autreNom.port() + "/flux";
            RecuperationRefusee refus = assertThrows(RecuperationRefusee.class, () -> recuperateur(resolveur,
                    List.of(NOM_CERTIFIE + ":" + autreNom.port()), certificatsDeTest).recuperer(adresse));
            assertEquals(Motif.ERREUR_RESEAU, refus.motif());
            assertTrue(autreNom.hotesRecus.isEmpty(), "aucune requête HTTP après l'échec de la vérification");
        }
    }

    /** Confiance accordée aux certificats autosignés des magasins de test (alias égal au nom certifié). */
    private static Optional<TrustOptions> confiance(KeyStore... magasins) throws IOException, GeneralSecurityException {
        KeyStore confiance = KeyStore.getInstance("PKCS12");
        confiance.load(null, null);
        for (KeyStore magasin : magasins) {
            String alias = magasin.aliases().nextElement();
            confiance.setCertificateEntry(alias, magasin.getCertificate(alias));
        }
        TrustManagerFactory fabrique = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrique.init(confiance);
        return Optional.of(TrustOptions.wrap(fabrique));
    }

    /**
     * Requête dont la connexion s'établit après l'échéance (négociation TLS plus longue que le délai) : le transport
     * lève le dépassement de délai, puis la requête ouverte trop tard est coupée avant tout envoi.
     */
    @Test
    void requeteOuverteApresLEcheanceJamaisEnvoyee(@TempDir Path dossier) throws IOException, GeneralSecurityException,
            InterruptedException {
        KeyStore magasinFlux = magasin(dossier, NOM_CERTIFIE);
        List<String> nomsSni = new CopyOnWriteArrayList<>();
        CountDownLatch retenue = new CountDownLatch(1);
        try (ServeurLocal lent = https(magasinFlux, nomsSni, Optional.of(retenue));
                TransportEpingle transport = new TransportEpingle(vertx, TAILLE_MAX, confiance(magasinFlux))) {
            DestinationControlee destination = new DestinationControlee(
                    URI.create("https://" + NOM_CERTIFIE + ":" + lent.port() + "/flux"),
                    List.of(InetAddress.getByName(BONNE_ADRESSE)));
            assertThrows(HttpTimeoutException.class, () -> transport.envoyer(destination, DELAI_COURT));
            retenue.countDown();
            assertTrue(aucuneRequetePendant(lent, ATTENTE_APRES_NEGOCIATION), "aucune requête HTTP envoyée après l'abandon");
            assertEquals(List.of(NOM_CERTIFIE), nomsSni, "la négociation TLS a bien eu lieu");
        }
    }

    /** Vrai si le serveur ne reçoit aucune requête avant {@code duree} ; faux dès qu'une requête arrive. */
    private static boolean aucuneRequetePendant(ServeurLocal serveur, Duration duree) throws InterruptedException {
        long fin = System.nanoTime() + duree.toNanos();
        while (System.nanoTime() < fin) {
            if (!serveur.hotesRecus.isEmpty()) {
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(SONDAGE_MS);
        }
        return serveur.hotesRecus.isEmpty();
    }

    @Test
    void echecsDeConnexionDistinguesDesErreursTls() {
        assertTrue(TransportEpingle.echecDeConnexion(new ConnectException("refusée")));
        assertTrue(TransportEpingle.echecDeConnexion(new NoRouteToHostException("sans route")));
        assertFalse(TransportEpingle.echecDeConnexion(new SSLHandshakeException("certificat d'un autre nom")));
        assertFalse(TransportEpingle.echecDeConnexion(new IOException("corps interrompu")));
    }

    /**
     * Sélecteur SNI du serveur de test : enregistre chaque nom reçu, puis, s'il a une retenue, bloque la négociation TLS
     * jusqu'à sa libération par le test.
     */
    private static final class SniEnregistre extends SNIMatcher {
        private final List<String> nomsSni;
        private final Optional<CountDownLatch> retenue;

        SniEnregistre(List<String> nomsSni, Optional<CountDownLatch> retenue) {
            super(StandardConstants.SNI_HOST_NAME);
            this.nomsSni = nomsSni;
            this.retenue = retenue;
        }

        @Override
        public boolean matches(SNIServerName nom) {
            nomsSni.add(((SNIHostName) nom).getAsciiName());
            retenue.ifPresent(SniEnregistre::attendre);
            return true;
        }

        private static void attendre(CountDownLatch verrou) {
            try {
                verrou.await(ATTENTE_SERVEUR_MUET_SECONDES, TimeUnit.SECONDS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Serveur HTTPS local qui présente le certificat du magasin et enregistre les noms SNI reçus. */
    private static ServeurLocal https(KeyStore magasin, List<String> nomsSni) throws IOException, GeneralSecurityException {
        return https(magasin, nomsSni, Optional.empty());
    }

    /** Serveur HTTPS local dont la négociation TLS attend, s'il y a une retenue, que le test la libère. */
    private static ServeurLocal https(KeyStore magasin, List<String> nomsSni, Optional<CountDownLatch> retenue)
            throws IOException, GeneralSecurityException {
        KeyManagerFactory cles = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        cles.init(magasin, MOT_DE_PASSE_MAGASIN.toCharArray());
        SSLContext contexte = SSLContext.getInstance("TLS");
        contexte.init(cles.getKeyManagers(), null, null);
        HttpsServer serveur = HttpsServer.create(new InetSocketAddress(InetAddress.getByName(BONNE_ADRESSE), 0), 0);
        serveur.setHttpsConfigurator(new HttpsConfigurator(contexte) {
            @Override
            public void configure(HttpsParameters parametres) {
                SSLParameters ssl = getSSLContext().getDefaultSSLParameters();
                ssl.setSNIMatchers(List.of(new SniEnregistre(nomsSni, retenue)));
                parametres.setSSLParameters(ssl);
            }
        });
        return new ServeurLocal(serveur, Map.of("/", contenu(FLUX)));
    }

    /** Magasin PKCS12 d'une paire de clés EC et d'un certificat autosigné pour {@code nom}, produit par keytool. */
    private static KeyStore magasin(Path dossier, String nom) throws IOException, GeneralSecurityException,
            InterruptedException {
        Path fichier = dossier.resolve(nom + ".p12");
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
        Process generation = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", nom, "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=" + nom, "-ext", "SAN=dns:" + nom, "-validity", "2",
                "-storetype", "PKCS12", "-keystore", fichier.toString(), "-storepass", MOT_DE_PASSE_MAGASIN,
                "-keypass", MOT_DE_PASSE_MAGASIN)
                .redirectErrorStream(true).start();
        String sortie = new String(generation.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (generation.waitFor() != 0) {
            throw new IllegalStateException("keytool en échec pour " + nom + " : " + sortie);
        }
        KeyStore magasin = KeyStore.getInstance("PKCS12");
        try (InputStream entree = Files.newInputStream(fichier)) {
            magasin.load(entree, MOT_DE_PASSE_MAGASIN.toCharArray());
        }
        return magasin;
    }
}
