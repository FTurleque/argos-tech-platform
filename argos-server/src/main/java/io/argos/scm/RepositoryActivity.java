package io.argos.scm;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lit l'activité d'un dépôt GitLab ou GitHub en lecture seule (PIN-01, PIN-05) : tickets, demandes
 * de fusion, jalons, pipelines et versions. Mêmes forges, mêmes jetons facultatifs et même client
 * HTTP que {@link RepositoryFiles} ({@code argos.scm.*}) ; seules des requêtes {@code GET} sont émises
 * et aucune redirection n'est suivie.
 *
 * <p>Les réponses sont lues dans des structures qui ne déclarent que des dates, des états, des
 * compteurs et les titres des jalons : les champs d'identité (auteur, assigné, relecteur) ne sont
 * jamais lus ni conservés (PIN-03, décision D1). Un quota épuisé lève {@link QuotaEpuise}.
 *
 * <ul>
 *   <li>GitLab ({@code /api/v4/projects/:id}) : {@code /issues}, {@code /merge_requests},
 *       {@code /pipelines} ({@code updated_after}), {@code /releases}, {@code /milestones} et
 *       {@code /issues_statistics?milestone=:titre}.</li>
 *   <li>GitHub ({@code /repos/:owner/:repo}) : {@code /issues?state=all&since=} (tickets et pull
 *       requests), {@code /actions/runs?created=>=}, {@code /releases}, {@code /milestones?state=all}.</li>
 * </ul>
 */
@ApplicationScoped
public class RepositoryActivity {

    static final int PAR_PAGE = 100;
    private static final int TITRE_MAX = 200;
    private static final int REFERENCE_MAX = 64;

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ForgeHttp http;
    private final String gitlabUrl;
    private final String githubUrl;
    private final Optional<String> gitlabToken;
    private final Optional<String> githubToken;
    private final int pagesMax;
    private final int jalonsMax;
    private final long tailleMax;

    @Inject
    public RepositoryActivity(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout,
            @ConfigProperty(name = "argos.scm.gitlab.url") String gitlabUrl,
            @ConfigProperty(name = "argos.scm.github.url") String githubUrl,
            @ConfigProperty(name = "argos.scm.gitlab.token") Optional<String> gitlabToken,
            @ConfigProperty(name = "argos.scm.github.token") Optional<String> githubToken,
            @ConfigProperty(name = "argos.scm.activite.pages-max", defaultValue = "10") int pagesMax,
            @ConfigProperty(name = "argos.scm.activite.jalons-max", defaultValue = "50") int jalonsMax,
            @ConfigProperty(name = "argos.scm.activite.taille-max-octets", defaultValue = "5242880") long tailleMax) {
        this.http = new ForgeHttp(timeout);
        this.gitlabUrl = trim(gitlabUrl);
        this.githubUrl = trim(githubUrl);
        this.gitlabToken = gitlabToken.filter(t -> !t.isBlank());
        this.githubToken = githubToken.filter(t -> !t.isBlank());
        this.pagesMax = Math.max(1, pagesMax);
        this.jalonsMax = Math.max(0, jalonsMax);
        this.tailleMax = tailleMax;
    }

    /** Activité du dépôt depuis {@code depuis}. */
    public ActiviteDepot lire(RepositoryRef ref, Instant depuis) {
        String provider = ref.provider() == null ? "" : ref.provider().toUpperCase(Locale.ROOT);
        return switch (provider) {
            case "GITHUB" -> github(ref, depuis);
            case "GITLAB" -> gitlab(ref, depuis);
            default -> throw new ScmException("forge non prise en charge : " + ref.provider(), null);
        };
    }

