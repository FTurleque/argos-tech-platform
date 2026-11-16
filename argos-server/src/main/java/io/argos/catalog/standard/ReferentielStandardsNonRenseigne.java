package io.argos.catalog.standard;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

/**
 * Référentiel vide, actif tant qu'aucune implémentation du référentiel des standards n'est
 * déployée : aucune technologie n'a de statut, ce que le serveur MCP restitue comme « aucune
 * information connue » sans rien inventer (MCP-02-S2).
 */
@DefaultBean
@ApplicationScoped
public class ReferentielStandardsNonRenseigne implements ReferentielStandards {

    @Override
    public List<Standard> standards(String cle, Set<UUID> espaces) {
        return List.of();
    }
}
