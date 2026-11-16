package io.argos.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.impact.ImpactQueries;
import io.argos.signal.SignalQueries;
import io.argos.workspace.subscription.SubscriptionRequest;
import io.argos.workspace.subscription.SubscriptionService;

/**
 * Restitution de la veille de l'appelant (DF-ABO-2) : les abonnements choisissent les signaux
 * publics à restituer ; les impacts internes associés sont toujours limités aux espaces dont
 * l'appelant est membre, quel que soit le filtre (ABO-05, COR-08).
 */
@Path("/api/v1/me/watch")
@Tag(name = "Veille")
@Produces(MediaType.APPLICATION_JSON)
public class WatchResource {

    private static final int PER_SUBSCRIPTION = 100;

    /** Impact restitué dans la veille : seulement ceux du périmètre autorisé. */
    public record WatchImpact(UUID impactId, UUID projectId, String projectName, String level) {
    }

    /** Élément de veille : un signal, les abonnements qui l'ont retenu et les impacts visibles. */
    public record WatchItem(UUID signalId, String source, String externalId, String title, String severity,
            Instant modifiedAt, Set<UUID> subscriptions, List<WatchImpact> impacts) {
    }

    @Inject
    Access access;

    @Inject
    SubscriptionService subscriptions;

    @Inject
    SignalQueries signals;

    @Inject
    ImpactQueries impacts;

    @GET
    @Operation(summary = "Veille de l'appelant : socle TEAM hérité et abonnements PERSONAL")
    public List<WatchItem> watch() {
        Caller caller = access.authenticated();
        Set<UUID> scope = caller.memberWorkspaces();
        Map<UUID, SignalQueries.SignalView> found = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> matchedBy = new LinkedHashMap<>();

        for (SubscriptionService.Subscription s : subscriptions.activeFor(caller.accountId(), scope)) {
            Set<String> technologies = new HashSet<>();
            Set<String> packages = new HashSet<>();
            Set<String> sources = new HashSet<>();
            for (SubscriptionRequest.Filter f : s.filters()) {
                switch (f.type()) {
                    case "TECHNOLOGY" -> technologies.add(f.value());
                    case "PACKAGE" -> packages.add(f.value());
                    case "SOURCE" -> sources.add(f.value());
                    case "PROJECT" -> projectSignals(f.value(), caller).forEach(id -> signals.find(id).ifPresent(v -> {
                        found.putIfAbsent(v.id(), v);
                        matchedBy.computeIfAbsent(v.id(), k -> new LinkedHashSet<>()).add(s.id());
                    }));
                    default -> {
                        // VENDOR, THEME, DOMAIN : sans taxonomie des signaux de sécurité dans ce lot.
                    }
                }
            }
            if (technologies.isEmpty() && packages.isEmpty() && sources.isEmpty()) {
                continue;
            }
            for (SignalQueries.SignalView v : signals.search(new SignalQueries.Criteria(technologies, packages, sources),
                    PER_SUBSCRIPTION)) {
                found.putIfAbsent(v.id(), v);
                matchedBy.computeIfAbsent(v.id(), k -> new LinkedHashSet<>()).add(s.id());
            }
        }

        Map<UUID, List<WatchImpact>> visible = new LinkedHashMap<>();
        for (ImpactQueries.ImpactView i : impacts.forSignals(found.keySet(), caller.accountId(), scope)) {
            visible.computeIfAbsent(i.signalId(), k -> new ArrayList<>())
                    .add(new WatchImpact(i.id(), i.projectId(), i.projectName(), i.level()));
        }
        List<WatchItem> items = new ArrayList<>();
        for (SignalQueries.SignalView v : found.values()) {
            items.add(new WatchItem(v.id(), v.source(), v.externalId(), v.title(), v.severity(), v.modifiedAt(),
                    matchedBy.get(v.id()), visible.getOrDefault(v.id(), List.of())));
        }
        return items;
    }

    /** Signaux qui impactent un projet désigné par un filtre, seulement s'il est dans le périmètre. */
    private List<UUID> projectSignals(String projectId, Caller caller) {
        UUID id;
        try {
            id = UUID.fromString(projectId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return impacts.forProject(id, caller.accountId(), caller.memberWorkspaces()).stream().map(ImpactQueries.ImpactView::signalId).distinct().toList();
    }
}