    // --- GitHub -------------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhPullRequest(@JsonProperty("merged_at") String mergedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhIssue(@JsonProperty("created_at") String createdAt, @JsonProperty("closed_at") String closedAt,
            @JsonProperty("pull_request") GhPullRequest pullRequest) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhRun(@JsonProperty("created_at") String createdAt, @JsonProperty("conclusion") String conclusion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhRuns(@JsonProperty("workflow_runs") List<GhRun> runs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhRelease(@JsonProperty("published_at") String publishedAt, @JsonProperty("draft") boolean draft) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhMilestone(@JsonProperty("number") Long number, @JsonProperty("title") String title,
            @JsonProperty("state") String state, @JsonProperty("due_on") String dueOn,
            @JsonProperty("closed_at") String closedAt, @JsonProperty("open_issues") int openIssues,
            @JsonProperty("closed_issues") int closedIssues) {
    }

    private ActiviteDepot github(RepositoryRef ref, Instant depuis) {
        if (ref.repository() == null || !ref.repository().matches("[\\w.-]+/[\\w.-]+")) {
            throw new ScmException("dépôt GitHub invalide : attendu owner/repo", null);
        }
        String base = githubUrl + "/repos/" + ref.repository();
        boolean complet = true;

        List<GhIssue> issues = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/issues?state=all&since=" + enc(depuis.toString())
                + "&per_page=" + PAR_PAGE + "&page=" + p), githubToken, true, new TypeReference<List<GhIssue>>() { },
                issues, page -> true);
        List<ActiviteDepot.Ticket> tickets = new ArrayList<>();
        List<ActiviteDepot.Fusion> fusions = new ArrayList<>();
        for (GhIssue i : issues) {
            if (i.pullRequest() != null) {
                fusions.add(new ActiviteDepot.Fusion(instant(i.createdAt()), instant(i.pullRequest().mergedAt())));
            } else {
                tickets.add(new ActiviteDepot.Ticket(instant(i.createdAt()), instant(i.closedAt())));
            }
        }

        List<GhRun> runs = new ArrayList<>();
        String jour = LocalDate.ofInstant(depuis, ZoneOffset.UTC).toString();
        for (int p = 1; ; p++) {
            if (p > pagesMax) {
                complet = false;
                break;
            }
            GhRuns page = lireJson(URI.create(base + "/actions/runs?created=" + enc(">=" + jour) + "&per_page=" + PAR_PAGE
                    + "&page=" + p), githubToken, true, new TypeReference<GhRuns>() { });
            List<GhRun> contenu = page == null || page.runs() == null ? List.of() : page.runs();
            runs.addAll(contenu);
            if (contenu.size() < PAR_PAGE) {
                break;
            }
        }
        List<ActiviteDepot.Pipeline> pipelines = runs.stream()
                .map(run -> new ActiviteDepot.Pipeline(instant(run.createdAt()), issueGithub(run.conclusion())))
                .filter(pl -> pl.creeLe() != null && !pl.creeLe().isBefore(depuis)).toList();

        List<GhRelease> releases = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/releases?per_page=" + PAR_PAGE + "&page=" + p), githubToken, true,
                new TypeReference<List<GhRelease>>() { }, releases,
                page -> page.stream().map(rel -> instant(rel.publishedAt())).anyMatch(d -> d == null || !d.isBefore(depuis)));
        List<ActiviteDepot.Version> versions = releases.stream().filter(rel -> !rel.draft())
                .map(rel -> instant(rel.publishedAt())).filter(d -> d != null && !d.isBefore(depuis))
                .map(ActiviteDepot.Version::new).toList();

        List<GhMilestone> milestones = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/milestones?state=all&per_page=" + PAR_PAGE + "&page=" + p), githubToken,
                true, new TypeReference<List<GhMilestone>>() { }, milestones, page -> true);
        List<ActiviteDepot.Jalon> jalons = new ArrayList<>();
        for (GhMilestone m : milestones) {
            boolean ouvert = "open".equalsIgnoreCase(m.state());
            Instant ferme = instant(m.closedAt());
            if (m.number() == null || (!ouvert && (ferme == null || ferme.isBefore(depuis)))) {
                continue;
            }
            if (jalons.size() >= jalonsMax) {
                complet = false;
                break;
            }
            Instant echeance = instant(m.dueOn());
            jalons.add(new ActiviteDepot.Jalon(reference(String.valueOf(m.number())), titre(m.title()), ouvert,
                    echeance == null ? null : LocalDate.ofInstant(echeance, ZoneOffset.UTC),
                    Math.max(0, m.closedIssues()), Math.max(0, m.closedIssues()) + Math.max(0, m.openIssues())));
        }
        return new ActiviteDepot(tickets, fusions, pipelines, versions, jalons, complet);
    }

    private static ActiviteDepot.Issue issueGithub(String conclusion) {
        if (conclusion == null) {
            return ActiviteDepot.Issue.AUTRE;
        }
        return switch (conclusion.toLowerCase(Locale.ROOT)) {
            case "success" -> ActiviteDepot.Issue.REUSSI;
            case "failure", "timed_out", "startup_failure" -> ActiviteDepot.Issue.ECHOUE;
            default -> ActiviteDepot.Issue.AUTRE;
        };
    }

    // --- GitLab -------------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlIssue(@JsonProperty("created_at") String createdAt, @JsonProperty("closed_at") String closedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlMergeRequest(@JsonProperty("created_at") String createdAt, @JsonProperty("merged_at") String mergedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlPipeline(@JsonProperty("created_at") String createdAt, @JsonProperty("status") String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlRelease(@JsonProperty("released_at") String releasedAt, @JsonProperty("created_at") String createdAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlMilestone(@JsonProperty("id") Long id, @JsonProperty("title") String title,
            @JsonProperty("state") String state, @JsonProperty("due_date") String dueDate,
            @JsonProperty("updated_at") String updatedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlCounts(@JsonProperty("all") int all, @JsonProperty("closed") int closed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlStatistics(@JsonProperty("counts") GlCounts counts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlIssuesStatistics(@JsonProperty("statistics") GlStatistics statistics) {
    }

    private ActiviteDepot gitlab(RepositoryRef ref, Instant depuis) {
        if (ref.repository() == null || ref.repository().isBlank()) {
            throw new ScmException("dépôt GitLab invalide : identifiant ou chemin attendu", null);
        }
        String base = gitlabUrl + "/api/v4/projects/" + enc(ref.repository());
        String apres = enc(depuis.toString());
        boolean complet = true;

        List<GlIssue> issues = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/issues?updated_after=" + apres + "&per_page=" + PAR_PAGE + "&page=" + p),
                gitlabToken, false, new TypeReference<List<GlIssue>>() { }, issues, page -> true);
        List<ActiviteDepot.Ticket> tickets = issues.stream()
                .map(i -> new ActiviteDepot.Ticket(instant(i.createdAt()), instant(i.closedAt()))).toList();

        List<GlMergeRequest> mrs = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/merge_requests?updated_after=" + apres + "&per_page=" + PAR_PAGE
                + "&page=" + p), gitlabToken, false, new TypeReference<List<GlMergeRequest>>() { }, mrs, page -> true);
        List<ActiviteDepot.Fusion> fusions = mrs.stream()
                .map(m -> new ActiviteDepot.Fusion(instant(m.createdAt()), instant(m.mergedAt()))).toList();

        List<GlPipeline> pls = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/pipelines?updated_after=" + apres + "&per_page=" + PAR_PAGE + "&page=" + p),
                gitlabToken, false, new TypeReference<List<GlPipeline>>() { }, pls, page -> true);
        List<ActiviteDepot.Pipeline> pipelines = pls.stream()
                .map(pl -> new ActiviteDepot.Pipeline(instant(pl.createdAt()), issueGitlab(pl.status())))
                .filter(pl -> pl.creeLe() != null && !pl.creeLe().isBefore(depuis)).toList();

        List<GlRelease> releases = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/releases?per_page=" + PAR_PAGE + "&page=" + p), gitlabToken, false,
                new TypeReference<List<GlRelease>>() { }, releases,
                page -> page.stream().map(RepositoryActivity::publication).anyMatch(d -> d == null || !d.isBefore(depuis)));
        List<ActiviteDepot.Version> versions = releases.stream().map(RepositoryActivity::publication)
                .filter(d -> d != null && !d.isBefore(depuis)).map(ActiviteDepot.Version::new).toList();

        List<GlMilestone> milestones = new ArrayList<>();
        complet &= pages(p -> URI.create(base + "/milestones?per_page=" + PAR_PAGE + "&page=" + p), gitlabToken, false,
                new TypeReference<List<GlMilestone>>() { }, milestones, page -> true);
        List<ActiviteDepot.Jalon> jalons = new ArrayList<>();
        for (GlMilestone m : milestones) {
            boolean ouvert = "active".equalsIgnoreCase(m.state());
            Instant modifie = instant(m.updatedAt());
            if (m.id() == null || m.title() == null || (!ouvert && (modifie == null || modifie.isBefore(depuis)))) {
                continue;
            }
            if (jalons.size() >= jalonsMax) {
                complet = false;
                break;
            }
            GlIssuesStatistics stats = lireJson(URI.create(base + "/issues_statistics?milestone=" + enc(m.title())),
                    gitlabToken, false, new TypeReference<GlIssuesStatistics>() { });
            GlCounts counts = stats == null || stats.statistics() == null || stats.statistics().counts() == null
                    ? new GlCounts(0, 0) : stats.statistics().counts();
            int total = Math.max(0, counts.all());
            jalons.add(new ActiviteDepot.Jalon(reference(String.valueOf(m.id())), titre(m.title()), ouvert, date(m.dueDate()),
                    Math.min(total, Math.max(0, counts.closed())), total));
        }
        return new ActiviteDepot(tickets, fusions, pipelines, versions, jalons, complet);
    }

    private static Instant publication(GlRelease r) {
        Instant publiee = instant(r.releasedAt());
        return publiee != null ? publiee : instant(r.createdAt());
    }

    private static ActiviteDepot.Issue issueGitlab(String status) {
        if (status == null) {
            return ActiviteDepot.Issue.AUTRE;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "success" -> ActiviteDepot.Issue.REUSSI;
            case "failed" -> ActiviteDepot.Issue.ECHOUE;
            default -> ActiviteDepot.Issue.AUTRE;
        };
    }

    // --- Commun -------------------------------------------------------------------------------

    /**
     * Lit les pages successives tant qu'une page est pleine et que {@code continuer} l'accepte.
     *
     * @return faux si la lecture s'est arrêtée au nombre maximal de pages avec une page encore pleine
     */
    private <T> boolean pages(IntFunction<URI> uri, Optional<String> jeton, boolean github, TypeReference<List<T>> type,
            List<T> resultat, Predicate<List<T>> continuer) {
        for (int p = 1; p <= pagesMax; p++) {
            List<T> page = lireJson(uri.apply(p), jeton, github, type);
            List<T> contenu = page == null ? List.of() : page;
            resultat.addAll(contenu);
            if (contenu.size() < PAR_PAGE || !continuer.test(contenu)) {
                return true;
            }
        }
        return false;
    }

    private <T> T lireJson(URI uri, Optional<String> jeton, boolean github, TypeReference<T> type) {
        ForgeHttp.Reponse r = http.get(uri, jeton, github, github ? "application/vnd.github+json" : "application/json",
                tailleMax);
        if (r.status() != 200) {
            throw new ScmException("HTTP " + r.status() + " de " + uri.getHost(), null);
        }
        try {
            return JSON.readValue(r.corps(), type);
        } catch (IOException e) {
            throw new ScmException("réponse illisible de " + uri.getHost(), null);
        }
    }

    private static Instant instant(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valeur.strip()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate date(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valeur.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Texte externe : caractères de contrôle et de mise en forme retirés, 200 caractères au plus. */
    static String titre(String brut) {
        String t = brut == null ? "" : brut.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        if (t.isEmpty()) {
            return "(sans titre)";
        }
        return t.length() > TITRE_MAX ? t.substring(0, TITRE_MAX) : t;
    }

    private static String reference(String brut) {
        return brut.length() > REFERENCE_MAX ? brut.substring(0, REFERENCE_MAX) : brut;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
