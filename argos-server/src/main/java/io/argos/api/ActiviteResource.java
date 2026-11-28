package io.argos.api;

import java.time.Clock;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.commun.securite.Caller;
import io.argos.commun.securite.LimiteDebitAtteinte;
import io.argos.commun.securite.LimiteurDebit;
import io.argos.inventory.InventoryQueries;
import io.argos.projectintel.ActiviteQueries;
import io.argos.projectintel.CollecteActivite;
import io.argos.projectintel.SyntheseActivite;
import io.argos.projectintel.VueActivite;

/**
 * Activité factuelle d'un projet (capacité {@code activite-projets}) : compteurs sur une période,
 * jalons avec leur avancement et son dénominateur, fraîcheur des données (PIN-01, PIN-02, PIN-05) et
 * synthèse IA marquée (PIN-04). Aucune opération ne restitue d'indicateur par personne (PIN-03).
 * Réservée aux membres de l'espace du projet ; un non-membre reçoit 404 et le refus est journalisé. La collecte
 * immédiate (PIN-06) est réservée aux contributeurs et administrateurs de l'espace.
 */
@Path("/api/v1/projects/{projectId}/activite")
@Tag(name = "Activité des projets")
@Produces(MediaType.APPLICATION_JSON)
public class ActiviteResource {

    @Inject
    Access access;

    @Inject
    InventoryQueries projets;

    @Inject
    ActiviteQueries activites;

    @Inject
    SyntheseActivite syntheses;

    @Inject
    CollecteActivite collecte;

    @Inject
    Clock horloge;

    /** Issue d'une collecte immédiate. */
    public record Collecte(CollecteActivite.Issue issue) {
    }

    @ConfigProperty(name = "argos.activite.synthese.debit.requetes-par-minute", defaultValue = "10")
    int requetesParMinute;

    @ConfigProperty(name = "argos.forges.debit.requetes-par-minute", defaultValue = "6")
    int lecturesForgeParMinute;

    /** Limite des demandes de synthèse, qui sollicitent le modèle d'IA, par compte. */
    private LimiteurDebit limiteur;

    /** Limite des collectes immédiates, qui consomment le quota de la forge, par compte. */
    private LimiteurDebit limiteurCollecte;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(requetesParMinute);
        limiteurCollecte = new LimiteurDebit(lecturesForgeParMinute);
    }

    @GET
    @Operation(summary = "Compteurs d'activité du projet sur une période, jalons et fraîcheur des données")
    @APIResponse(responseCode = "200", description = "Activité agrégée du projet, sans indicateur par personne")
    @APIResponse(responseCode = "400", description = "Période hors de 1 à 397 jours")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre")
    public VueActivite activite(@PathParam("projectId") UUID projectId, @QueryParam("jours") @DefaultValue("30") int jours) {
        Caller caller = autoriser(projectId);
        return activites.activite(projectId, caller.accountId(), jours);
    }

    @GET
    @Path("/synthese")
    @Operation(summary = "Synthèse produite par l'IA à partir des seuls indicateurs affichés")
    @APIResponse(responseCode = "200", description = "Synthèse marquée « produite par l'IA » et indicateurs dont elle découle")
    @APIResponse(responseCode = "400", description = "Période hors de 1 à 397 jours")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte")
    public SyntheseActivite.Synthese synthese(@PathParam("projectId") UUID projectId,
            @QueryParam("jours") @DefaultValue("30") int jours) {
        Caller caller = autoriser(projectId);
        VueActivite vue = activites.activite(projectId, caller.accountId(), jours);
        if (!limiteur.admettre(caller.accountId())) {
            throw new LimiteDebitAtteinte();
        }
        return syntheses.produire(vue);
    }

    @POST
    @Path("/collecte")
    @Operation(summary = "Collecter immédiatement l'activité du projet, en lecture seule sur sa forge (PIN-06)")
    @APIResponse(responseCode = "200", description = "Issue : A_JOUR, REPORTEE_QUOTA, ECHEC ou SANS_DEPOT ;"
            + " les indicateurs existants sont conservés sauf A_JOUR")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Lecteur de l'espace du projet (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte")
    public Collecte collecter(@PathParam("projectId") UUID projectId) {
        access.authenticated();
        Caller caller = access.contributor(projets.espaceDe(projectId).orElseThrow(NotFoundException::new));
        if (!limiteurCollecte.admettre(caller.accountId())) {
            throw new LimiteDebitAtteinte();
        }
        return new Collecte(collecte.collecterProjet(projectId, horloge.instant()));
    }

    private Caller autoriser(UUID projectId) {
        return access.memberOfProject(() -> projets.espaceDe(projectId));
    }
}
