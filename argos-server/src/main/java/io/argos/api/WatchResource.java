package io.argos.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    SignauxAbonnements signauxAbonnements;

    @Inject
    ImpactQueries impacts;

    @GET
    @Operation(summary = "Veille de l'appelant : socle TEAM hérité et abonnements PERSONAL")
    public List<WatchItem> watch() {
        Caller caller = access.authenticated();
        Set<UUID> scope = caller.memberWorkspaces();
        SignauxAbonnements.Correspondances retenus = signauxAbonnements.pour(
                subscriptions.activeFor(caller.accountId(), scope), caller);

        Map<UUID, List<WatchImpact>> visible = new LinkedHashMap<>();
        for (ImpactQueries.ImpactView impact : impacts.forSignals(retenus.signaux().keySet(), caller.accountId(), scope)) {
            visible.computeIfAbsent(impact.signalId(), key -> new ArrayList<>())
                    .add(new WatchImpact(impact.id(), impact.projectId(), impact.projectName(), impact.level()));
        }
        List<WatchItem> items = new ArrayList<>();
        for (SignalQueries.SignalView signal : retenus.signaux().values()) {
            items.add(new WatchItem(signal.id(), signal.source(), signal.externalId(), signal.title(), signal.severity(),
                    signal.modifiedAt(), retenus.retenusPar().get(signal.id()), visible.getOrDefault(signal.id(), List.of())));
        }
        return items;
    }
}
