package io.argos.api;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.commun.DonneeInvalide;
import io.argos.commun.Page;
import io.argos.commun.Pagination;
import io.argos.commun.RegleViolee;
import io.argos.commun.securite.Caller;
import io.argos.commun.securite.LimiteDebitAtteinte;
import io.argos.commun.securite.LimiteurDebit;
import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryService;

/**
 * Inventaire d'un projet : composants, origine et âge (INV-02, INV-03), synthèse sans composants (INV-06, décision
 * G5 du 2026-11-19), composants inventoriés paginés (INV-06, décision F5 du 2026-11-19), imports refusés (INV-05) ;
 * dépôts du projet dans la synthèse et dans chaque composant (INV-10, décision J6 du 2026-11-19). Import manuel
 * d'une SBOM, de manifestes ou relance depuis les dépôts, réservés à un contributeur ou à un administrateur de
 * l'espace (INV-13) ; ces imports empruntent les traitements de l'import planifié. Aucune route ne
 * reçoit de notification de forge ni n'accepte une demande non authentifiée (INV-07-S3).
 */
@Path("/api/v1/projects/{projectId}/inventory")
@Tag(name = "Inventaires")
@Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {

    /**
     * Inventaire d'un projet. {@code status} vaut {@code AVAILABLE} ou {@code NO_SBOM} (aucun inventaire,
     * INV-03-S2) ; {@code inventory} est absent dans le second cas.
     */
    public record ProjectInventory(UUID projectId, String status, InventoryQueries.InventoryView inventory) {
    }

    /**
     * Synthèse de l'inventaire d'un projet, sans la liste des composants (décision G5 du porteur du projet,
     * 2026-11-19). {@code status} vaut {@code AVAILABLE} ou {@code NO_SBOM} ; {@code summary} est absent dans le
     * second cas.
     */
    public record ProjectInventorySummary(UUID projectId, String status, InventoryQueries.InventorySummary summary) {
    }

    /** Nombre maximal de manifestes importés en une demande. */
    static final int MANIFESTES_PAR_IMPORT_MAX = 20;

    /** Longueur maximale d'un chemin de manifeste. */
    static final int CHEMIN_MAX = 200;

    /** Longueur maximale de la liste des chemins, enregistrée comme référence de la source de l'import. */
    static final int CHEMINS_CUMULES_MAX = 500;

    /** Référence de source d'un import manuel, enregistrée avec l'inventaire et ses échecs. */
    static final String SOURCE_MANUELLE = "import manuel";

    /** Manifeste envoyé : chemin (nom de fichier reconnu, par exemple {@code package.json}) et contenu texte. */
    @Schema(name = "ManifesteImporte")
    public record Manifeste(@Schema(required = true) String chemin, @Schema(required = true) String contenu) {
    }

    /** Manifestes d'un import manuel. */
    @Schema(name = "ManifestesImportes")
    public record Manifestes(@Schema(required = true) List<Manifeste> manifestes) {
    }

    @Inject
    Access access;

    @Inject
    InventoryService imports;

    @ConfigProperty(name = "argos.forges.debit.requetes-par-minute", defaultValue = "6")
    int lecturesForgeParMinute;

    /** Limite des relances d'import, qui consomment le quota des forges, par compte. */
    private LimiteurDebit limiteurRelance;

    @PostConstruct
    void initialiser() {
        limiteurRelance = new LimiteurDebit(lecturesForgeParMinute);
    }

    @Inject
    InventoryQueries inventories;

    @GET
    @Operation(summary = "Inventaire de référence du projet, son origine, l'âge de la SBOM et une page de ses composants,"
            + " triés par clé de paquet, version puis identifiant")
    @APIResponse(responseCode = "200", description = "Inventaire ; inventory.components est une page de composants")
    @APIResponse(responseCode = "400", description = "Paramètre page ou size non entier")
    public ProjectInventory inventory(@PathParam("projectId") UUID projectId,
            @Parameter(description = ParametresPagination.DESCRIPTION_PAGE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam(ParametresPagination.PAGE) String page,
            @Parameter(description = ParametresPagination.DESCRIPTION_TAILLE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + Pagination.TAILLE_DEFAUT))
            @QueryParam(ParametresPagination.TAILLE) String size) {
        Pagination pagination = ParametresPagination.pagination(page, size);
        Caller caller = authorize(projectId);
        return inventories.inventory(projectId, caller.accountId(), pagination)
                .map(inventaire -> new ProjectInventory(projectId, "AVAILABLE", inventaire))
                .orElseGet(() -> new ProjectInventory(projectId, "NO_SBOM", null));
    }

    @GET
    @Path("/summary")
    @Operation(summary = "Synthèse de l'inventaire de référence du projet, sans la liste des composants")
    @APIResponse(responseCode = "200", description = "Origine, dates, âge, obsolescence, nombre de composants inventoriés,"
            + " composants ignorés faute de PURL et dépôts du projet avec leur dernier import et leur dernier échec"
            + " (repositories, INV-10) ; statut NO_SBOM sans synthèse si le projet n'a pas d'inventaire")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public ProjectInventorySummary summary(@PathParam("projectId") UUID projectId) {
        Caller caller = authorize(projectId);
        return inventories.summary(projectId, caller.accountId())
                .map(synthese -> new ProjectInventorySummary(projectId, "AVAILABLE", synthese))
                .orElseGet(() -> new ProjectInventorySummary(projectId, "NO_SBOM", null));
    }

    @GET
    @Path("/components")
    @Operation(summary = "Composants inventoriés du projet, triés par nom, version puis identifiant (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page de composants : nom, version, PURL, direct ou transitif, dépôts qui"
            + " le contiennent (repositories, INV-10) ; page vide si le projet n'a pas d'inventaire")
    @APIResponse(responseCode = "400", description = "Paramètre page ou size non entier")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public InventoryQueries.ComponentPage components(@PathParam("projectId") UUID projectId,
            @Parameter(description = "Numéro de page, à partir de 0 ; une valeur négative vaut 0 ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0")) @QueryParam("page") String page,
            @Parameter(description = "Taille de page, de 1 à " + InventoryQueries.MAX_PAGE_SIZE
                    + " ; une valeur hors bornes est ramenée à la borne la plus proche ; une valeur non entière donne 400",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + InventoryQueries.DEFAULT_PAGE_SIZE))
            @QueryParam("size") String size) {
        Caller caller = authorize(projectId);
        return inventories.components(projectId, caller.accountId(), ParametresPagination.entier(page, "page", 0),
                ParametresPagination.entier(size, "size", InventoryQueries.DEFAULT_PAGE_SIZE));
    }

    @GET
    @Path("/failures")
    @Operation(summary = "Imports de SBOM ou de manifestes refusés, avec leur motif, du plus récent au plus ancien (liste paginée)")
    @APIResponse(responseCode = "200", description = "Page d'imports refusés ou en erreur")
    @APIResponse(responseCode = "400", description = "Paramètre page ou size non entier")
    public Page<InventoryQueries.ImportFailure> failures(@PathParam("projectId") UUID projectId,
            @Parameter(description = ParametresPagination.DESCRIPTION_PAGE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam(ParametresPagination.PAGE) String page,
            @Parameter(description = ParametresPagination.DESCRIPTION_TAILLE,
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + Pagination.TAILLE_DEFAUT))
            @QueryParam(ParametresPagination.TAILLE) String size) {
        Pagination pagination = ParametresPagination.pagination(page, size);
        Caller caller = authorize(projectId);
        return inventories.failures(projectId, caller.accountId(), pagination);
    }

    @POST
    @Path("/sbom")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Importer une SBOM CycloneDX JSON qui remplace l'inventaire du dépôt principal (INV-13)")
    @APIResponse(responseCode = "200", description = "Issue de l'import : IMPORTED, ou REJECTED avec son motif (inventaire conservé)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Lecteur de l'espace du projet (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public InventoryService.ImportResult importerSbom(@PathParam("projectId") UUID projectId, byte[] document) {
        autoriserImport(projectId);
        return imports.importSbom(projectId, document == null ? new byte[0] : document, SOURCE_MANUELLE);
    }

    @POST
    @Path("/manifests")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Importer un inventaire de repli à partir de manifestes de dépendances (INV-13, INV-04)")
    @APIResponse(responseCode = "200", description = "Issue de l'import : IMPORTED (origine MANIFEST) ou NONE sans manifeste lisible")
    @APIResponse(responseCode = "400", description = "Aucun manifeste, plus de " + MANIFESTES_PAR_IMPORT_MAX
            + ", chemin vide, trop long ou contenant une virgule (DONNEE_INVALIDE)")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Lecteur de l'espace du projet (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    public InventoryService.ImportResult importerManifestes(@PathParam("projectId") UUID projectId, Manifestes demande) {
        autoriserImport(projectId);
        return imports.importManifests(projectId, contenus(demande));
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Relancer l'import du projet depuis ses dépôts, en lecture seule sur les forges (INV-13, INV-07)")
    @APIResponse(responseCode = "200", description = "Issue de l'import de chaque dépôt, le principal d'abord")
    @APIResponse(responseCode = "401", description = "Non authentifié")
    @APIResponse(responseCode = "403", description = "Lecteur de l'espace du projet (refus journalisé)")
    @APIResponse(responseCode = "404", description = "Projet inconnu ou d'un espace dont l'appelant n'est pas membre (refus journalisé)")
    @APIResponse(responseCode = "409", description = "Projet sans dépôt (PROJET_SANS_DEPOT)")
    @APIResponse(responseCode = "429", description = "Limite de débit du compte atteinte")
    public InventoryService.ImportProjet relancer(@PathParam("projectId") UUID projectId) {
        Caller caller = autoriserImport(projectId);
        if (!limiteurRelance.admettre(caller.accountId())) {
            throw new LimiteDebitAtteinte();
        }
        InventoryService.ImportProjet resultat = imports.importerProjet(projectId);
        if (resultat.depots().isEmpty()) {
            throw new RegleViolee("PROJET_SANS_DEPOT", "Le projet n'a aucun dépôt à importer.");
        }
        return resultat;
    }

    private Caller authorize(UUID projectId) {
        return access.memberOfProject(() -> inventories.workspaceOf(projectId));
    }

    /** Contributeur ou administrateur de l'espace du projet ; 404 sans refus journalisé pour un projet inconnu. */
    private Caller autoriserImport(UUID projectId) {
        access.authenticated();
        return access.contributor(inventories.workspaceOf(projectId).orElseThrow(NotFoundException::new));
    }

    /** Manifestes de la demande, par chemin, contenus encodés en UTF-8. */
    private static Map<String, byte[]> contenus(Manifestes demande) {
        if (demande == null || demande.manifestes() == null || demande.manifestes().isEmpty()) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Au moins un manifeste est attendu.", "manifestes");
        }
        if (demande.manifestes().size() > MANIFESTES_PAR_IMPORT_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Au plus " + MANIFESTES_PAR_IMPORT_MAX + " manifestes par import.", "manifestes");
        }
        Map<String, byte[]> contenus = new LinkedHashMap<>();
        for (Manifeste manifeste : demande.manifestes()) {
            String contenu = manifeste == null || manifeste.contenu() == null ? "" : manifeste.contenu();
            contenus.put(chemin(manifeste), contenu.getBytes(StandardCharsets.UTF_8));
        }
        if (String.join(",", contenus.keySet()).length() > CHEMINS_CUMULES_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Chemins cumulés trop longs (" + CHEMINS_CUMULES_MAX + " caractères au plus).",
                    "chemin");
        }
        return contenus;
    }

    /** Chemin d'un manifeste, non vide, d'au plus {@value #CHEMIN_MAX} caractères, sans virgule. */
    private static String chemin(Manifeste manifeste) {
        String chemin = manifeste == null || manifeste.chemin() == null ? "" : manifeste.chemin().strip();
        if (chemin.isEmpty() || chemin.length() > CHEMIN_MAX || chemin.contains(",")) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque manifeste porte un chemin non vide, d'au plus " + CHEMIN_MAX
                    + " caractères, sans virgule.", "chemin");
        }
        return chemin;
    }
}
