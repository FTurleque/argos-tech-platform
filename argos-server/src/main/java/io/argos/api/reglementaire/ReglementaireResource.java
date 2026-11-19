package io.argos.api.reglementaire;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import io.argos.api.Erreur;
import io.argos.api.ParametresPagination;
import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.api.security.LimiteDebitAtteinte;
import io.argos.api.security.LimiteurDebit;
import io.argos.catalog.dictionnaire.DictionnaireMetier;
import io.argos.regintel.CollecteReglementaire;
import io.argos.regintel.CycleVie;
import io.argos.regintel.EcheancesReglementaires;
import io.argos.regintel.ExpertiseFiches;
import io.argos.regintel.FicheAnalyse;
import io.argos.regintel.FichesEnBase;
import io.argos.workspace.Introuvable;
import io.quarkus.security.Authenticated;

/**
 * Veille réglementaire (changement {@code suivre-veille-reglementaire}, REG-01 à REG-08) : domaines,
 * dictionnaire et experts de chaque espace ; fiches d'analyse, leur correction et leur validation par
 * l'expert métier ; cycle de vie, demande d'évolution associée, calendrier des entrées en vigueur et
 * jours de rappel ; état et rejeu de la collecte pour l'administrateur plateforme.
 *
 * <p>Les droits sont ceux des espaces (ADR-0016) : tout membre lit les domaines et les fiches validées ;
 * un administrateur de l'espace configure domaines, règles, experts et rappels ; seul un expert d'un des
 * domaines d'une fiche la voit avant validation et peut la corriger, la valider ou la faire avancer. La
 * base applique les mêmes règles sous le rôle {@code argos_app}. Les profils n'accordent aucun droit
 * (PRF-05).
 */
