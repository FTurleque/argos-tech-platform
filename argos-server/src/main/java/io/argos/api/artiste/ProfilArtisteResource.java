package io.argos.api.artiste;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import io.argos.alerting.RappelsOpportunites;
import io.argos.api.Erreur;
import io.argos.api.UtilisateurCourant;
import io.argos.api.preference.ResolutionPreferences;
import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.api.security.LimiteDebitAtteinte;
import io.argos.api.security.LimiteurDebit;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ListeObjets;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.catalog.profil.ValeurListe;
import io.argos.impact.RapprochementOpportunites;
import io.argos.impact.ResumeOpportunite;
import io.argos.signal.opportunite.CollecteOpportunites;
import io.argos.signal.opportunite.SourceProfil;
import io.argos.signal.opportunite.SourcesProfil;
import io.argos.workspace.Introuvable;
import io.argos.workspace.RegleViolee;
import io.argos.workspace.preference.PreferencesService;
import io.argos.workspace.suivi.SuivisProfil;
import io.quarkus.security.Authenticated;

/**
 * Profil de veille {@code artiste} (changement ajouter-profil-artiste) : listes du pack, suivis de
 * l'utilisateur (ART-02), opportunités restituées (ART-03) et leur résumé sans avis d'éligibilité
 * (ART-06) ; pour l'administrateur plateforme, registre des sources et de leurs conditions d'usage
 * (ART-05).
 *
 * <p>Les suivis et la restitution sont réservés à un utilisateur dont le profil {@code artiste} est
 * actif ; ils n'accordent aucun droit et ne modifient pas la veille des autres profils (PRF-04,
 * PRF-05). Les valeurs suivies sont confrontées aux listes du manifeste avant tout enregistrement.
 */
