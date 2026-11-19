package io.argos.api.recherche;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.impact.RechercheVeille;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.vue.VuesEnregistrees;

/**
 * Vues enregistrées de l'appelant (RCH-04) : une recherche et ses filtres sous un nom, rappelée et
 * supprimée par son seul auteur. La vue d'un autre utilisateur est refusée sans être révélée (404) et
 * le refus est journalisé (ACC-06). Une vue rappelée est rejouée sous les droits courants de l'appelant.
 */
@Path("/api/v1/vues")
@Tag(name = "Recherche")
@Produces(MediaType.APPLICATION_JSON)
public class VuesResource {

    /** Corps de création d'une vue. */
    @Schema(name = "NouvelleVue")
    public record NouvelleVue(String nom, CriteresRecherche criteres) {
    }

    /** Vue enregistrée. */
    @Schema(name = "VueEnregistree")
    public record VueEnregistree(UUID id, String nom, CriteresRecherche criteres, Instant creeLe) {
    }

    @Inject
    Access access;

    @Inject
    VuesEnregistrees vues;

    @Inject
    TraductionCriteres traduction;

    @Inject
    ObjectMapper mapper;

    @GET
    @Operation(summary = "Vues enregistrées de l'appelant")
    @APIResponse(responseCode = "200", description = "Vues de l'appelant, par nom")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    public List<VueEnregistree> lister() {
        Caller appelant = access.authenticated();
        return vues.lister(appelant.accountId()).stream().map(this::representation).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Enregistre une recherche et ses filtres sous un nom")
    @APIResponse(responseCode = "201", description = "Vue enregistrée")
    @APIResponse(responseCode = "400", description = "Nom ou filtre invalide")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre")
    @APIResponse(responseCode = "409", description = "Nom déjà utilisé ou nombre maximal de vues atteint")
    public RestResponse<VueEnregistree> creer(NouvelleVue corps) {
        Caller appelant = access.authenticated();
        NouvelleVue b = corps == null ? new NouvelleVue(null, null) : corps;
        CriteresRecherche criteres = b.criteres() == null
                ? new CriteresRecherche(null, null, null, null, null, null, null) : b.criteres();
        traduction.traduire(criteres, appelant, true);
        VueEnregistree vue = representation(vues.creer(appelant.accountId(), b.nom(), json(criteres)));
        return RestResponse.ResponseBuilder.<VueEnregistree>created(URI.create("/api/v1/vues/" + vue.id()))
                .entity(vue).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Vue enregistrée de l'appelant")
    @APIResponse(responseCode = "200", description = "Vue, ses mots et ses filtres")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Vue inconnue ou d'un autre utilisateur")
    public VueEnregistree vue(@PathParam("id") UUID id) {
        Caller appelant = access.authenticated();
        return representation(vues.trouver(appelant.accountId(), id));
    }

    @GET
    @Path("/{id}/resultats")
    @Operation(summary = "Rappelle une vue : applique ses mots et ses filtres sous les droits courants")
    @APIResponse(responseCode = "200", description = "Page de résultats de la vue")
    @APIResponse(responseCode = "400", description = "Filtre de la vue devenu invalide (profil retiré du catalogue),"
            + " paramètre page ou taille non entier ou hors bornes")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Vue inconnue ou d'un autre utilisateur")
    public RechercheVeille.Resultats resultats(@PathParam("id") UUID id,
            @Parameter(description = RechercheResource.PAGE, schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam("page") String page,
            @Parameter(description = RechercheResource.TAILLE, schema = @Schema(type = SchemaType.INTEGER, defaultValue = "20"))
            @QueryParam("taille") String taille) {
        Caller appelant = access.authenticated();
        VueEnregistree vue = representation(vues.trouver(appelant.accountId(), id));
        return traduction.executer(vue.criteres(), appelant, false, page, taille);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Supprime une vue de l'appelant")
    @APIResponse(responseCode = "204", description = "Vue supprimée")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Vue inconnue ou d'un autre utilisateur")
    public RestResponse<Void> supprimer(@PathParam("id") UUID id) {
        Caller appelant = access.authenticated();
        vues.supprimer(appelant.accountId(), id);
        return RestResponse.noContent();
    }

    private String json(CriteresRecherche criteres) {
        try {
            return mapper.writeValueAsString(criteres);
        } catch (JsonProcessingException e) {
            throw new DonneeInvalide("CRITERE_INVALIDE", "Critères illisibles.", "criteres");
        }
    }

    private VueEnregistree representation(VuesEnregistrees.Vue v) {
        try {
            return new VueEnregistree(v.id(), v.nom(), mapper.readValue(v.criteres(), CriteresRecherche.class), v.creeLe());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("critères de la vue illisibles", e);
        }
    }
}