@Path("/api/v1/reglementaire")
@Tag(name = "Veille réglementaire")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ReglementaireResource {

    @Schema(name = "DomaineMetier")
    public record DomaineVue(String code, String libelle, List<DictionnaireMetier.Regle> regles,
            List<DictionnaireMetier.Expert> experts) {
    }

    @Schema(name = "SaisieDomaineMetier")
    public record SaisieDomaine(String libelle) {
    }

    @Schema(name = "ReglesDictionnaire")
    public record Regles(List<DictionnaireMetier.Regle> regles) {
    }

    @Schema(name = "ExpertsDomaine")
    public record Experts(List<UUID> comptes) {
    }

    @Schema(name = "RappelsReglementaires")
    public record Rappels(List<Integer> rappelsJours) {
    }

    @Schema(name = "CorrectionFiche")
    public record CorrectionFiche(String resume, LocalDate dateEffet, Boolean dateEffetAConfirmer, List<String> domaines) {
    }

    @Schema(name = "RejetFiche")
    public record Rejet(String motif) {
    }

    @Schema(name = "TransitionFiche")
    public record Transition(String etat) {
    }

    @Schema(name = "DemandeEvolution")
    public record DemandeEvolution(String adresse) {
    }

    @Schema(name = "CollecteReglementaireDemandee")
    public record CollecteDemandee(LocalDate jour) {
    }

    @Inject
    Access access;

    @Inject
    DictionnaireMetier dictionnaire;

    @Inject
    FichesEnBase fiches;

    @Inject
    ExpertiseFiches expertise;

    @Inject
    EcheancesReglementaires echeances;

    @Inject
    CollecteReglementaire collecte;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @ConfigProperty(name = "argos.reglementaire.debit.requetes-par-minute", defaultValue = "10")
    int requetesParMinute;

    private LimiteurDebit limiteur;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(requetesParMinute);
    }

    // ------------------------------------------------------------------ domaines, dictionnaire, experts

    @GET
    @Path("/espaces/{espaceId}/domaines")
    @Operation(summary = "Domaines métier de l'espace, avec leurs règles de rattachement et leurs experts")
    @APIResponse(responseCode = "404", description = "Espace inconnu ou hors périmètre",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public List<DomaineVue> domaines(@PathParam("espaceId") UUID espaceId) {
        Caller caller = access.member(espaceId);
        return dictionnaire.domaines(caller.accountId(), espaceId).stream().map(ReglementaireResource::vue).toList();
    }

    @PUT
    @Path("/espaces/{espaceId}/domaines/{code}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Crée un domaine métier ou change son libellé (administrateur de l'espace)")
    @APIResponse(responseCode = "400", description = "Code ou libellé invalide",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "403", description = "Réservé à un administrateur de l'espace",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public DomaineVue enregistrerDomaine(@PathParam("espaceId") UUID espaceId, @PathParam("code") String code,
            SaisieDomaine saisie) {
        Caller caller = access.administrator(espaceId);
        return vue(dictionnaire.enregistrerDomaine(caller.accountId(), espaceId, code, saisie == null ? null : saisie.libelle()));
    }

    @DELETE
    @Path("/espaces/{espaceId}/domaines/{code}")
    @Operation(summary = "Supprime un domaine métier, ses règles, ses experts et ses rattachements (administrateur de l'espace)")
    @APIResponse(responseCode = "204", description = "Domaine supprimé")
    public RestResponse<Void> supprimerDomaine(@PathParam("espaceId") UUID espaceId, @PathParam("code") String code) {
        Caller caller = access.administrator(espaceId);
        dictionnaire.supprimerDomaine(caller.accountId(), espaceId, code);
        return RestResponse.noContent();
    }

    @PUT
    @Path("/espaces/{espaceId}/domaines/{code}/regles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remplace les règles de rattachement déterministes du domaine (administrateur de l'espace)")
    @APIResponse(responseCode = "400", description = "Règle invalide",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public DomaineVue remplacerRegles(@PathParam("espaceId") UUID espaceId, @PathParam("code") String code, Regles corps) {
        Caller caller = access.administrator(espaceId);
        return vue(dictionnaire.remplacerRegles(caller.accountId(), espaceId, code, corps == null ? null : corps.regles()));
    }

    @PUT
    @Path("/espaces/{espaceId}/domaines/{code}/experts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Désigne les experts métier du domaine parmi les membres contributeurs ou administrateurs")
    @APIResponse(responseCode = "400", description = "Compte non membre contributeur ou administrateur de l'espace",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public DomaineVue remplacerExperts(@PathParam("espaceId") UUID espaceId, @PathParam("code") String code, Experts corps) {
        Caller caller = access.administrator(espaceId);
        return vue(dictionnaire.remplacerExperts(caller.accountId(), espaceId, code, corps == null ? null : corps.comptes()));
    }

    @GET
    @Path("/espaces/{espaceId}/rappels")
    @Operation(summary = "Jours de rappel avant une entrée en vigueur (J-90, J-30 et J-7 par défaut)")
    public Rappels rappels(@PathParam("espaceId") UUID espaceId) {
        Caller caller = access.member(espaceId);
        return new Rappels(echeances.parametres(caller.accountId(), espaceId));
    }

    @PUT
    @Path("/espaces/{espaceId}/rappels")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Règle les jours de rappel de l'espace (administrateur de l'espace)")
    @APIResponse(responseCode = "400", description = "De 1 à 10 jours entre 0 et 365",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public Rappels modifierRappels(@PathParam("espaceId") UUID espaceId, Rappels corps) {
        Caller caller = access.administrator(espaceId);
        return new Rappels(echeances.modifierParametres(caller.accountId(), espaceId, corps == null ? null : corps.rappelsJours()));
    }

    // ------------------------------------------------------------------ fiches

    @GET
    @Path("/fiches")
    @Operation(summary = "Fiches d'analyse visibles : validées pour tout membre, en attente pour les experts de leurs domaines")
    @APIResponse(responseCode = "400", description = "Critère invalide",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public List<FicheAnalyse> listerFiches(@QueryParam("espace") UUID espace, @QueryParam("domaine") String domaine,
            @QueryParam("etat") String etat,
            @Parameter(description = "Nombre maximal de fiches, de 1 à " + FichesEnBase.LIMITE_MAX
                    + " ; une valeur hors bornes ou non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "50")) @QueryParam("limite") String limite) {
        Caller caller = access.authenticated();
        if (espace != null && !caller.memberOf(espace)) {
            throw access.outOfScope();
        }
        String codeDomaine = domaine == null || domaine.isBlank() ? null : DictionnaireMetier.code(domaine);
        String etatRetenu = etat == null || etat.isBlank() ? null : etatValidation(etat);
        int nombre = ParametresPagination.entier(limite, "limite", 50);
        if (nombre < 1 || nombre > FichesEnBase.LIMITE_MAX) {
            throw Erreur.demandeRefusee("DONNEE_INVALIDE", "La limite est comprise entre 1 et " + FichesEnBase.LIMITE_MAX + ".");
        }
        return fiches.lister(caller.accountId(), new FichesEnBase.Criteres(espace, codeDomaine, etatRetenu, null, null, nombre));
    }

    @GET
    @Path("/fiches/{id}")
    @Operation(summary = "Fiche d'analyse et son historique")
    @APIResponse(responseCode = "404", description = "Fiche inconnue, hors périmètre ou non validée pour un non-expert",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public FicheAnalyse fiche(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        return fiches.detail(caller.accountId(), id).orElseThrow(() -> fiches.espaceDe(id).isPresent()
                ? access.outOfScope() : new Introuvable("Fiche introuvable."));
    }

    @PUT
    @Path("/fiches/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Corrige une fiche en attente : résumé, date d'effet, domaines (expert du domaine) ; chaque correction est historisée")
    @APIResponse(responseCode = "403", description = "Réservé à un expert d'un des domaines de la fiche",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "409", description = "Fiche déjà validée ou rejetée",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public FicheAnalyse corriger(@PathParam("id") UUID id, CorrectionFiche corps) {
        Caller caller = access.authenticated();
        return expertise.corriger(caller.accountId(), id, corps == null ? null
                : new ExpertiseFiches.Correction(corps.resume(), corps.dateEffet(), corps.dateEffetAConfirmer(), corps.domaines()),
                Instant.now());
    }

    @POST
    @Path("/fiches/{id}/validation")
    @Operation(summary = "Valide une fiche : elle devient visible des membres de l'espace et le texte passe en analyse")
    @APIResponse(responseCode = "403", description = "Réservé à un expert d'un des domaines de la fiche",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "409", description = "Fiche déjà décidée, ou résumé à rédiger",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public FicheAnalyse valider(@PathParam("id") UUID id) {
        Caller caller = access.authenticated();
        return expertise.valider(caller.accountId(), id, Instant.now());
    }

    @POST
    @Path("/fiches/{id}/rejet")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rejette une fiche en attente : elle n'est jamais diffusée")
    public FicheAnalyse rejeter(@PathParam("id") UUID id, Rejet corps) {
        Caller caller = access.authenticated();
        return expertise.rejeter(caller.accountId(), id, corps == null ? null : corps.motif(), Instant.now());
    }

    @POST
    @Path("/fiches/{id}/transitions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Fait avancer le texte dans son cycle de vie : SANS_IMPACT, IMPACT_PRODUIT, SPECIFIE, LIVRE")
    @APIResponse(responseCode = "409", description = "Transition hors de l'ordre du cycle de vie",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public FicheAnalyse transition(@PathParam("id") UUID id, Transition corps) {
        Caller caller = access.authenticated();
        CycleVie.Etat vers;
        try {
            vers = CycleVie.Etat.valueOf(corps == null || corps.etat() == null ? "" : corps.etat().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw Erreur.demandeRefusee("ETAT_INCONNU",
                    "État inconnu : NOUVEAU, EN_ANALYSE, SANS_IMPACT, IMPACT_PRODUIT, SPECIFIE ou LIVRE.");
        }
        return expertise.transition(caller.accountId(), id, vers, Instant.now());
    }

    @PUT
    @Path("/fiches/{id}/demande-evolution")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Associe un ticket ou une demande de fusion GitHub ou GitLab à un texte « impact produit »")
    @APIResponse(responseCode = "400", description = "Adresse non reconnue ou demande inexistante",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public FicheAnalyse associerDemande(@PathParam("id") UUID id, DemandeEvolution corps) {
        Caller caller = access.authenticated();
        if (!limiteur.admettre(caller.accountId())) {
            throw new LimiteDebitAtteinte();
        }
        return expertise.associerDemande(caller.accountId(), id, corps == null ? null : corps.adresse(), Instant.now());
    }

    @GET
    @Path("/calendrier")
    @Operation(summary = "Calendrier des entrées en vigueur des fiches validées (douze mois à venir par défaut)")
    public List<FicheAnalyse> calendrier(@QueryParam("espace") UUID espace, @QueryParam("depuis") String depuis,
            @QueryParam("jusqua") String jusqua) {
        Caller caller = access.authenticated();
        if (espace != null && !caller.memberOf(espace)) {
            throw access.outOfScope();
        }
        LocalDate debut = date(depuis, LocalDate.now(ZoneId.of(fuseau)), "depuis");
        LocalDate fin = date(jusqua, debut.plusDays(365), "jusqua");
        if (fin.isBefore(debut) || fin.isAfter(debut.plusYears(5))) {
            throw Erreur.demandeRefusee("DONNEE_INVALIDE", "La période va de « depuis » à « jusqua », cinq ans au plus.");
        }
        return fiches.calendrier(caller.accountId(), espace, debut, fin);
    }

    // ------------------------------------------------------------------ collecte (administrateur plateforme)

    @GET
    @Path("/collecte")
    @Operation(summary = "État de la source JORF et des jours collectés (administrateur plateforme)")
    @APIResponse(responseCode = "403", description = "Réservé à l'administrateur plateforme",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public CollecteReglementaire.EtatSource etatCollecte() {
        access.operator();
        return collecte.etat();
    }

    @POST
    @Path("/collectes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lance la collecte du jour et des jours manqués, ou rejoue un jour (administrateur plateforme)")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public CollecteReglementaire.Rapport collecter(CollecteDemandee corps) {
        Caller caller = access.operator();
        if (!limiteur.admettre(caller.accountId())) {
            throw new LimiteDebitAtteinte();
        }
        LocalDate aujourdhui = LocalDate.now(ZoneId.of(fuseau));
        if (corps != null && corps.jour() != null) {
            if (corps.jour().isAfter(aujourdhui) || corps.jour().isBefore(aujourdhui.minusYears(1))) {
                throw Erreur.demandeRefusee("DONNEE_INVALIDE", "Le jour rejoué est compris dans l'année écoulée.");
            }
            return collecte.rejouer(corps.jour(), Instant.now());
        }
        return collecte.collecter(aujourdhui, Instant.now());
    }

    // ------------------------------------------------------------------ outils

    private static DomaineVue vue(DictionnaireMetier.Domaine domaine) {
        return new DomaineVue(domaine.code(), domaine.libelle(), domaine.regles(), domaine.experts());
    }

    private static String etatValidation(String etat) {
        String normalise = etat.strip().toUpperCase(Locale.ROOT);
        if (!List.of("EN_ATTENTE_VALIDATION", "VALIDEE", "REJETEE").contains(normalise)) {
            throw Erreur.demandeRefusee("ETAT_INCONNU", "État de validation inconnu : EN_ATTENTE_VALIDATION, VALIDEE ou REJETEE.");
        }
        return normalise;
    }

    private static LocalDate date(String valeur, LocalDate defaut, String nom) {
        if (valeur == null || valeur.isBlank()) {
            return defaut;
        }
        try {
            return LocalDate.parse(valeur.strip());
        } catch (DateTimeParseException e) {
            throw Erreur.demandeRefusee("DONNEE_INVALIDE", "Date « " + nom + " » attendue au format AAAA-MM-JJ.");
        }
    }
}
