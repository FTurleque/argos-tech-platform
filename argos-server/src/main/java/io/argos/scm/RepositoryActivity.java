package io.argos.scm;

import static io.argos.scm.AccesForges.enc;
import static io.argos.scm.AccesForges.provider;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

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

    private final AccesForges forges;
    private final int pagesMax;
    private final int jalonsMax;
    private final long tailleMax;

    @Inject
    public RepositoryActivity(ParametresForges parametres,
            @ConfigProperty(name = "argos.scm.activite.pages-max", defaultValue = "10") int pagesMax,
            @ConfigProperty(name = "argos.scm.activite.jalons-max", defaultValue = "50") int jalonsMax,
            @ConfigProperty(name = "argos.scm.activite.taille-max-octets", defaultValue = "5242880") long tailleMax,
            Clock horloge) {
        this.forges = AccesForges.configure(parametres, horloge);
        this.pagesMax = Math.max(1, pagesMax);
        this.jalonsMax = Math.max(0, jalonsMax);
        this.tailleMax = tailleMax;
    }

    /** Activité du dépôt depuis {@code depuis}. */
    public ActiviteDepot lire(RepositoryRef ref, Instant depuis) {
        return switch (provider(ref)) {
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

    private static final TypeReference<List<GhIssue>> GH_ISSUES = new TypeReference<List<GhIssue>>() { };
    private static final TypeReference<GhRuns> GH_RUNS = new TypeReference<GhRuns>() { };
    private static final TypeReference<List<GhRelease>> GH_RELEASES = new TypeReference<List<GhRelease>>() { };
    private static final TypeReference<List<GhMilestone>> GH_MILESTONES = new TypeReference<List<GhMilestone>>() { };

    private ActiviteDepot github(RepositoryRef ref, Instant depuis) {
        if (!ForgeHttp.depotGithubValide(ref.repository())) {
            throw new ScmException("dépôt GitHub invalide : attendu owner/repo", null);
        }
        Forge forge = new Forge(forges.githubUrl() + "/repos/" + ref.repository(), forges.githubToken(), true);
        String jour = LocalDate.ofInstant(depuis, ZoneOffset.UTC).toString();

        Lot<GhIssue> issues = pages(forge, numero -> "/issues?state=all&since=" + enc(depuis.toString()) + "&" + pagination(numero),
                GH_ISSUES, RepositoryActivity::liste, page -> true);
        Lot<GhRun> runs = pages(forge, numero -> "/actions/runs?created=" + enc(">=" + jour) + "&" + pagination(numero),
                GH_RUNS, RepositoryActivity::executions, page -> true);
        Lot<GhRelease> releases = pages(forge, numero -> "/releases?" + pagination(numero), GH_RELEASES, RepositoryActivity::liste,
                page -> contientDesDatesDepuis(page.stream().map(rel -> instant(rel.publishedAt())), depuis));
        Lot<GhMilestone> milestones = pages(forge, numero -> "/milestones?state=all&" + pagination(numero), GH_MILESTONES,
                RepositoryActivity::liste, page -> true);
        Lot<ActiviteDepot.Jalon> jalons = jalons(milestones.elements(), jalon -> jalonGithubRetenu(jalon, depuis),
                RepositoryActivity::jalonGithub);

        return new ActiviteDepot(ticketsGithub(issues.elements()), fusionsGithub(issues.elements()),
                pipelines(runs.elements().stream()
                        .map(run -> new ActiviteDepot.Pipeline(instant(run.createdAt()), issueGithub(run.conclusion()))), depuis),
                versions(releases.elements().stream().filter(rel -> !rel.draft()).map(rel -> instant(rel.publishedAt())), depuis),
                jalons.elements(), tousComplets(issues, runs, releases, milestones, jalons));
    }

    /** Tickets GitHub : les éléments de {@code /issues} qui ne sont pas des pull requests. */
    private static List<ActiviteDepot.Ticket> ticketsGithub(List<GhIssue> issues) {
        return issues.stream().filter(issue -> issue.pullRequest() == null)
                .map(issue -> new ActiviteDepot.Ticket(instant(issue.createdAt()), instant(issue.closedAt()))).toList();
    }

    /** Pull requests GitHub : les éléments de {@code /issues} qui portent {@code pull_request}. */
    private static List<ActiviteDepot.Fusion> fusionsGithub(List<GhIssue> issues) {
        return issues.stream().filter(issue -> issue.pullRequest() != null)
                .map(issue -> new ActiviteDepot.Fusion(instant(issue.createdAt()), instant(issue.pullRequest().mergedAt()))).toList();
    }

    private static List<GhRun> executions(GhRuns page) {
        return page == null || page.runs() == null ? List.of() : page.runs();
    }

    /** Jalon retenu : numéroté, et ouvert ou fermé depuis {@code depuis}. */
    private static boolean jalonGithubRetenu(GhMilestone jalon, Instant depuis) {
        return jalon.number() != null && (jalonGithubOuvert(jalon) || estDepuis(instant(jalon.closedAt()), depuis));
    }

    private static boolean jalonGithubOuvert(GhMilestone jalon) {
        return "open".equalsIgnoreCase(jalon.state());
    }

    private static ActiviteDepot.Jalon jalonGithub(GhMilestone jalon) {
        Instant echeance = instant(jalon.dueOn());
        int fermes = Math.max(0, jalon.closedIssues());
        return new ActiviteDepot.Jalon(reference(String.valueOf(jalon.number())), titre(jalon.title()), jalonGithubOuvert(jalon),
                echeance == null ? null : LocalDate.ofInstant(echeance, ZoneOffset.UTC),
                fermes, fermes + Math.max(0, jalon.openIssues()));
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

    private static final TypeReference<List<GlIssue>> GL_ISSUES = new TypeReference<List<GlIssue>>() { };
    private static final TypeReference<List<GlMergeRequest>> GL_MERGE_REQUESTS = new TypeReference<List<GlMergeRequest>>() { };
    private static final TypeReference<List<GlPipeline>> GL_PIPELINES = new TypeReference<List<GlPipeline>>() { };
    private static final TypeReference<List<GlRelease>> GL_RELEASES = new TypeReference<List<GlRelease>>() { };
    private static final TypeReference<List<GlMilestone>> GL_MILESTONES = new TypeReference<List<GlMilestone>>() { };
    private static final TypeReference<GlIssuesStatistics> GL_STATISTIQUES = new TypeReference<GlIssuesStatistics>() { };

    private ActiviteDepot gitlab(RepositoryRef ref, Instant depuis) {
        if (ref.repository() == null || ref.repository().isBlank()) {
            throw new ScmException("dépôt GitLab invalide : identifiant ou chemin attendu", null);
        }
        Forge forge = new Forge(forges.gitlabUrl() + "/api/v4/projects/" + enc(ref.repository()), forges.gitlabToken(), false);
        String modifiesApres = "?updated_after=" + enc(depuis.toString()) + "&";

        Lot<GlIssue> issues = pages(forge, numero -> "/issues" + modifiesApres + pagination(numero), GL_ISSUES,
                RepositoryActivity::liste, page -> true);
        Lot<GlMergeRequest> mrs = pages(forge, numero -> "/merge_requests" + modifiesApres + pagination(numero), GL_MERGE_REQUESTS,
                RepositoryActivity::liste, page -> true);
        Lot<GlPipeline> pls = pages(forge, numero -> "/pipelines" + modifiesApres + pagination(numero), GL_PIPELINES,
                RepositoryActivity::liste, page -> true);
        Lot<GlRelease> releases = pages(forge, numero -> "/releases?" + pagination(numero), GL_RELEASES, RepositoryActivity::liste,
                page -> contientDesDatesDepuis(page.stream().map(RepositoryActivity::publication), depuis));
        Lot<GlMilestone> milestones = pages(forge, numero -> "/milestones?" + pagination(numero), GL_MILESTONES,
                RepositoryActivity::liste, page -> true);
        Lot<ActiviteDepot.Jalon> jalons = jalons(milestones.elements(), jalon -> jalonGitlabRetenu(jalon, depuis),
                jalon -> jalonGitlab(forge, jalon));

        return new ActiviteDepot(
                issues.elements().stream()
                        .map(issue -> new ActiviteDepot.Ticket(instant(issue.createdAt()), instant(issue.closedAt()))).toList(),
                mrs.elements().stream()
                        .map(fusion -> new ActiviteDepot.Fusion(instant(fusion.createdAt()), instant(fusion.mergedAt()))).toList(),
                pipelines(pls.elements().stream()
                        .map(pipeline -> new ActiviteDepot.Pipeline(instant(pipeline.createdAt()), issueGitlab(pipeline.status()))),
                        depuis),
                versions(releases.elements().stream().map(RepositoryActivity::publication), depuis),
                jalons.elements(), tousComplets(issues, mrs, pls, releases, milestones, jalons));
    }

    /** Jalon retenu : identifié et titré, et actif ou modifié depuis {@code depuis}. */
    private static boolean jalonGitlabRetenu(GlMilestone jalon, Instant depuis) {
        return jalon.id() != null && jalon.title() != null
                && ("active".equalsIgnoreCase(jalon.state()) || estDepuis(instant(jalon.updatedAt()), depuis));
    }

    /** Jalon GitLab et ses compteurs, lus par {@code /issues_statistics?milestone=:titre}. */
    private ActiviteDepot.Jalon jalonGitlab(Forge forge, GlMilestone jalon) {
        GlCounts counts = compteurs(lireJson(forge, "/issues_statistics?milestone=" + enc(jalon.title()), GL_STATISTIQUES));
        int total = Math.max(0, counts.all());
        return new ActiviteDepot.Jalon(reference(String.valueOf(jalon.id())), titre(jalon.title()),
                "active".equalsIgnoreCase(jalon.state()), date(jalon.dueDate()), Math.min(total, Math.max(0, counts.closed())), total);
    }

    private static GlCounts compteurs(GlIssuesStatistics stats) {
        return stats == null || stats.statistics() == null || stats.statistics().counts() == null
                ? new GlCounts(0, 0) : stats.statistics().counts();
    }

    private static Instant publication(GlRelease version) {
        Instant publiee = instant(version.releasedAt());
        return publiee != null ? publiee : instant(version.createdAt());
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

    /** Forge interrogée : adresse de base de l'API du dépôt, jeton facultatif et type de forge. */
    private record Forge(String base, Optional<String> jeton, boolean github) {

        String accept() {
            return github ? "application/vnd.github+json" : "application/json";
        }
    }

    /** Éléments lus et indicateur de complétude : faux si une borne (pages, jalons) a arrêté la lecture. */
    private record Lot<T>(List<T> elements, boolean complet) {
    }

    private static boolean tousComplets(Lot<?>... lots) {
        boolean complet = true;
        for (Lot<?> lot : lots) {
            complet &= lot.complet();
        }
        return complet;
    }

    private static String pagination(int page) {
        return "per_page=" + PAR_PAGE + "&page=" + page;
    }

    /**
     * Lit les pages successives tant qu'une page est pleine et que {@code continuer} l'accepte.
     *
     * @param chemin chemin de la page {@code p}, relatif à l'adresse de base de la forge
     * @param contenu éléments d'une page lue, liste vide si la page est absente
     * @return lot incomplet si la lecture s'est arrêtée au nombre maximal de pages avec une page encore pleine
     */
    private <R, T> Lot<T> pages(Forge forge, IntFunction<String> chemin, TypeReference<R> type, Function<R, List<T>> contenu,
            Predicate<List<T>> continuer) {
        List<T> elements = new ArrayList<>();
        for (int numero = 1; numero <= pagesMax; numero++) {
            List<T> page = contenu.apply(lireJson(forge, chemin.apply(numero), type));
            elements.addAll(page);
            if (page.size() < PAR_PAGE || !continuer.test(page)) {
                return new Lot<>(elements, true);
            }
        }
        return new Lot<>(elements, false);
    }

    private static <T> List<T> liste(List<T> page) {
        return page == null ? List.of() : page;
    }

    /**
     * Jalons retenus, dans l'ordre de la forge, convertis un à un ; lot incomplet si un jalon retenu dépasse
     * le nombre maximal de jalons.
     */
    private <M> Lot<ActiviteDepot.Jalon> jalons(List<M> milestones, Predicate<M> retenu,
            Function<M, ActiviteDepot.Jalon> conversion) {
        List<ActiviteDepot.Jalon> jalons = new ArrayList<>();
        for (M jalon : milestones) {
            if (!retenu.test(jalon)) {
                continue;
            }
            if (jalons.size() >= jalonsMax) {
                return new Lot<>(jalons, false);
            }
            jalons.add(conversion.apply(jalon));
        }
        return new Lot<>(jalons, true);
    }

    /** Pipelines datés et créés depuis {@code depuis}. */
    private static List<ActiviteDepot.Pipeline> pipelines(Stream<ActiviteDepot.Pipeline> pipelines, Instant depuis) {
        return pipelines.filter(pipeline -> estDepuis(pipeline.creeLe(), depuis)).toList();
    }

    /** Versions publiées depuis {@code depuis} ; une date absente n'est pas retenue. */
    private static List<ActiviteDepot.Version> versions(Stream<Instant> publications, Instant depuis) {
        return publications.filter(date -> estDepuis(date, depuis)).map(ActiviteDepot.Version::new).toList();
    }

    /** Vrai si une date est absente ou postérieure à {@code depuis} : la page suivante peut encore compter. */
    private static boolean contientDesDatesDepuis(Stream<Instant> dates, Instant depuis) {
        return dates.anyMatch(date -> date == null || !date.isBefore(depuis));
    }

    private static boolean estDepuis(Instant date, Instant depuis) {
        return date != null && !date.isBefore(depuis);
    }

    private <T> T lireJson(Forge forge, String chemin, TypeReference<T> type) {
        URI uri = URI.create(forge.base() + chemin);
        ForgeHttp.Reponse reponse = forges.http().get(uri, forge.jeton(), forge.github(), forge.accept(), tailleMax);
        if (reponse.status() != 200) {
            throw new ScmException("HTTP " + reponse.status() + " de " + uri.getHost(), null);
        }
        try {
            return JSON.readValue(reponse.corps(), type);
        } catch (IOException illisible) {
            throw new ScmException("réponse illisible de " + uri.getHost(), null);
        }
    }

    private static Instant instant(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valeur.strip()).toInstant();
        } catch (DateTimeParseException illisible) {
            return null;
        }
    }

    private static LocalDate date(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valeur.strip());
        } catch (DateTimeParseException illisible) {
            return null;
        }
    }

    /** Texte externe : caractères de contrôle et de mise en forme retirés, 200 caractères au plus. */
    static String titre(String brut) {
        String texte = brut == null ? "" : brut.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        if (texte.isEmpty()) {
            return "(sans titre)";
        }
        return texte.length() > TITRE_MAX ? texte.substring(0, TITRE_MAX) : texte;
    }

    private static String reference(String brut) {
        return brut.length() > REFERENCE_MAX ? brut.substring(0, REFERENCE_MAX) : brut;
    }

}
