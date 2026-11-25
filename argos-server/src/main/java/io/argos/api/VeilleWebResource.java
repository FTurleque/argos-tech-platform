package io.argos.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import io.argos.api.security.Access;
import io.argos.commun.securite.Caller;
import io.argos.commun.securite.LimiteDebitAtteinte;
import io.argos.commun.securite.LimiteurDebit;
import io.argos.impact.RapprochementActualites;
import io.argos.signal.web.ActualitesWeb;
import io.argos.signal.web.CollecteWeb;
import io.argos.signal.web.ResumesIaActualites;
import io.argos.signal.web.SourceWeb;
import io.argos.signal.web.SourcesWeb;

/**
 * Veille RSS/Atom et pages surveillées (WEB-01 à WEB-06). Les règles d'autorisation sont appliquées
 * ici : une source personnelle appartient à l'appelant ; seul un administrateur de l'espace crée,
 * collecte ou supprime une source d'espace. Un non-membre reçoit 404, un rôle insuffisant 403 ;
 * chaque refus est journalisé (ACC-06).
 *
 * <p>Les requêtes qui déclenchent une récupération externe ({@code POST /sources}, réussie ou
 * refusée, et {@code POST /sources/{id}/collectes}) sont limitées par compte à
 * {@code argos.veille-web.debit.requetes-par-minute} sur une fenêtre glissante d'une minute ; au-delà,
 * la requête reçoit 429 ({@code LIMITE_DEBIT}) et le refus est journalisé. La demande d'un résumé IA
 * ({@code POST /actualites/{id}/resume-ia}), qui sollicite le modèle par la passerelle IA, partage cette
 * limite.
 */
@Path("/api/v1/veille-web")
@Tag(name = "Veille web")
@Produces(MediaType.APPLICATION_JSON)
public class VeilleWebResource {

    /** Corps de création d'une source. */
    public record NouvelleSource(String portee, UUID espaceId, String type, String adresse, String libelle) {
    }

    /** Actualité détaillée : résumé et rapprochements visibles. */
    public record DetailActualite(ActualitesWeb.Actualite actualite, ActualitesWeb.Resume resume,
            List<RapprochementActualites.Rapprochement> rapprochements) {
    }

    @Inject
    Access access;

    @Inject
    SourcesWeb sources;

    @Inject
    CollecteWeb collecte;

    @Inject
    ActualitesWeb actualites;

    @Inject
    RapprochementActualites rapprochements;

    @Inject
    ResumesIaActualites resumesIa;

    @ConfigProperty(name = "argos.veille-web.debit.requetes-par-minute", defaultValue = "10")
    int requetesParMinute;

