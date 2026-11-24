package io.argos.api;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.impact.EtatImpact;
import io.argos.impact.ImpactQueries;
import io.argos.impact.TraitementImpacts;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.RegleViolee;

/**
 * Consultation d'un impact et de sa provenance (COR-03-S1), limitée au périmètre autorisé (COR-08-S2), avec son état
 * de traitement et son historique (COR-09, COR-12) ; transitions d'état demandées par un membre {@code CONTRIBUTEUR}
 * ou {@code ADMINISTRATEUR} de l'espace du projet (COR-10, décision J5 du porteur du projet, 2026-11-19 ; P-5).
 */
@Path("/api/v1")
@Tag(name = "Impacts")
@Produces(MediaType.APPLICATION_JSON)
public class ImpactResource {

    /** Demande de transition d'état d'un impact. */
    @Schema(name = "DemandeTransitionImpact", description = "Transition d'état demandée pour un impact")
    public record DemandeTransition(
            @Schema(description = "État d'arrivée", enumeration = {"OUVERT", "CONFIRME", "ECARTE", "RESOLU"},
                    required = true) String vers,
            @Schema(description = "Motif, obligatoire pour ECARTE ; au plus 1 000 caractères") String motif,
            @Schema(description = "Commentaire facultatif ; au plus 1 000 caractères") String commentaire) {
    }

    @Inject
    Clock horloge;

    @Inject
    Access access;

    @Inject
    ImpactQueries impacts;

    @Inject
    TraitementImpacts traitementImpacts;

    @GET
    @Path("/impacts/{id}")
    @Operation(summary = "Impact, signal source, projet, composant, inventaire utilisé, méthode d'analyse, état de traitement"
            + " et historique de ses transitions")
    public ImpactQueries.ImpactView impact(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        ImpactQueries.ImpactView view = visible(caller, id);
        return avecTransitions(caller, view);
    }

    @POST
    @Path("/impacts/{id}/transitions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change l'état de traitement d'un impact (CONTRIBUTEUR ou ADMINISTRATEUR de l'espace du projet)")
    @APIResponse(responseCode = "200", description = "Impact après la transition, avec son historique")
    @APIResponse(responseCode = "400", description = "État inconnu, motif manquant pour ECARTE ou texte trop long (DONNEE_INVALIDE)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Rôle insuffisant (LECTEUR) ; refus journalisé")
    @APIResponse(responseCode = "404", description = "Impact inconnu ou d'un autre espace ; refus journalisé dans le second cas")
    @APIResponse(responseCode = "409", description = "Transition non prévue depuis l'état courant (TRANSITION_INTERDITE),"
            + " le message nomme les transitions possibles")
    public ImpactQueries.ImpactView transition(@PathParam("id") UUID id, DemandeTransition demande) {
        Caller caller = access.authenticated();
        ImpactQueries.ImpactView view = visible(caller, id);
        if (!caller.contributorOf(view.workspaceId())) {
            throw access.forbidden();
        }
        EtatImpact vers = vers(demande);
        String motif = demande.motif();
        if (vers == EtatImpact.ECARTE && (motif == null || motif.isBlank())) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Un motif est obligatoire pour écarter un impact.", "motif");
        }
        longueur(motif, "motif");
        longueur(demande.commentaire(), "commentaire");
        Optional<EtatImpact> atteint;
        try {
            atteint = traitementImpacts.transitionner(id, caller.accountId(), vers, motif, demande.commentaire(), horloge.instant());
        } catch (TraitementImpacts.TransitionInterdite interdite) {
            throw new RegleViolee("TRANSITION_INTERDITE", "Transition vers " + vers + " impossible depuis l'état "
                    + interdite.courant() + ". Transitions possibles : " + noms(interdite.possibles()) + ".");
        } catch (TraitementImpacts.ModificationRefusee refusee) {
            throw access.forbidden();
        }
        if (atteint.isEmpty()) {
            throw new NotFoundException();
        }
        return avecTransitions(caller, visible(caller, id));
    }

    @GET
    @Path("/ops/impacts/integrity")
    @Operation(summary = "Contrôle d'intégrité : aucun impact sans signal, projet, méthode ou provenance, aucun impact"
            + " ouvert ou confirmé sans composant")
    public ImpactQueries.IntegrityReport integrity() {
        access.operator();
        return impacts.integrity();
    }

    /** Impact visible de l'appelant ; sinon 404, avec un refus journalisé s'il existe dans un autre espace. */
    private ImpactQueries.ImpactView visible(Caller caller, UUID id) {
        Optional<ImpactQueries.ImpactView> view = impacts.find(id, caller.accountId(), caller.memberWorkspaces());
        if (view.isPresent()) {
            return view.get();
        }
        if (impacts.workspaceOf(id).isPresent()) {
            throw access.outOfScope();
        }
        throw new NotFoundException();
    }

    /** Transitions que l'appelant peut demander : aucune pour un lecteur. */
    private static ImpactQueries.ImpactView avecTransitions(Caller caller, ImpactQueries.ImpactView view) {
        List<String> possibles = caller.contributorOf(view.workspaceId())
                ? EtatImpact.valueOf(view.etat()).suivants(view.componentId() != null).stream().sorted(Comparator.naturalOrder())
                        .map(EtatImpact::name).toList()
                : List.of();
        return view.avecTransitionsPossibles(possibles);
    }

    private static EtatImpact vers(DemandeTransition demande) {
        if (demande == null || demande.vers() == null || demande.vers().isBlank()) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "L'état d'arrivée « vers » est obligatoire.", "vers");
        }
        return EtatImpact.depuis(demande.vers()).orElseThrow(() -> new DonneeInvalide("DONNEE_INVALIDE",
                "État inconnu : OUVERT, CONFIRME, ECARTE ou RESOLU attendu.", "vers"));
    }

    private static void longueur(String texte, String attribut) {
        if (texte != null && texte.strip().length() > TraitementImpacts.LONGUEUR_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le champ « " + attribut + " » dépasse "
                    + TraitementImpacts.LONGUEUR_MAX + " caractères.", attribut);
        }
    }

    private static String noms(java.util.Set<EtatImpact> etats) {
        return etats.stream().sorted().map(EtatImpact::name).collect(Collectors.joining(", "));
    }
}
