package io.argos.api;

import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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
import io.argos.api.security.Caller;
import io.argos.api.security.LimiteDebitAtteinte;
import io.argos.api.security.LimiteurDebit;
import io.argos.inventory.InventoryQueries;
import io.argos.projectintel.ActiviteQueries;
import io.argos.projectintel.SyntheseActivite;
import io.argos.projectintel.VueActivite;

/**
 * Activité factuelle d'un projet (capacité {@code activite-projets}) : compteurs sur une période,
 * jalons avec leur avancement et son dénominateur, fraîcheur des données (PIN-01, PIN-02, PIN-05) et
 * synthèse IA marquée (PIN-04). Aucune opération ne restitue d'indicateur par personne (PIN-03).
 * Réservée aux membres de l'espace du projet ; un non-membre reçoit 404 et le refus est journalisé.
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

    @ConfigProperty(name = "argos.activite.synthese.debit.requetes-par-minute", defaultValue = "10")
    int requetesParMinute;

    /** Limite des demandes de synthèse, qui sollicitent le modèle d'IA, par compte. */
    private LimiteurDebit limiteur;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(requetesParMinute);
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

    private Caller autoriser(UUID projectId) {
        Caller caller = access.authenticated();
        UUID espace = projets.workspaceOf(projectId).orElseThrow(NotFoundException::new);
        if (!caller.memberOf(espace)) {
            throw access.outOfScope();
        }
        return caller;
    }
}
