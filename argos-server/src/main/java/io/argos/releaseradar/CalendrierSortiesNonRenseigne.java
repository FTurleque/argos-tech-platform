package io.argos.releaseradar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

/**
 * Calendrier vide, actif tant qu'aucun connecteur de calendriers n'est déployé : aucune sortie ni
 * fin de support n'est connue, ce que le serveur MCP restitue sans rien inventer (MCP-02-S2).
 */
@DefaultBean
@ApplicationScoped
public class CalendrierSortiesNonRenseigne implements CalendrierSorties {

    @Override
    public List<Echeance> echeances(Collection<String> cles, LocalDate depuis, LocalDate jusqua) {
        return List.of();
    }

    @Override
    public Optional<Echeance> finDeSupport(String cle, String version) {
        return Optional.empty();
    }
}
