package io.argos.api;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.alerting.RappelsRadar;
import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.catalog.standard.ReferentielEspace;
import io.argos.catalog.standard.SaisieStandard;
import io.argos.impact.RadarProjets;
import io.argos.releaseradar.CalendrierSorties;
import io.argos.releaseradar.CyclesDeVie;
import io.argos.workspace.DonneeInvalide;

/**
 * Référentiel des standards et radar des sorties d'un espace (RAD-01 à RAD-06). Les membres de l'espace
 * consultent ; seuls ses administrateurs modifient le référentiel, décident des suggestions de l'IA et
 * fixent les jours de rappel (RAD-02). Un non-membre reçoit 404, un rôle insuffisant 403 ; chaque refus
 * est journalisé (ACC-06).
 */
@Path("/api/v1/radar/espaces/{espaceId}")
@Tag(name = "Radar et standards")
@Produces(MediaType.APPLICATION_JSON)
public class RadarResource {

    /** Corps d'une décision sur une suggestion : {@code ACCEPTER} ou {@code REJETER}. */
    public record CorpsDecision(String decision, String responsable, String dateRevue) {
    }

    /** Jours de rappel avant une échéance (0 : jour même). */
    public record Parametres(List<Integer> rappelsJours) {
    }

    /** Échéance du radar, avec le libellé de la technologie au référentiel. */
    public record EcheanceRadar(String cle, String libelle, String version, String nature, LocalDate date, String source,
            String note) {
    }

    /** Radar d'un horizon : échéances et fraîcheur du calendrier des produits suivis. */
    public record Radar(String horizon, LocalDate depuis, LocalDate jusqua, List<EcheanceRadar> echeances,
            List<CyclesDeVie.EtatProduit> sources) {
    }

    @Inject
    Access access;

    @Inject
    ReferentielEspace referentiel;

    @Inject
    CalendrierSorties calendrier;

    @Inject
    CyclesDeVie cycles;

    @Inject
    RadarProjets radar;

    @Inject
    RappelsRadar rappels;

    /** Fuseau de la tâche quotidienne : le radar et les rappels comptent les jours de la même façon. */
    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @GET
    @Path("/standards")
    @Operation(summary = "Référentiel des standards de l'espace (membres)")
    @APIResponse(responseCode = "200", description = "Technologies, statuts, versions recommandées, responsables et revues")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    public List<ReferentielEspace.EntreeReferentiel> standards(@PathParam("espaceId") UUID espaceId) {
        Caller caller = access.member(espaceId);
        return referentiel.lister(caller.accountId(), espaceId);
    }

    @PUT
    @Path("/standards")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Crée ou modifie l'entrée d'une technologie (administrateur de l'espace)")
    @APIResponse(responseCode = "200", description = "Entrée enregistrée et historisée ; un changement de statut ou de version est annoncé")
    @APIResponse(responseCode = "400", description = "Champ manquant ou invalide, nommé dans l'attribut de l'erreur")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Membre non administrateur de l'espace")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    @APIResponse(responseCode = "409", description = "Modification concurrente de la même technologie")
    public ReferentielEspace.EntreeReferentiel enregistrer(@PathParam("espaceId") UUID espaceId, SaisieStandard saisie) {
        Caller caller = access.administrator(espaceId);
        if (saisie == null) {
            throw new DonneeInvalide("CHAMP_MANQUANT", "Le corps de la demande est obligatoire.", "cle");
        }
        return referentiel.enregistrer(caller.accountId(), espaceId, saisie);
    }

    @GET
    @Path("/standards/historique")
    @Operation(summary = "Historique d'une technologie du référentiel (membres)")
    @APIResponse(responseCode = "200", description = "Valeurs successives, de la plus récente à la plus ancienne")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    public List<ReferentielEspace.Version> historique(@PathParam("espaceId") UUID espaceId, @QueryParam("cle") String cle) {
        Caller caller = access.member(espaceId);
        if (cle == null || cle.isBlank()) {
            throw new DonneeInvalide("CHAMP_MANQUANT", "Le paramètre cle est obligatoire.", "cle");
        }
        return referentiel.historique(caller.accountId(), espaceId, cle.strip());
    }

    @GET
    @Path("/suggestions")
    @Operation(summary = "Suggestions de l'IA pour le référentiel, en attente d'abord (membres)")
    @APIResponse(responseCode = "200", description = "Suggestions et leur état")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    public List<ReferentielEspace.Suggestion> suggestions(@PathParam("espaceId") UUID espaceId) {
        Caller caller = access.member(espaceId);
        return referentiel.suggestions(caller.accountId(), espaceId);
    }

    @POST
    @Path("/suggestions/{suggestionId}/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Accepte ou rejette une suggestion en attente (administrateur de l'espace)")
    @APIResponse(responseCode = "200", description = "Décision enregistrée ; acceptée, la suggestion est appliquée au référentiel")
    @APIResponse(responseCode = "400", description = "Décision inconnue ou entrée résultante invalide")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Membre non administrateur de l'espace")
    @APIResponse(responseCode = "404", description = "Espace ou suggestion introuvable")
    @APIResponse(responseCode = "409", description = "Suggestion déjà décidée")
    public List<ReferentielEspace.Suggestion> decider(@PathParam("espaceId") UUID espaceId,
            @PathParam("suggestionId") UUID suggestionId, CorpsDecision corps) {
        Caller caller = access.administrator(espaceId);
        String d = corps == null || corps.decision() == null ? "" : corps.decision().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("ACCEPTER", "REJETER").contains(d)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Décision attendue : ACCEPTER ou REJETER.", "decision");
        }
        referentiel.decider(caller.accountId(), espaceId, suggestionId,
                new ReferentielEspace.Decision("ACCEPTER".equals(d), corps.responsable(), corps.dateRevue()));
        return referentiel.suggestions(caller.accountId(), espaceId);
    }

