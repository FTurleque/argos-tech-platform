package io.argos.mcp;

import static io.argos.mcp.OutilsSecurite.AVERTISSEMENT;
import static io.argos.mcp.OutilsSecurite.parametres;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import io.argos.catalog.standard.ReferentielStandards;
import io.argos.horloge.LectureHeure;
import io.argos.inventory.InventoryQueries;
import io.argos.releaseradar.CalendrierSorties;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

/**
 * Outils MCP du référentiel : standard d'une technologie dans les espaces de l'appelant et
 * échéances à venir (sorties, fins de support). Ils lisent les points d'extension
 * {@link ReferentielStandards} et {@link CalendrierSorties}.
 */
public class OutilsReferentiel {

    /** Réponse de {@code get_technology_standard}. */
    public record StandardTechnologique(String cle, boolean connu, String constat,
            List<OutilsSecurite.StandardEspace> standards) {
    }

    /** Échéance à venir. */
    public record Echeance(String cle, String version, String nature, String date, String source, TexteExterne note) {
    }

    /** Réponse de {@code upcoming_releases}. */
    public record Echeances(String jusqua, int nombre, String constat, List<Echeance> echeances) {
    }

    @Inject
    Clock horloge;

    @Inject
    GardeAppels garde;

    @Inject
    BalisageExterne balisage;

    @Inject
    ReferentielStandards referentiel;

    @Inject
    CalendrierSorties calendrier;

    @Inject
    InventoryQueries inventaires;

    @Tool(name = "get_technology_standard", structuredContent = true,
            annotations = @Tool.Annotations(title = "Standard d'une technologie", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Statut d'une technologie au référentiel des standards des espaces de l'utilisateur (ADOPT,"
                    + " TRIAL, ASSESS, HOLD), version recommandée et justification. Une technologie absente"
                    + " du référentiel est signalée comme inconnue.")
    public StandardTechnologique getTechnologyStandard(
            @ToolArg(name = "ecosystem", description = "Écosystème : Maven, npm, PyPI…", required = false) String ecosysteme,
            @ToolArg(name = "name", description = "Nom du paquet (Maven : groupe:artefact) ou PURL sans version.") String nom) {
        return garde.appeler("get_technology_standard", parametres("ecosystem", ecosysteme, "name", nom), appelant -> {
            String cle = Parametres.cle(ecosysteme, nom);
            List<OutilsSecurite.StandardEspace> standards = referentiel.standards(cle, appelant.espacesMembre()).stream()
                    .filter(standard -> appelant.memberOf(standard.espaceId()))
                    .map(standard -> new OutilsSecurite.StandardEspace(standard.espaceId(), standard.statut().name(),
                            standard.versionRecommandee(), standard.justification()))
                    .toList();
            return new StandardTechnologique(cle, !standards.isEmpty(), standards.isEmpty()
                    ? "Aucun standard n'est défini pour cette technologie dans vos espaces." : null, standards);
        });
    }

    @Tool(name = "upcoming_releases", structuredContent = true,
            annotations = @Tool.Annotations(title = "Sorties et fins de support à venir", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Sorties et fins de support à venir pour une technologie, ou à défaut pour les composants des"
                    + " projets de l'utilisateur, sur un horizon en jours." + AVERTISSEMENT)
    public Echeances upcomingReleases(
            @ToolArg(name = "ecosystem", description = "Écosystème, facultatif.", required = false) String ecosysteme,
            @ToolArg(name = "name", description = "Nom du paquet ou PURL, facultatif.", required = false) String nom,
            @ToolArg(name = "horizon_days", description = "Horizon en jours (1 à 730, 90 par défaut).", required = false) Integer horizon) {
        return garde.appeler("upcoming_releases", parametres("ecosystem", ecosysteme, "name", nom, "horizon_days", horizon),
                appelant -> {
                    Optional<String> cle = Parametres.cleFacultative(ecosysteme, nom);
                    int jours = Parametres.borne("horizon_days", horizon, 90, 1, 730);
                    Set<String> cles = new LinkedHashSet<>();
                    if (cle.isPresent()) {
                        cles.add(cle.get());
                    } else {
                        ProjetsVisibles.duCompte(appelant.accountId(), inventaires)
                                .filter(projet -> appelant.memberOf(projet.espaceId()))
                                .forEach(projet -> cles.addAll(inventaires.clesDePaquets(projet.id(), appelant.accountId())));
                    }
                    LocalDate aujourdhui = LectureHeure.jour(horloge);
                    LocalDate jusqua = aujourdhui.plusDays(jours);
                    List<Echeance> echeances = cles.isEmpty() ? List.of()
                            : calendrier.echeances(cles, appelant.espacesMembre(), aujourdhui, jusqua).stream()
                                    .map(echeance -> new Echeance(echeance.cle(), echeance.version(), echeance.nature().name(),
                                            echeance.date().toString(), echeance.source(),
                                            balisage.baliser(echeance.source(), echeance.note())))
                                    .toList();
                    return new Echeances(jusqua.toString(), echeances.size(),
                            echeances.isEmpty() ? "Aucune échéance connue d'Argos sur cet horizon." : null, echeances);
                });
    }
}
