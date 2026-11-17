package io.argos.api;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.api.security.Caller;
import io.argos.impact.ImpactQueries;
import io.argos.signal.SignalQueries;
import io.argos.workspace.subscription.SubscriptionRequest;
import io.argos.workspace.subscription.SubscriptionService;

/**
 * Signaux publics retenus par des abonnements de veille (DF-ABO-2), partagés par la restitution des
 * abonnements ({@code GET /api/v1/me/watch}) et par la veille par profils ({@code GET /api/v1/me/veille},
 * décision Q-3 de {@code profils-veille}).
 *
 * <p>Un critère désigne ce qui intéresse l'utilisateur, jamais ce qu'il a le droit de voir (ABO-05) : les
 * critères {@code TECHNOLOGY}, {@code PACKAGE} et {@code SOURCE} choisissent des signaux publics ; un critère
 * {@code PROJECT} ne retient que les signaux qui impactent ce projet s'il appartient à un espace de
 * l'appelant (lecture sous la RLS). Les critères {@code VENDOR}, {@code THEME}, {@code DOMAIN} et
 * {@code SEVERITY} ne sont pas interprétés ici. Aucun impact n'est restitué par cette classe : l'appelant
 * lit lui-même les impacts de son périmètre.
 */
@ApplicationScoped
public class SignauxAbonnements {

    /** Nombre maximal de signaux lus par abonnement. */
    static final int PAR_ABONNEMENT = 100;

    /**
     * Signaux retenus, dans l'ordre de première correspondance.
     *
     * @param signaux    signaux publics retenus, par identifiant
     * @param retenusPar abonnements qui ont retenu chaque signal, dans l'ordre des abonnements
     */
    public record Correspondances(Map<UUID, SignalQueries.SignalView> signaux, Map<UUID, Set<UUID>> retenusPar) {
    }

    @Inject
    SignalQueries signals;

    @Inject
    ImpactQueries impacts;

    /** Signaux retenus par les abonnements donnés, pour l'appelant. */
    public Correspondances pour(List<SubscriptionService.Subscription> abonnements, Caller caller) {
        Map<UUID, SignalQueries.SignalView> found = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> matchedBy = new LinkedHashMap<>();
        for (SubscriptionService.Subscription s : abonnements) {
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
                        // VENDOR, THEME, DOMAIN, SEVERITY : sans taxonomie des signaux de sécurité dans ce lot.
                    }
                }
            }
            if (technologies.isEmpty() && packages.isEmpty() && sources.isEmpty()) {
                continue;
            }
            for (SignalQueries.SignalView v : signals.search(new SignalQueries.Criteria(technologies, packages, sources),
                    PAR_ABONNEMENT)) {
                found.putIfAbsent(v.id(), v);
                matchedBy.computeIfAbsent(v.id(), k -> new LinkedHashSet<>()).add(s.id());
            }
        }
        return new Correspondances(found, matchedBy);
    }

    /** Signaux qui impactent un projet désigné par un filtre, seulement s'il est dans le périmètre. */
    private List<UUID> projectSignals(String projectId, Caller caller) {
        UUID id;
        try {
            id = UUID.fromString(projectId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return impacts.forProject(id, caller.accountId(), caller.memberWorkspaces()).stream()
                .map(ImpactQueries.ImpactView::signalId).distinct().toList();
    }
}