@Path("/api/v1/profils/artiste")
@Tag(name = "Profil artiste")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ProfilArtisteResource {

    static final String PROFIL = RapprochementOpportunites.PROFIL;
    static final String DISCIPLINE = "discipline";
    static final String TERRITOIRE = "territoire";

    @Schema(name = "ValeurListeArtiste")
    public record ValeurVue(String identifiant, String libelle) {
    }

    @Schema(name = "ListesArtiste")
    public record Listes(List<ValeurVue> disciplines, List<ValeurVue> territoires, List<Integer> rappelsJoursParDefaut) {
    }

    @Schema(name = "SuivisArtiste")
    public record Suivis(List<String> disciplines, List<String> territoires, List<Integer> rappelsJours,
            boolean rappelsParDefaut, boolean complets) {
    }

    @Schema(name = "ChoixSuivisArtiste")
    public record ChoixSuivis(List<String> disciplines, List<String> territoires, List<Integer> rappelsJours) {
    }

    @Schema(name = "OpportuniteArtiste")
    public record OpportuniteVue(UUID id, String rubrique, String titre, String organisme, LocalDate dateLimite,
            boolean dateAConfirmer, String lienOfficiel, List<String> disciplines, List<String> territoires,
            String explication, ResumeOpportunite.Resume resume) {
    }

    @Schema(name = "OpportunitesArtiste")
    public record OpportunitesVue(boolean suivisRequis, String message, List<OpportuniteVue> items) {
    }

    @Schema(name = "DeclarationSourceArtiste")
    public record DeclarationSource(String type, String adresse, String libelle, String rubrique,
            List<String> disciplines, List<String> territoires) {
    }

    @Schema(name = "ConditionsUsageSource")
    public record ConditionsUsage(String licence, String conditionsUsage, String conditionsUrl) {
    }

    @Inject
    UtilisateurCourant utilisateur;

    @Inject
    Access access;

    @Inject
    CatalogueProfils catalogue;

    @Inject
    PreferencesService preferences;

    @Inject
    ResolutionPreferences resolution;

    @Inject
    SuivisProfil suivis;

    @Inject
    RapprochementOpportunites rapprochement;

    @Inject
    RappelsOpportunites rappels;

    @Inject
    SourcesProfil sources;

    @Inject
    CollecteOpportunites collecte;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @ConfigProperty(name = "argos.profil-artiste.debit.requetes-par-minute", defaultValue = "10")
    int requetesParMinute;

    private LimiteurDebit limiteur;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(requetesParMinute);
    }

    // ------------------------------------------------------------------ listes et suivis

    @GET
    @Path("/listes")
    @Operation(summary = "Disciplines et territoires du profil artiste, et jours de rappel par défaut")
    public Listes listes() {
        ManifesteProfil m = manifeste();
        return new Listes(vues(m, DISCIPLINE), vues(m, TERRITOIRE), rappels.rappelsParDefaut());
    }

    @GET
    @Path("/suivis")
    @Operation(summary = "Disciplines, territoires et jours de rappel suivis par l'utilisateur courant")
    @APIResponse(responseCode = "409", description = "Profil artiste inactif pour l'utilisateur",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public Suivis lireSuivis() {
        UUID compte = compteActif();
        return vue(suivis.lire(compte, PROFIL));
    }

    @PUT
    @Path("/suivis")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Choisir ses disciplines, ses territoires et, au besoin, ses jours de rappel")
    @APIResponse(responseCode = "200", description = "Suivis enregistrés",
            content = @Content(schema = @Schema(implementation = Suivis.class)))
    @APIResponse(responseCode = "400", description = "Valeur absente des listes du profil, ou liste vide",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "409", description = "Profil artiste inactif pour l'utilisateur",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public Suivis choisirSuivis(ChoixSuivis choix) {
        UUID compte = compteActif();
        ManifesteProfil m = manifeste();
        List<String> disciplines = valider(m, DISCIPLINE, choix == null ? null : choix.disciplines(),
                "DISCIPLINE_INCONNUE", "une discipline", "La discipline", "Disciplines disponibles");
        List<String> territoires = valider(m, TERRITOIRE, choix.territoires(),
                "TERRITOIRE_INCONNU", "un territoire", "Le territoire", "Territoires disponibles");
        List<Integer> jours = choix.rappelsJours() == null ? null : RappelsOpportunites.valider(choix.rappelsJours());
        Map<String, List<String>> objets = new LinkedHashMap<>();
        objets.put(DISCIPLINE, disciplines);
        objets.put(TERRITOIRE, territoires);
        SuivisProfil.Suivis enregistres = suivis.remplacer(compte, PROFIL, objets);
        if (jours != null) {
            enregistres = suivis.remplacerRappels(compte, PROFIL, jours);
        }
        return vue(enregistres);
    }

    // ------------------------------------------------------------------ restitution

    @GET
    @Path("/opportunites")
    @Operation(summary = "Opportunités ouvertes correspondant à ses disciplines et territoires suivis")
    @APIResponse(responseCode = "409", description = "Profil artiste inactif pour l'utilisateur",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public OpportunitesVue opportunites() {
        UUID compte = compteActif();
        SuivisProfil.Suivis s = suivis.lire(compte, PROFIL);
        if (s.valeurs(DISCIPLINE).isEmpty() || s.valeurs(TERRITOIRE).isEmpty()) {
            return new OpportunitesVue(true,
                    "Choisissez au moins une discipline et un territoire pour recevoir les opportunités du profil artiste.",
                    List.of());
        }
        List<OpportuniteVue> items = rapprochement.restituer(Set.copyOf(s.valeurs(DISCIPLINE)),
                Set.copyOf(s.valeurs(TERRITOIRE)), LocalDate.now(ZoneId.of(fuseau))).stream()
                .map(r -> new OpportuniteVue(r.id(), r.rubrique(), r.titre(), r.organisme(), r.dateLimite(),
                        r.dateLimite() == null, r.lien(), r.disciplines(), r.territoires(), r.decision().explication(),
                        r.resume()))
                .toList();
        return new OpportunitesVue(false, null, items);
    }

    @GET
    @Path("/opportunites/{id}/resume")
    @Operation(summary = "Résumé d'une opportunité : critères tels qu'énoncés et lien vers le texte officiel")
    @APIResponse(responseCode = "404", description = "Opportunité inconnue",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public ResumeOpportunite.Resume resume(@PathParam("id") UUID id) {
        compteActif();
        return rapprochement.resume(id).orElseThrow(() -> new Introuvable("Opportunité introuvable."));
    }

    // ------------------------------------------------------------------ sources (administrateur plateforme)

    @GET
    @Path("/sources")
    @Operation(summary = "Sources du profil et état de leurs conditions d'usage (administrateur plateforme)")
    public List<SourceProfil> listerSources() {
        access.operator();
        return sources.lister(PROFIL);
    }

    @POST
    @Path("/sources")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Déclare une source du profil ; elle n'est collectée qu'après l'enregistrement de ses conditions d'usage")
    @APIResponse(responseCode = "201", description = "Source déclarée, état CONDITIONS_A_VERIFIER")
    @APIResponse(responseCode = "400", description = "Adresse, type, rubrique ou valeur invalide",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "403", description = "Réservé à l'administrateur plateforme",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "409", description = "Adresse déjà déclarée",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public RestResponse<SourceProfil> declarerSource(DeclarationSource d) {
        Caller caller = access.operator();
        SourceProfil s = sources.declarer(d == null ? null : new SourcesProfil.Declaration(PROFIL, d.type(), d.adresse(),
                d.libelle(), d.rubrique(), d.disciplines(), d.territoires()), caller.accountId());
        return RestResponse.status(RestResponse.Status.CREATED, s);
    }

    @PUT
    @Path("/sources/{id}/conditions-usage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Enregistre les conditions d'usage vérifiées d'une source (administrateur plateforme)")
    @APIResponse(responseCode = "400", description = "Licence ou conditions absentes",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    @APIResponse(responseCode = "404", description = "Source inconnue",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public SourceProfil enregistrerConditions(@PathParam("id") UUID id, ConditionsUsage c) {
        Caller caller = access.operator();
        exigerSourceDuProfil(id);
        return sources.enregistrerConditions(id, c == null ? null
                : new SourcesProfil.Conditions(c.licence(), c.conditionsUsage(), c.conditionsUrl()),
                caller.accountId(), Instant.now()).orElseThrow(() -> new Introuvable("Source introuvable."));
    }

    @POST
    @Path("/collectes")
    @Operation(summary = "Collecte les sources du profil ; les sources non vérifiées ne sont pas collectées (administrateur plateforme)")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte",
            content = @Content(schema = @Schema(implementation = Erreur.class)))
    public List<CollecteOpportunites.Rapport> collecter() {
        Caller caller = access.operator();
        if (!limiteur.admettre(caller.accountId())) {
            throw new LimiteDebitAtteinte();
        }
        return collecte.collecter(PROFIL, Instant.now());
    }

    // ------------------------------------------------------------------ outils

    private void exigerSourceDuProfil(UUID id) {
        if (sources.trouver(id).filter(s -> PROFIL.equals(s.profil())).isEmpty()) {
            throw new Introuvable("Source introuvable.");
        }
    }

    private ManifesteProfil manifeste() {
        return catalogue.trouver(PROFIL).orElseThrow(() -> new Introuvable("Le profil artiste n'est pas publié."));
    }

    /** Compte de l'appelant, dont le profil artiste doit être actif (préférences effectives). */
    private UUID compteActif() {
        manifeste();
        UUID compte = utilisateur.compte().id();
        if (!resolution.resoudre(preferences.lire(compte)).profilsActifs().contains(PROFIL)) {
            throw new RegleViolee("PROFIL_INACTIF", "Activez le profil artiste pour choisir vos suivis et voir ses opportunités.");
        }
        return compte;
    }

    private List<Integer> joursAppliques(SuivisProfil.Suivis s) {
        return s.rappelsJours().orElse(rappels.rappelsParDefaut());
    }

    private Suivis vue(SuivisProfil.Suivis s) {
        return new Suivis(s.valeurs(DISCIPLINE), s.valeurs(TERRITOIRE), joursAppliques(s), s.rappelsJours().isEmpty(),
                !s.valeurs(DISCIPLINE).isEmpty() && !s.valeurs(TERRITOIRE).isEmpty());
    }

    private static List<ValeurVue> vues(ManifesteProfil m, String type) {
        return m.liste(type).map(ListeObjets::valeurs).orElse(List.of()).stream()
                .map(v -> new ValeurVue(v.identifiant(), v.libelle())).toList();
    }

    /** Au moins une valeur, toutes dans la liste du pack ; sinon refus avec la liste disponible (ART-02-S2). */
    private static List<String> valider(ManifesteProfil m, String type, List<String> demandees, String code,
            String auMoins, String designation, String intitule) {
        List<ValeurListe> admises = m.liste(type).map(ListeObjets::valeurs).orElse(List.of());
        String disponibles = intitule + " : " + admises.stream().map(v -> v.libelle() + " (" + v.identifiant() + ")")
                .collect(Collectors.joining(", ")) + ".";
        if (demandees == null || demandees.isEmpty()) {
            throw Erreur.demandeRefusee(code, "Choisissez au moins " + auMoins + ". " + disponibles);
        }
        if (demandees.size() > 50) {
            throw Erreur.demandeRefusee(code, "50 valeurs au plus.");
        }
        Set<String> retenues = new LinkedHashSet<>();
        for (String d : demandees) {
            String v = d == null ? "" : d.strip();
            if (admises.stream().noneMatch(a -> a.identifiant().equals(v))) {
                String affichee = v.length() > 64 ? v.substring(0, 64) + "…" : v;
                throw Erreur.demandeRefusee(code, designation + " « " + affichee
                        + " » n'est pas dans la liste du profil artiste. " + disponibles);
            }
            retenues.add(v);
        }
        return List.copyOf(retenues);
    }
}
