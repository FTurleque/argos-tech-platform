package io.argos.ingestion.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.ingestion.web.RecuperationRefusee.Motif;
import io.vertx.core.Vertx;

/**
 * Récupération bornée d'une adresse de veille (WEB-03, WEB-07, R-12) : chaque adresse, y compris chaque
 * destination de redirection, passe par {@link GardeAdresses} <em>avant</em> toute requête, et la requête
 * part vers une adresse IP issue de ce contrôle, sans nouvelle résolution du nom ({@link TransportEpingle}) ;
 * les redirections sont suivies une à une (au plus {@code argos.veille-web.redirections-max}) ; la durée
 * totale et la taille du contenu sont bornées. Aucune adresse trouvée dans un contenu collecté n'est
 * jamais récupérée.
 */
@ApplicationScoped
public class RecuperateurWeb {

    private static final Logger LOG = Logger.getLogger(RecuperateurWeb.class);

    /** Contenu récupéré : adresse finale après redirections, type déclaré et octets reçus. */
    public record Contenu(URI adresseFinale, String typeContenu, byte[] octets) {
    }

    /** Réponse brute d'une requête unique, sans suivi de redirection. */
    record Reponse(int statut, Optional<String> location, Optional<String> typeContenu, InputStream corps) {
    }

    /**
     * Envoi d'une requête GET unique vers une adresse IP de la destination contrôlée, sans résolution DNS ;
     * remplaçable dans les tests unitaires.
     */
    @FunctionalInterface
    interface Transport {
        Reponse envoyer(DestinationControlee destination, Duration delai) throws IOException, InterruptedException;
    }

    /** Minuterie qui ferme un corps de réponse dont la lecture dépasse l'échéance. */
    private static final ScheduledExecutorService MINUTERIE = Executors.newSingleThreadScheduledExecutor(tache -> {
        Thread fil = new Thread(tache, "argos-veille-web-delai");
        fil.setDaemon(true);
        return fil;
    });

    static final String AGENT = "Argos-veille/1.0 (+collecteur RSS/Atom)";

    private static final int TAMPON_OCTETS = 8192;
    /** Bornes des statuts HTTP de succès (2xx) et de redirection (3xx), bornes hautes exclues. */
    private static final int STATUT_SUCCES_MIN = 200;
    private static final int STATUT_REDIRECTION_MIN = 300;
    private static final int STATUT_ERREUR_CLIENT_MIN = 400;
    /** « Non modifié » : réponse 3xx qui n'est pas une redirection. */
    private static final int STATUT_NON_MODIFIE = 304;

    private final GardeAdresses garde;
    private final Transport transport;
    private final Duration delai;
    private final int redirectionsMax;
    private final long tailleMax;

    @Inject
    public RecuperateurWeb(GardeAdresses garde, Vertx vertx,
            @ConfigProperty(name = "argos.veille-web.delai", defaultValue = "PT10S") Duration delai,
            @ConfigProperty(name = "argos.veille-web.redirections-max", defaultValue = "5") int redirectionsMax,
            @ConfigProperty(name = "argos.veille-web.taille-max-octets", defaultValue = "2097152") long tailleMax) {
        this(garde, new TransportEpingle(vertx, tailleMax, Optional.empty()), delai, redirectionsMax, tailleMax);
    }

    RecuperateurWeb(GardeAdresses garde, Transport transport, Duration delai, int redirectionsMax, long tailleMax) {
        this.garde = garde;
        this.transport = transport;
        this.delai = delai;
        this.redirectionsMax = redirectionsMax;
        this.tailleMax = tailleMax;
    }

    /** Libère le client HTTP du transport de production à l'arrêt du serveur. */
    @PreDestroy
    void fermerTransport() {
        if (transport instanceof TransportEpingle epingle) {
            epingle.close();
        }
    }

    /** Vérifie puis récupère une adresse saisie par un utilisateur. */
    public Contenu recuperer(String adresse) {
        return recuperer(GardeAdresses.analyser(adresse));
    }

