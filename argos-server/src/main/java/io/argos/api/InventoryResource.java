package io.argos.api;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.security.Access;
import io.argos.commun.DonneeInvalide;
import io.argos.commun.RegleViolee;
import io.argos.commun.securite.Caller;
import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryService;

/**
 * Inventaire d'un projet : composants, origine et âge (INV-02, INV-03), synthèse sans composants (INV-06, décision
 * G5 du 2026-11-19), composants inventoriés paginés (INV-06, décision F5 du 2026-11-19), imports refusés (INV-05) ;
 * dépôts du projet dans la synthèse et dans chaque composant (INV-10, décision J6 du 2026-11-19). Import manuel
 * d'une SBOM, de manifestes ou relance depuis les dépôts par un contributeur ou un administrateur de l'espace
 * (INV-13 ; décision du porteur du projet, 2026-11-26), par les traitements de l'import planifié. Aucune route ne
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
    static final int MANIFESTES_MAX = 20;

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

    @Inject
    InventoryQueries inventories;

    @GET
    @Operation(summary = "Inventaire de référence du projet, son origine et l'âge de la SBOM")
    public ProjectInventory inventory(@PathParam("projectId") UUID projectId) {
        Caller caller = authorize(projectId);
        return inventories.inventory(projectId, caller.accountId())
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
    @Operation(summary = "Imports de SBOM ou de manifestes refusés, avec leur motif")
    public List<InventoryQueries.ImportFailure> failures(@PathParam("projectId") UUID projectId) {
        Caller caller = authorize(projectId);
        return inventories.failures(projectId, caller.accountId());
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
    @APIResponse(responseCode = "400", description = "Aucun manifeste, plus de " + MANIFESTES_MAX
            + " ou manifeste sans chemin (DONNEE_INVALIDE)")
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
    public InventoryService.ImportProjet relancer(@PathParam("projectId") UUID projectId) {
        autoriserImport(projectId);
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
    private void autoriserImport(UUID projectId) {
        access.authenticated();
        access.contributor(inventories.workspaceOf(projectId).orElseThrow(NotFoundException::new));
    }

    /** Manifestes de la demande, par chemin, contenus encodés en UTF-8. */
    private static Map<String, byte[]> contenus(Manifestes demande) {
        if (demande == null || demande.manifestes() == null || demande.manifestes().isEmpty()) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Au moins un manifeste est attendu.", "manifestes");
        }
        if (demande.manifestes().size() > MANIFESTES_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Au plus " + MANIFESTES_MAX + " manifestes par import.", "manifestes");
        }
        Map<String, byte[]> contenus = new LinkedHashMap<>();
        for (Manifeste manifeste : demande.manifestes()) {
            if (manifeste == null || manifeste.chemin() == null || manifeste.chemin().isBlank()) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque manifeste porte un chemin.", "chemin");
            }
            String contenu = manifeste.contenu() == null ? "" : manifeste.contenu();
            contenus.put(manifeste.chemin().strip(), contenu.getBytes(StandardCharsets.UTF_8));
        }
        return contenus;
    }
}
