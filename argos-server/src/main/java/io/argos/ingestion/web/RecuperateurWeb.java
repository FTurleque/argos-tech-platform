package io.argos.ingestion.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/**
 * Récupération bornée d'une adresse de veille (WEB-03, R-12) : chaque adresse, y compris chaque
 * destination de redirection, passe par {@link GardeAdresses} <em>avant</em> toute requête ; les
 * redirections sont suivies une à une (au plus {@code argos.veille-web.redirections-max}) ; la
 * durée totale et la taille du contenu sont bornées. Aucune adresse trouvée dans un contenu
 * collecté n'est jamais récupérée.
 */
@ApplicationScoped
public class RecuperateurWeb {

    /** Contenu récupéré : adresse finale après redirections, type déclaré et octets reçus. */
    public record Contenu(URI adresseFinale, String typeContenu, byte[] octets) {
    }

    /** Réponse brute d'une requête unique, sans suivi de redirection. */
    record Reponse(int statut, Optional<String> location, Optional<String> typeContenu, InputStream corps) {
    }

    /** Envoi d'une requête GET unique ; remplaçable dans les tests unitaires. */
    @FunctionalInterface
    interface Transport {
        Reponse envoyer(URI uri, Duration delai) throws IOException, InterruptedException;
    }

    /** Minuterie qui ferme un corps de réponse dont la lecture dépasse l'échéance. */
    private static final ScheduledExecutorService MINUTERIE = Executors.newSingleThreadScheduledExecutor(tache -> {
        Thread fil = new Thread(tache, "argos-veille-web-delai");
        fil.setDaemon(true);
        return fil;
    });

    static final String AGENT = "Argos-veille/1.0 (+collecteur RSS/Atom)";

    private final GardeAdresses garde;
    private final Transport transport;
    private final Duration delai;
    private final int redirectionsMax;
    private final long tailleMax;

    @Inject
    public RecuperateurWeb(GardeAdresses garde,
            @ConfigProperty(name = "argos.veille-web.delai", defaultValue = "PT10S") Duration delai,
            @ConfigProperty(name = "argos.veille-web.redirections-max", defaultValue = "5") int redirectionsMax,
            @ConfigProperty(name = "argos.veille-web.taille-max-octets", defaultValue = "2097152") long tailleMax) {
        this(garde, transportHttp(delai), delai, redirectionsMax, tailleMax);
    }

    RecuperateurWeb(GardeAdresses garde, Transport transport, Duration delai, int redirectionsMax, long tailleMax) {
        this.garde = garde;
        this.transport = transport;
        this.delai = delai;
        this.redirectionsMax = redirectionsMax;
        this.tailleMax = tailleMax;
    }

    /** Vérifie puis récupère une adresse saisie par un utilisateur. */
    public Contenu recuperer(String adresse) {
        return recuperer(garde.verifier(adresse));
    }

    /** Récupère une adresse déjà vérifiée ; elle est revérifiée (la résolution DNS peut avoir changé). */
    public Contenu recuperer(URI adresse) {
        Instant echeance = Instant.now().plus(delai);
        URI courante = garde.verifier(GardeAdresses.analyser(adresse.toString()), Motif.ADRESSE_INTERDITE);
        for (int sauts = 0; ; sauts++) {
            Reponse reponse = envoyer(courante, restant(echeance, courante));
            int statut = reponse.statut();
            if (statut >= 300 && statut < 400 && statut != 304) {
                fermer(reponse.corps());
                if (sauts >= redirectionsMax) {
                    throw new RecuperationRefusee(Motif.TROP_DE_REDIRECTIONS,
                            "Plus de " + redirectionsMax + " redirections pour " + courante.getHost() + ".");
                }
                URI suivante = GardeAdresses.redirection(courante, reponse.location().orElse(null));
                courante = garde.verifier(suivante, Motif.REDIRECTION_INTERDITE);
                continue;
            }
            if (statut < 200 || statut >= 300) {
                fermer(reponse.corps());
                throw new RecuperationRefusee(Motif.REPONSE_HTTP, "Réponse HTTP " + statut + " de " + courante.getHost() + ".");
            }
            byte[] octets = lire(reponse.corps(), echeance, courante);
            return new Contenu(courante, reponse.typeContenu().orElse(""), octets);
        }
    }

    private Reponse envoyer(URI uri, Duration restant) {
        try {
            return transport.envoyer(uri, restant);
        } catch (HttpTimeoutException erreur) {
            throw new RecuperationRefusee(Motif.DELAI_DEPASSE, "Délai dépassé pour " + uri.getHost() + ".", erreur);
        } catch (IOException erreur) {
            throw new RecuperationRefusee(Motif.ERREUR_RESEAU, "Erreur réseau pour " + uri.getHost() + ".", erreur);
        } catch (InterruptedException erreur) {
            Thread.currentThread().interrupt();
            throw new RecuperationRefusee(Motif.ERREUR_RESEAU, "Récupération interrompue pour " + uri.getHost() + ".", erreur);
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
        // Un serveur qui envoie ses en-têtes puis se tait bloquerait la lecture : le flux est fermé à
        // l'échéance, ce qui interrompt la lecture en cours.
        ScheduledFuture<?> coupure = MINUTERIE.schedule(() -> fermer(corps),
                Math.max(1, Duration.between(Instant.now(), echeance).toMillis()), TimeUnit.MILLISECONDS);
        try (InputStream flux = corps; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] tampon = new byte[8192];
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
            if (!Instant.now().isBefore(echeance)) {
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
            } catch (IOException ignored) {
                // corps abandonné
            }
        }
    }

    private static Transport transportHttp(Duration delai) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(delai)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return (uri, restant) -> {
            HttpRequest requete = HttpRequest.newBuilder(uri).timeout(restant).GET()
                    .header("User-Agent", AGENT)
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.5")
                    .build();
            HttpResponse<InputStream> reponseHttp = client.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            return new Reponse(reponseHttp.statusCode(), reponseHttp.headers().firstValue("Location"),
                    reponseHttp.headers().firstValue("Content-Type"), reponseHttp.body());
        };
    }
}
