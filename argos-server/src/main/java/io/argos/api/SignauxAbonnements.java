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

import io.argos.commun.securite.Caller;
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
        Map<UUID, SignalQueries.SignalView> trouves = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> retenusPar = new LinkedHashMap<>();
        for (SubscriptionService.Subscription abonnement : abonnements) {
            CriteresRecherche criteres = new CriteresRecherche(new HashSet<>(), new HashSet<>(), new HashSet<>());
            for (SubscriptionRequest.Filter filtre : abonnement.filters()) {
                if ("PROJECT".equals(filtre.type())) {
                    signals.findAll(projectSignals(filtre.value(), caller)).values()
                            .forEach(signal -> retenir(signal, abonnement.id(), trouves, retenusPar));
                } else {
                    criteres.ajouter(filtre);
                }
            }
            if (criteres.vides()) {
                continue;
            }
            for (SignalQueries.SignalView signal : signals.search(criteres.enCriteres(), PAR_ABONNEMENT)) {
                retenir(signal, abonnement.id(), trouves, retenusPar);
            }
        }
        return new Correspondances(trouves, retenusPar);
    }

    /** Critères d'un abonnement qui choisissent des signaux publics : technologies, paquets et sources. */
    private record CriteresRecherche(Set<String> technologies, Set<String> packages, Set<String> sources) {

        /** Range le filtre dans son critère ; VENDOR, THEME, DOMAIN et SEVERITY ne sont pas interprétés ici. */
        void ajouter(SubscriptionRequest.Filter filtre) {
            switch (filtre.type()) {
                case "TECHNOLOGY" -> technologies.add(filtre.value());
                case "PACKAGE" -> packages.add(filtre.value());
                case "SOURCE" -> sources.add(filtre.value());
                default -> {
                    // Sans taxonomie des signaux de sécurité pour ces critères.
                }
            }
        }

        boolean vides() {
            return technologies.isEmpty() && packages.isEmpty() && sources.isEmpty();
        }

        SignalQueries.Criteria enCriteres() {
            return new SignalQueries.Criteria(technologies, packages, sources);
        }
    }

    /** Retient le signal pour l'abonnement, dans l'ordre de première correspondance. */
    private static void retenir(SignalQueries.SignalView signal, UUID abonnementId,
            Map<UUID, SignalQueries.SignalView> trouves, Map<UUID, Set<UUID>> retenusPar) {
        trouves.putIfAbsent(signal.id(), signal);
        retenusPar.computeIfAbsent(signal.id(), cle -> new LinkedHashSet<>()).add(abonnementId);
    }

    /** Signaux qui impactent un projet désigné par un filtre, seulement s'il est dans le périmètre. */
    private List<UUID> projectSignals(String projectId, Caller caller) {
        UUID id;
        try {
            id = UUID.fromString(projectId);
        } catch (IllegalArgumentException illisible) {
            return List.of();
        }
        return impacts.forProject(id, caller.accountId(), caller.memberWorkspaces()).stream()
                .map(ImpactQueries.ImpactView::signalId).distinct().toList();
    }
}
