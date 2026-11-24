package io.argos.ingestion.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrustOptions;

/**
 * Transport de production de la veille web (WEB-07, décision D1 du changement {@code epingler-adresse-veille-web}) :
 * client HTTP de Vert.x, déjà fourni par Quarkus, qui ouvre la connexion TCP vers une adresse IP de la
 * {@link DestinationControlee} et ne fait aucune résolution DNS. Le nom d'hôte d'origine porte l'en-tête
 * {@code Host}, l'indication SNI et la vérification du certificat (identification HTTPS du point de terminaison).
 *
 * <p>Les adresses IP sont essayées dans l'ordre de la résolution contrôlée, la suivante seulement après un échec
 * d'établissement de la connexion TCP (connexion refusée, hôte sans route, délai de connexion), dans le délai global
 * de la récupération (décision D3), mesuré sur l'horloge monotone de la JVM. Le corps est lu en entier dans ce délai,
 * au plus jusqu'à {@code tailleMax + 1} octets, puis la connexion est coupée : la récupération constate le
 * dépassement. À l'échéance, la requête est abandonnée, y compris si elle s'ouvre après, et le transport
 * lève {@link HttpTimeoutException}.
 */
final class TransportEpingle implements RecuperateurWeb.Transport, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(TransportEpingle.class);

    static final String TYPES_ACCEPTES =
            "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.5";

    /** Délai minimal de connexion accordé à une adresse, en millisecondes. */
    private static final long CONNEXION_MIN_MS = 1;

    private final HttpClient client;
    private final long tailleMax;

    /**
     * @param vertx instance Vert.x qui porte le client
     * @param tailleMax taille maximale du contenu, en octets
     * @param confiance certificats de confiance ; à défaut, ceux de la JVM
     */
    TransportEpingle(Vertx vertx, long tailleMax, Optional<TrustOptions> confiance) {
        HttpClientOptions options = new HttpClientOptions()
                .setKeepAlive(false)
                .setVerifyHost(true)
                .setForceSni(true)
                .setTryUseCompression(false);
        confiance.ifPresent(options::setTrustOptions);
        this.client = vertx.createHttpClient(options);
        this.tailleMax = tailleMax;
    }

    @Override
    public RecuperateurWeb.Reponse envoyer(DestinationControlee destination, Duration delai)
            throws IOException, InterruptedException {
        long echeance = System.nanoTime() + delai.toNanos();
        List<InetAddress> adresses = destination.adresses();
        List<SocketException> echecs = new ArrayList<>();
        for (int rang = 0; rang < adresses.size(); rang++) {
            long resteMs = resteMillis(echeance);
            if (resteMs <= 0) {
                break;
            }
            long connexionMs = Math.max(CONNEXION_MIN_MS, resteMs / (adresses.size() - rang));
            try {
                return envoyerA(destination, adresses.get(rang), connexionMs, echeance);
            } catch (EchecConnexion echec) {
                LOG.debugf(echec.getCause(), "veille web : connexion impossible à %s pour %s",
                        adresses.get(rang).getHostAddress(), destination.hote());
                echecs.add(echec.echec());
            }
        }
        if (echecs.isEmpty() || System.nanoTime() - echeance >= 0) {
            HttpTimeoutException expiration = new HttpTimeoutException("délai dépassé pour " + destination.hote());
            echecs.forEach(expiration::addSuppressed);
            throw expiration;
        }
        SocketException derniere = echecs.get(echecs.size() - 1);
        echecs.subList(0, echecs.size() - 1).forEach(derniere::addSuppressed);
        throw derniere;
    }

    /** Millisecondes restant avant l'échéance, lue sur l'horloge monotone de la JVM ({@code System.nanoTime()}). */
    private static long resteMillis(long echeance) {
        return TimeUnit.NANOSECONDS.toMillis(echeance - System.nanoTime());
    }

    /**
     * Vrai pour un échec d'établissement de la connexion TCP (connexion refusée, hôte sans route, délai de connexion,
     * tous des {@link SocketException}) : l'adresse suivante peut alors être essayée. Un échec de la négociation TLS,
     * dont un certificat non conforme au nom d'hôte, n'en est pas un.
     */
    static boolean echecDeConnexion(Throwable cause) {
        return cause instanceof SocketException;
    }

    /** Échec de connexion à une adresse, distingué des autres erreurs pour l'essai de l'adresse suivante. */
    private static final class EchecConnexion extends IOException {
        private static final long serialVersionUID = 1L;

        EchecConnexion(SocketException cause) {
            super(cause.getMessage(), cause);
        }

        SocketException echec() {
            return (SocketException) getCause();
        }
    }

    private RecuperateurWeb.Reponse envoyerA(DestinationControlee destination, InetAddress adresse, long connexionMs,
            long echeance) throws IOException, InterruptedException {
        LOG.debugf("veille web : connexion à %s pour %s", adresse.getHostAddress(), destination.hote());
        AtomicReference<HttpClientRequest> requete = new AtomicReference<>();
        CompletableFuture<RecuperateurWeb.Reponse> resultat = new CompletableFuture<>();
        client.request(options(destination, adresse, connexionMs))
                .onComplete(ouverture -> envoyerSiAttendue(ouverture, requete, resultat));
        return attendre(destination, echeance, requete, resultat);
    }

    /** Requête GET vers l'adresse IP, avec le nom d'hôte d'origine pour Host, SNI et la vérification du certificat. */
    private static RequestOptions options(DestinationControlee destination, InetAddress adresse, long connexionMs) {
        return new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setServer(SocketAddress.inetSocketAddress(new InetSocketAddress(adresse, destination.port())))
                .setHost(destination.hote())
                .setPort(destination.port())
                .setSsl(destination.chiffree())
                .setURI(destination.cheminEtRequete())
                .setConnectTimeout(connexionMs)
                .putHeader(HttpHeaders.USER_AGENT, RecuperateurWeb.AGENT)
                .putHeader(HttpHeaders.ACCEPT, TYPES_ACCEPTES);
    }

    /** Rappel d'ouverture : envoie la requête ouverte, sauf si l'appelant a déjà abandonné la réponse. */
    private void envoyerSiAttendue(AsyncResult<HttpClientRequest> ouverture, AtomicReference<HttpClientRequest> requete,
            CompletableFuture<RecuperateurWeb.Reponse> resultat) {
        if (ouverture.failed()) {
            Throwable cause = ouverture.cause();
            resultat.completeExceptionally(echecDeConnexion(cause) ? new EchecConnexion((SocketException) cause) : cause);
            return;
        }
        HttpClientRequest ouverte = ouverture.result();
        // Requête publiée avant le contrôle : si l'appelant abandonne entre les deux, l'un ou l'autre la coupe.
        requete.set(ouverte);
        if (resultat.isDone()) {
            // Ouverte après l'échéance ou l'interruption de l'appelant : abandonnée avant tout envoi.
            ouverte.reset();
            return;
        }
        ouverte.send().onComplete(envoi -> {
            if (envoi.failed()) {
                resultat.completeExceptionally(envoi.cause());
            } else {
                lireCorps(envoi.result(), ouverte, resultat);
            }
        });
    }

    /** Attend la réponse jusqu'à l'échéance ; l'abandonne sur dépassement ou interruption. */
    private static RecuperateurWeb.Reponse attendre(DestinationControlee destination, long echeance,
            AtomicReference<HttpClientRequest> requete, CompletableFuture<RecuperateurWeb.Reponse> resultat)
            throws IOException, InterruptedException {
        try {
            long resteMs = Math.max(CONNEXION_MIN_MS, resteMillis(echeance));
            return resultat.get(resteMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expiration) {
            abandonner(resultat, requete);
            HttpTimeoutException delaiDepasse = new HttpTimeoutException("délai dépassé pour " + destination.hote());
            delaiDepasse.initCause(expiration);
            throw delaiDepasse;
        } catch (InterruptedException interruption) {
            abandonner(resultat, requete);
            throw interruption;
        } catch (ExecutionException echec) {
            Throwable cause = echec.getCause();
            if (cause instanceof IOException entreeSortie) {
                throw entreeSortie;
            }
            throw new IOException("échec de la requête vers " + destination.hote() + " : " + cause.getMessage(), cause);
        }
    }

    /** Accumule le corps jusqu'à {@code tailleMax + 1} octets, puis rend la réponse. */
    private void lireCorps(HttpClientResponse reponse, HttpClientRequest requete,
            CompletableFuture<RecuperateurWeb.Reponse> resultat) {
        long plafond = Math.min(Integer.MAX_VALUE, tailleMax + 1);
        Buffer corps = Buffer.buffer();
        Runnable terminer = () -> resultat.complete(new RecuperateurWeb.Reponse(reponse.statusCode(),
                Optional.ofNullable(reponse.getHeader(HttpHeaders.LOCATION)),
                Optional.ofNullable(reponse.getHeader(HttpHeaders.CONTENT_TYPE)),
                new ByteArrayInputStream(corps.getBytes())));
        reponse.exceptionHandler(resultat::completeExceptionally);
        reponse.handler(morceau -> {
            if (resultat.isDone()) {
                return;
            }
            int place = (int) (plafond - corps.length());
            if (morceau.length() < place) {
                corps.appendBuffer(morceau);
                return;
            }
            corps.appendBuffer(morceau, 0, place);
            terminer.run();
            requete.reset();
        });
        reponse.endHandler(fin -> terminer.run());
    }

    /**
     * Marque la réponse attendue comme abandonnée, puis coupe la requête si elle est déjà ouverte ; ouverte plus tard,
     * elle est coupée par le rappel d'ouverture, qui voit l'abandon.
     */
    private static void abandonner(CompletableFuture<RecuperateurWeb.Reponse> resultat,
            AtomicReference<HttpClientRequest> requete) {
        resultat.cancel(false);
        HttpClientRequest enCours = requete.get();
        if (enCours != null) {
            enCours.reset();
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
