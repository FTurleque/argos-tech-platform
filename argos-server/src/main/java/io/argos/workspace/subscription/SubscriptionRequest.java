package io.argos.workspace.subscription;

import java.util.List;
import java.util.UUID;

/**
 * Demande de création d'un abonnement de veille (ABO-01, ABO-02). Le propriétaire est un espace
 * d'équipe pour la portée {@code TEAM}, un utilisateur pour la portée {@code PERSONAL}.
 */
public record SubscriptionRequest(String scope, UUID ownerWorkspaceId, UUID ownerUserId, List<Filter> filters,
        String channel, String frequency, Boolean mandatory) {

    /**
     * Critère de veille. Types : TECHNOLOGY, PACKAGE, VENDOR, THEME, DOMAIN, SOURCE, PROJECT.
     * Un critère désigne ce qui intéresse l'utilisateur, jamais ce qu'il a le droit de voir (ABO-05).
     */
    public record Filter(String type, String value) {
    }
}
