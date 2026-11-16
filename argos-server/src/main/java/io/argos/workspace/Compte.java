package io.argos.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Compte Argos, créé à la première connexion OIDC réussie (ACC-02). */
public record Compte(UUID id, String sujetOidc, String nomAffiche, String email, boolean adminPlateforme,
        OffsetDateTime creeLe) {
}