    /** Limite commune à l'ajout et à la collecte à la demande, par compte. */
    private LimiteurDebit limiteur;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(requetesParMinute);
    }

    @POST
    @Path("/sources")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Ajoute un flux RSS/Atom ou une page surveillée, personnel ou d'un espace (administrateur)")
    @APIResponse(responseCode = "201", description = "Source créée et première collecte intégrée")
    @APIResponse(responseCode = "400", description = "Adresse interdite ou invalide, contenu qui n'est pas un flux ou une page")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Source d'espace demandée par un membre non administrateur")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    @APIResponse(responseCode = "409", description = "Limite de sources personnelles atteinte ou adresse déjà suivie")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte")
    public RestResponse<SourceWeb> creer(NouvelleSource corps) {
        Caller caller = access.authenticated();
        limiter(caller);
        NouvelleSource saisie = corps == null ? new NouvelleSource(null, null, null, null, null) : corps;
        if (saisie.espaceId() != null && saisie.portee() != null && SourceWeb.ESPACE.equalsIgnoreCase(saisie.portee().strip())) {
            exigerAdministrateur(caller, saisie.espaceId());
        }
        SourceWeb source = sources.creer(new SourcesWeb.Demande(saisie.portee(), saisie.espaceId(), saisie.type(), saisie.adresse(),
                saisie.libelle()), caller.accountId());
        return RestResponse.ResponseBuilder.<SourceWeb>created(URI.create("/api/v1/veille-web/sources/" + source.id()))
                .entity(source).build();
    }

    @GET
    @Path("/sources")
    @Operation(summary = "Sources visibles : personnelles de l'appelant et sources de ses espaces, avec leur état")
    @APIResponse(responseCode = "200", description = "Sources et état de chacune")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public List<SourceWeb> lister() {
        return sources.visibles(access.authenticated().accountId());
    }

    @POST
    @Path("/sources/{id}/collectes")
    @Operation(summary = "Collecte immédiatement une source (propriétaire ou administrateur de l'espace)")
    @APIResponse(responseCode = "200", description = "Résultat de la collecte ; une erreur fait passer la source EN_ERREUR")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Source d'espace et appelant non administrateur")
    @APIResponse(responseCode = "404", description = "Source inconnue ou invisible pour l'appelant")
    @APIResponse(responseCode = "409", description = "Collecte de cette source déjà en cours")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte")
    public CollecteWeb.Rapport collecter(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        limiter(caller);
        SourceWeb source = sourceGeree(caller, id);
        return collecte.collecter(source.id());
    }

    @DELETE
    @Path("/sources/{id}")
    @Operation(summary = "Supprime une source (propriétaire ou administrateur de l'espace)")
    @APIResponse(responseCode = "204", description = "Source supprimée")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Source d'espace et appelant non administrateur")
    @APIResponse(responseCode = "404", description = "Source inconnue ou invisible pour l'appelant")
    public RestResponse<Void> supprimer(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        SourceWeb source = sourceGeree(caller, id);
        if (!sources.supprimer(source, caller.accountId())) {
            throw access.forbidden();
        }
        return RestResponse.noContent();
    }

    @GET
    @Path("/actualites")
    @Operation(summary = "Actualités des sources visibles, chacune restituée une fois avec toutes ses sources")
    @APIResponse(responseCode = "200", description = "Actualités, contenu externe cité et marqué non fiable")
    @APIResponse(responseCode = "400", description = "Paramètre limite non entier")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace dont l'appelant n'est pas membre")
    public List<ActualitesWeb.Actualite> actualites(@QueryParam("espaceId") UUID espaceId,
            @Parameter(description = "Nombre maximal d'actualités, de 1 à 200 ; une valeur hors bornes est ramenée à la borne la"
                    + " plus proche ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "50")) @QueryParam("limite") String limite) {
        Caller caller = access.authenticated();
        if (espaceId != null && !caller.memberOf(espaceId)) {
            throw access.outOfScope();
        }
        return actualites.lister(caller.accountId(), espaceId, ParametresPagination.entier(limite, "limite", 50));
    }

    @GET
    @Path("/actualites/{id}")
    @Operation(summary = "Détail d'une actualité : résumé extractif et rapprochements « à vérifier »")
    @APIResponse(responseCode = "200", description = "Actualité, résumé et rapprochements")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Actualité inconnue ou invisible pour l'appelant")
    public DetailActualite actualite(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        ActualitesWeb.Detail detail = actualites.detail(id, caller.accountId()).orElseThrow(access::outOfScope);
        return new DetailActualite(detail.actualite(), detail.resume(), rapprochements.pour(id, caller.accountId()));
    }

    @POST
    @Path("/actualites/{id}/resume-ia")
    @Operation(summary = "Résumé IA d'une actualité visible, marqué « produit par l'IA » avec ses sources ; "
            + "sans IA disponible, résumé extractif déterministe")
    @APIResponse(responseCode = "200", description = "Résumé IA (état DISPONIBLE) ou extrait déterministe et motif")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Actualité inconnue ou invisible pour l'appelant")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte")
    public ResumesIaActualites.ResumeIaActualite resumeIa(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        limiter(caller);
        return resumesIa.resumer(id, caller.accountId()).orElseThrow(access::outOfScope);
    }

    private void limiter(Caller caller) {
        if (!limiteur.admettre(caller.accountId())) {
            throw new LimiteDebitAtteinte();
        }
    }

    private void exigerAdministrateur(Caller caller, UUID espaceId) {
        if (!caller.memberOf(espaceId)) {
            throw access.outOfScope();
        }
        if (!caller.adminOf(espaceId)) {
            throw access.forbidden();
        }
    }

    /** Source visible que l'appelant peut gérer : la sienne, ou une source d'un espace qu'il administre. */
    private SourceWeb sourceGeree(Caller caller, UUID id) {
        SourceWeb source = sources.trouver(id, caller.accountId()).orElseThrow(access::outOfScope);
        if (!source.personnelle()) {
            exigerAdministrateur(caller, source.espaceId());
        }
        return source;
    }
}