    /**
     * Récupère une adresse : elle est contrôlée (une seule résolution DNS), puis la connexion vise une adresse IP
     * de ce contrôle ; chaque redirection est contrôlée et suivie de la même façon.
     */
    public Contenu recuperer(URI adresse) {
        Instant echeance = Instant.now().plus(delai);
        DestinationControlee courante = garde.verifier(GardeAdresses.analyser(adresse.toString()), Motif.ADRESSE_INTERDITE);
        for (int sauts = 0; ; sauts++) {
            Reponse reponse = envoyer(courante, restant(echeance, courante.uri()));
            int statut = reponse.statut();
            if (statut >= STATUT_REDIRECTION_MIN && statut < STATUT_ERREUR_CLIENT_MIN && statut != STATUT_NON_MODIFIE) {
                fermer(reponse.corps());
                if (sauts >= redirectionsMax) {
                    throw new RecuperationRefusee(Motif.TROP_DE_REDIRECTIONS,
                            "Plus de " + redirectionsMax + " redirections pour " + courante.hote() + ".");
                }
                URI suivante = GardeAdresses.redirection(courante.uri(), reponse.location().orElse(null));
                courante = garde.verifier(suivante, Motif.REDIRECTION_INTERDITE);
                continue;
            }
            if (statut < STATUT_SUCCES_MIN || statut >= STATUT_REDIRECTION_MIN) {
                fermer(reponse.corps());
                throw new RecuperationRefusee(Motif.REPONSE_HTTP, "Réponse HTTP " + statut + " de " + courante.hote() + ".");
            }
            byte[] octets = lire(reponse.corps(), echeance, courante.uri());
            return new Contenu(courante.uri(), reponse.typeContenu().orElse(""), octets);
        }
    }

    private Reponse envoyer(DestinationControlee destination, Duration restant) {
        String hote = destination.hote();
        try {
            return transport.envoyer(destination, restant);
        } catch (HttpTimeoutException erreur) {
            throw new RecuperationRefusee(Motif.DELAI_DEPASSE, "Délai dépassé pour " + hote + ".", erreur);
        } catch (IOException erreur) {
            throw new RecuperationRefusee(Motif.ERREUR_RESEAU, "Erreur réseau pour " + hote + ".", erreur);
        } catch (InterruptedException erreur) {
            Thread.currentThread().interrupt();
            throw new RecuperationRefusee(Motif.ERREUR_RESEAU, "Récupération interrompue pour " + hote + ".", erreur);
        }
    }

    private Duration restant(Instant echeance, URI uri) {
        Duration reste = Duration.between(Instant.now(), echeance);
        if (reste.isNegative() || reste.isZero()) {
            throw new RecuperationRefusee(Motif.DELAI_DEPASSE, "Délai dépassé pour " + uri.getHost() + ".");
        }
        return reste;
    }

    private byte[] lire(InputStream corps, Instant echeance, URI uri) {
        if (corps == null) {
            return new byte[0];
        }
        // Un corps qui cesse d'arriver bloquerait la lecture : le flux est fermé à l'échéance, ce qui interrompt
        // la lecture en cours. La minuterie, au millième de seconde, peut fermer le flux un peu avant l'instant
        // d'échéance : la coupure est marquée avant la fermeture, et une lecture interrompue par elle est un délai
        // dépassé.
        AtomicBoolean coupeeALEcheance = new AtomicBoolean();
        ScheduledFuture<?> coupure = MINUTERIE.schedule(() -> {
            coupeeALEcheance.set(true);
            fermer(corps);
        }, Math.max(1, Duration.between(Instant.now(), echeance).toMillis()), TimeUnit.MILLISECONDS);
        try (InputStream flux = corps; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] tampon = new byte[TAMPON_OCTETS];
            long total = 0;
            int octetsLus;
            while ((octetsLus = flux.read(tampon)) != -1) {
                total += octetsLus;
                if (total > tailleMax) {
                    throw new RecuperationRefusee(Motif.CONTENU_TROP_VOLUMINEUX,
                            "Contenu de " + uri.getHost() + " supérieur à " + tailleMax + " octets.");
                }
                if (Instant.now().isAfter(echeance)) {
                    throw new RecuperationRefusee(Motif.DELAI_DEPASSE, "Délai dépassé pour " + uri.getHost() + ".");
                }
                out.write(tampon, 0, octetsLus);
            }
            return out.toByteArray();
        } catch (HttpTimeoutException erreur) {
            throw new RecuperationRefusee(Motif.DELAI_DEPASSE, "Délai dépassé pour " + uri.getHost() + ".", erreur);
        } catch (IOException erreur) {
            if (coupeeALEcheance.get() || !Instant.now().isBefore(echeance)) {
                throw new RecuperationRefusee(Motif.DELAI_DEPASSE, "Délai dépassé pour " + uri.getHost() + ".", erreur);
            }
            throw new RecuperationRefusee(Motif.ERREUR_RESEAU, "Lecture interrompue pour " + uri.getHost() + ".", erreur);
        } finally {
            coupure.cancel(false);
        }
    }

    private static void fermer(InputStream corps) {
        if (corps != null) {
            try {
                corps.close();
            } catch (IOException erreur) {
                LOG.debugf(erreur, "veille web : fermeture d'un corps de réponse abandonné en échec");
            }
        }
    }
}
