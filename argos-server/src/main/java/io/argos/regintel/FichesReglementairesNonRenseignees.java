package io.argos.regintel;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

/** Aucune fiche, tant qu'aucune veille réglementaire n'est déployée. */
@DefaultBean
@ApplicationScoped
public class FichesReglementairesNonRenseignees implements FichesReglementaires {

    @Override
    public List<Fiche> fiches(Set<UUID> espaces, Optional<String> domaine) {
        return List.of();
    }
}
