package io.argos.scm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Lecture HTTP commune aux adapters GitLab et GitHub : requêtes {@code GET} uniquement (lecture seule,
 * PIN-05), aucune redirection suivie (le jeton ne part jamais vers un autre hôte), taille de réponse
 * bornée pendant la lecture, détection des quotas épuisés. Le jeton n'apparaît dans aucun message.
 */
final class ForgeHttp {

    /**
     * Vrai pour un dépôt GitHub de la forme {@code owner/repo} dont aucun segment n'est fait que de
     * points : {@code ../..} ne peut pas faire sortir l'appel de {@code /repos/owner/repo}.
     */
    static boolean depotGithubValide(String depot) {
        return depot != null && depot.matches("(?!\\.+/)[\\w.-]+/(?!\\.+$)[\\w.-]+");
    }

    /** Réponse lue : statut, corps (au plus la taille maximale) et en-têtes. */
    record Reponse(int status, byte[] corps, HttpHeaders entetes) {
    }

    /** Délai de report maximal accepté d'une forge : au-delà, la valeur annoncée est ignorée. */
    static final Duration REPORT_MAX = Duration.ofHours(24);

    /** Report par défaut quand la forge signale un quota épuisé sans date de réinitialisation. */
    static final Duration REPORT_PAR_DEFAUT = Duration.ofHours(1);

    private final HttpClient client;
    private final Duration timeout;
    private final Clock horloge;

    ForgeHttp(Duration timeout, Clock horloge) {
        this.timeout = timeout;
        this.horloge = horloge;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /**
     * Requête {@code GET} authentifiée par le jeton de la forge, s'il est configuré.
     *
     * @throws QuotaEpuise si la forge signale un quota d'API épuisé
     * @throws ScmException pour une erreur réseau ou une réponse au-delà de {@code tailleMax}
     */
    Reponse get(URI uri, Optional<String> jeton, boolean github, String accept, long tailleMax) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(uri).timeout(timeout).GET();
        if (accept != null) {
            requete.header("Accept", accept);
        }
        if (github) {
            jeton.ifPresent(valeurJeton -> requete.header("Authorization", "Bearer " + valeurJeton));
        } else {
            jeton.ifPresent(valeurJeton -> requete.header("PRIVATE-TOKEN", valeurJeton));
        }
        try {
            HttpResponse<InputStream> reponse = client.send(requete.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] corps;
            try (InputStream flux = reponse.body()) {
                corps = flux.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, tailleMax + 1));
            }
            if (corps.length > tailleMax) {
                throw new ScmException("réponse de " + uri.getHost() + " au-delà de la taille maximale", null);
            }
            quota(reponse.statusCode(), reponse.headers(), corps, horloge.instant()).ifPresent(reprise -> {
                throw new QuotaEpuise(uri.getHost(), reprise);
            });
            return new Reponse(reponse.statusCode(), corps, reponse.headers());
        } catch (IOException erreur) {
            throw new ScmException("erreur réseau vers " + uri.getHost(), erreur);
        } catch (InterruptedException erreur) {
            Thread.currentThread().interrupt();
            throw new ScmException("appel interrompu vers " + uri.getHost(), erreur);
        }
    }

    /** Taille au-delà de laquelle le corps d'un 403 n'est pas examiné : un message d'erreur est court. */
    private static final int CORPS_MESSAGE_MAX = 65_536;

    /**
     * Date de reprise si la réponse signale un quota épuisé : 429, ou 403 avec
     * {@code x-ratelimit-remaining: 0} (limite principale GitHub), avec {@code Retry-After} (limite
     * secondaire GitHub) ou dont le corps mentionne une limite de débit ({@code rate limit} : GitHub
     * peut signaler une limite secondaire sans {@code Retry-After} et avec un quota principal
     * restant). Ordre de lecture : {@code Retry-After} (secondes), {@code x-ratelimit-reset}
     * (GitHub) ou {@code RateLimit-Reset} (GitLab), en secondes depuis l'époque ; à défaut, une heure.
     */
    static Optional<Instant> quota(int status, HttpHeaders entetes, byte[] corps, Instant maintenant) {
        if (!quotaEpuise(status, entetes, corps)) {
            return Optional.empty();
        }
        return Optional.of(bornerReprise(repriseAnnoncee(entetes, maintenant), maintenant));
    }

    /** Vrai pour un 429, ou pour un 403 qui signale une limite de débit (en-têtes ou corps). */
    private static boolean quotaEpuise(int status, HttpHeaders entetes, byte[] corps) {
        return status == 429 || (status == 403 && limiteDeDebitSignalee(entetes, corps));
    }

    private static boolean limiteDeDebitSignalee(HttpHeaders entetes, byte[] corps) {
        return entetes.firstValue("x-ratelimit-remaining").map(String::strip).filter("0"::equals).isPresent()
                || entetes.firstValue("retry-after").isPresent()
                || mentionneLimiteDeDebit(corps);
    }

    /**
     * Date de reprise annoncée par la forge : {@code Retry-After}, puis {@code x-ratelimit-reset},
     * puis {@code RateLimit-Reset} ; à défaut, {@link #REPORT_PAR_DEFAUT} après {@code maintenant}.
     */
    private static Instant repriseAnnoncee(HttpHeaders entetes, Instant maintenant) {
        return entier(entetes.firstValue("retry-after")).map(secondes -> maintenant.plusSeconds(secondes))
                .or(() -> entier(entetes.firstValue("x-ratelimit-reset")).map(Instant::ofEpochSecond))
                .or(() -> entier(entetes.firstValue("ratelimit-reset")).map(Instant::ofEpochSecond))
                .orElse(maintenant.plus(REPORT_PAR_DEFAUT));
    }

    /**
     * Reprise ramenée à {@code maintenant} si elle est passée, et remplacée par le report par défaut
     * si elle dépasse {@link #REPORT_MAX}.
     */
    private static Instant bornerReprise(Instant reprise, Instant maintenant) {
        if (reprise.isBefore(maintenant)) {
            return maintenant;
        }
        if (reprise.isAfter(maintenant.plus(REPORT_MAX))) {
            return maintenant.plus(REPORT_PAR_DEFAUT);
        }
        return reprise;
    }

    /** Vrai si le corps (message d'erreur) mentionne une limite de débit. */
    static boolean mentionneLimiteDeDebit(byte[] corps) {
        if (corps == null || corps.length == 0 || corps.length > CORPS_MESSAGE_MAX) {
            return false;
        }
        return new String(corps, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("rate limit");
    }

    private static Optional<Long> entier(Optional<String> valeur) {
        try {
            return valeur.map(String::strip).filter(nombre -> nombre.matches("\\d{1,12}")).map(Long::parseLong);
        } catch (NumberFormatException illisible) {
            return Optional.empty();
        }
    }
}
