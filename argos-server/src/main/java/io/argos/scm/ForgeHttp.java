package io.argos.scm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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

    ForgeHttp(Duration timeout) {
        this.timeout = timeout;
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
            jeton.ifPresent(t -> requete.header("Authorization", "Bearer " + t));
        } else {
            jeton.ifPresent(t -> requete.header("PRIVATE-TOKEN", t));
        }
        try {
            HttpResponse<InputStream> r = client.send(requete.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] corps;
            try (InputStream in = r.body()) {
                corps = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, tailleMax + 1));
            }
            if (corps.length > tailleMax) {
                throw new ScmException("réponse de " + uri.getHost() + " au-delà de la taille maximale", null);
            }
            quota(r.statusCode(), r.headers(), uri).ifPresent(reprise -> {
                throw new QuotaEpuise(uri.getHost(), reprise);
            });
            return new Reponse(r.statusCode(), corps, r.headers());
        } catch (IOException e) {
            throw new ScmException("erreur réseau vers " + uri.getHost(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScmException("appel interrompu vers " + uri.getHost(), e);
        }
    }

    /**
     * Date de reprise si la réponse signale un quota épuisé : 429, ou 403 avec
     * {@code x-ratelimit-remaining: 0} (GitHub). Ordre de lecture : {@code Retry-After} (secondes),
     * {@code x-ratelimit-reset} (GitHub) ou {@code RateLimit-Reset} (GitLab), en secondes depuis l'époque.
     */
    static Optional<Instant> quota(int status, HttpHeaders entetes, URI uri) {
        boolean epuise = status == 429
                || (status == 403 && entetes.firstValue("x-ratelimit-remaining").map(String::strip).filter("0"::equals).isPresent());
        if (!epuise) {
            return Optional.empty();
        }
        Instant maintenant = Instant.now();
        Instant reprise = entier(entetes.firstValue("retry-after")).map(s -> maintenant.plusSeconds(s))
                .or(() -> entier(entetes.firstValue("x-ratelimit-reset")).map(Instant::ofEpochSecond))
                .or(() -> entier(entetes.firstValue("ratelimit-reset")).map(Instant::ofEpochSecond))
                .orElse(maintenant.plus(REPORT_PAR_DEFAUT));
        if (reprise.isBefore(maintenant)) {
            reprise = maintenant;
        }
        if (reprise.isAfter(maintenant.plus(REPORT_MAX))) {
            reprise = maintenant.plus(REPORT_PAR_DEFAUT);
        }
        return Optional.of(reprise);
    }

    private static Optional<Long> entier(Optional<String> valeur) {
        try {
            return valeur.map(String::strip).filter(v -> v.matches("\\d{1,12}")).map(Long::parseLong);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