    @GET
    @Path("/echeances")
    @Operation(summary = "Radar : sorties, correctifs et fins de support des technologies de l'espace sur un horizon (membres)")
    @APIResponse(responseCode = "200", description = "Échéances par date croissante, source citée, et fraîcheur du calendrier")
    @APIResponse(responseCode = "400", description = "Horizon inconnu : 30j, 6m ou 1a")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    public Radar echeances(@PathParam("espaceId") UUID espaceId, @QueryParam("horizon") @DefaultValue("30j") String horizon) {
        Caller caller = access.member(espaceId);
        LocalDate depuis = LocalDate.now(ZoneId.of(fuseau));
        LocalDate jusqua = switch (horizon.strip().toLowerCase(Locale.ROOT)) {
            case "30j" -> depuis.plusDays(30);
            case "6m" -> depuis.plusMonths(6);
            case "1a" -> depuis.plusYears(1);
            default -> throw new DonneeInvalide("DONNEE_INVALIDE", "Horizon attendu : 30j, 6m ou 1a.", "horizon");
        };
        List<ReferentielEspace.EntreeReferentiel> entrees = referentiel.lister(caller.accountId(), espaceId);
        java.util.Map<String, String> libelles = new java.util.HashMap<>();
        entrees.forEach(e -> libelles.put(e.cle(), e.libelle()));
        List<EcheanceRadar> echeances = libelles.isEmpty() ? List.of()
                : calendrier.echeances(libelles.keySet(), Set.of(espaceId), depuis, jusqua).stream()
                        .map(e -> new EcheanceRadar(e.cle(), libelles.get(e.cle()), e.version(), e.nature().name(), e.date(),
                                e.source(), e.note()))
                        .toList();
        List<String> produits = entrees.stream().map(ReferentielEspace.EntreeReferentiel::produitCycleVie)
                .filter(p -> p != null).distinct().toList();
        return new Radar(horizon.strip().toLowerCase(Locale.ROOT), depuis, jusqua, echeances, cycles.etats(produits));
    }

    @GET
    @Path("/dettes")
    @Operation(summary = "Impacts « dette de version » des projets de l'espace (membres)")
    @APIResponse(responseCode = "200", description = "Composants en fin de support, avec la version standard de l'espace")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    public List<RadarProjets.DetteVersion> dettes(@PathParam("espaceId") UUID espaceId) {
        Caller caller = access.member(espaceId);
        return radar.dettes(caller.accountId(), espaceId);
    }

    @GET
    @Path("/parametres")
    @Operation(summary = "Jours de rappel avant une échéance (membres) : J-30, J-7 et J0 par défaut")
    @APIResponse(responseCode = "200", description = "Jours de rappel de l'espace")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    public Parametres parametres(@PathParam("espaceId") UUID espaceId) {
        Caller caller = access.member(espaceId);
        return new Parametres(rappels.parametres(caller.accountId(), espaceId));
    }

    @PUT
    @Path("/parametres")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remplace les jours de rappel de l'espace (administrateur)")
    @APIResponse(responseCode = "200", description = "Jours de rappel enregistrés")
    @APIResponse(responseCode = "400", description = "De 1 à 10 jours distincts entre 0 et 365")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Membre non administrateur de l'espace")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou dont l'appelant n'est pas membre")
    public Parametres modifierParametres(@PathParam("espaceId") UUID espaceId, Parametres corps) {
        Caller caller = access.administrator(espaceId);
        return new Parametres(rappels.modifierParametres(caller.accountId(), espaceId,
                corps == null ? null : corps.rappelsJours()));
    }
}
