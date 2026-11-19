package io.argos.inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.scm.RepositoryFiles;
import io.argos.scm.RepositoryRef;
import io.argos.scm.SbomDocument;
import io.argos.scm.ScmException;

/**
 * Constitution de l'inventaire d'un projet (DF-INV-1, DF-INV-2) : SBOM CycloneDX de la branche
 * principale en priorité (INV-01), repli sur les manifestes en l'absence de SBOM (INV-04),
 * rejet motivé d'un document non conforme (INV-05).
 */
@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);

    /**
     * Issue d'un import : IMPORTED, REJECTED, KEPT (inventaire SBOM conservé) ou NONE (aucune source).
     * {@code ignored} compte les composants écartés faute de PURL exploitable ; {@code ignoredComponents}
     * les décrit, au plus {@value ParsedInventory#MAX_SIGNALED} (INV-01, décision D6 du 2026-11-18).
     */
    public record ImportResult(String status, String origin, int components, int ignored, String reason,
            List<ParsedInventory.IgnoredComponent> ignoredComponents) {

        public ImportResult(String status, String origin, int components, int ignored, String reason) {
            this(status, origin, components, ignored, reason, List.of());
        }
    }

    @Inject
    InventoryStore store;

    @Inject
    InventoryQueries queries;

    @Inject
    ProjectDirectory projects;

    @Inject
    RepositoryFiles repositoryFiles;

    @ConfigProperty(name = "argos.inventory.max-document-bytes")
    long maxBytes;

    /** Importe une SBOM ; un document non conforme est refusé sans modifier l'inventaire. */
    public ImportResult importSbom(UUID projectId, byte[] document, String sourceRef) {
        requireProject(projectId);
        ParsedInventory parsed;
        try {
            parsed = CycloneDxParser.parse(document, maxBytes);
        } catch (InvalidDocumentException e) {
            store.recordFailure(projectId, e.getMessage(), sourceRef);
            LOG.infof("SBOM refusée pour le projet %s : %s", projectId, e.getMessage());
            return new ImportResult("REJECTED", null, 0, 0, e.getMessage());
        }
        store.replace(projectId, "SBOM", parsed, sourceRef);
        return new ImportResult("IMPORTED", "SBOM", parsed.components().size(), parsed.ignoredCount(), null,
                parsed.ignoredComponents());
    }

    /** Constitue un inventaire de repli à partir de manifestes (chemin → contenu). */
    public ImportResult importManifests(UUID projectId, Map<String, byte[]> manifests) {
        requireProject(projectId);
        Map<String, ParsedInventory.Item> items = new LinkedHashMap<>();
        int read = 0;
        for (Map.Entry<String, byte[]> m : manifests.entrySet()) {
            try {
                for (ParsedInventory.Item item : ManifestParser.parse(m.getKey(), m.getValue())) {
                    items.putIfAbsent(item.packageKey() + "@" + (item.version() == null ? "" : item.version()), item);
                }
                read++;
            } catch (InvalidDocumentException e) {
                store.recordFailure(projectId, m.getKey() + " : " + e.getMessage(), m.getKey());
            }
        }
        if (read == 0) {
            return new ImportResult("NONE", null, 0, 0, "aucun manifeste lisible");
        }
        store.replace(projectId, "MANIFEST", new ParsedInventory(null, null, List.copyOf(items.values())),
                String.join(",", manifests.keySet()));
        return new ImportResult("IMPORTED", "MANIFEST", items.size(), 0, null);
    }

    /**
     * Rafraîchit l'inventaire depuis le dépôt : SBOM si disponible ; sinon, si l'inventaire actuel
     * ne provient pas d'une SBOM, repli sur les manifestes. Un inventaire SBOM existant est conservé
     * en l'absence de nouvelle SBOM ; son âge le signale (INV-03, COR-06).
     */
    public ImportResult refresh(UUID projectId) {
        ProjectDirectory.Project project = requireProject(projectId);
        RepositoryRef repo = project.repository()
                .orElseThrow(() -> new IllegalStateException("aucun dépôt rattaché au projet " + projectId));
        try {
            // Une seule lecture par rafraîchissement : le dépôt GitHub n'est vérifié qu'une fois.
            RepositoryFiles.Lecture depot = repositoryFiles.lecture(repo);
            Optional<SbomDocument> sbom = depot.latestSbom();
            if (sbom.isPresent()) {
                return importRepositorySbom(projectId, repo, sbom.get());
            }
            if (queries.origin(projectId).filter("SBOM"::equals).isPresent()) {
                return new ImportResult("KEPT", "SBOM", 0, 0, "aucune nouvelle SBOM disponible");
            }
            return importRepositoryManifests(projectId, project, depot);
        } catch (ScmException e) {
            LOG.warnf("rafraîchissement de l'inventaire du projet %s impossible : %s", projectId, e.getMessage());
            return new ImportResult("ERROR", null, 0, 0, e.getMessage());
        }
    }

    /** Importe la SBOM lue dans le dépôt, ou la refuse si elle n'est pas en CycloneDX JSON. */
    private ImportResult importRepositorySbom(UUID projectId, RepositoryRef repo, SbomDocument sbom) {
        String sourceRef = repo.provider() + ":" + repo.repository() + "@" + repo.branch();
        SbomDocument.Format format = sbom.format();
        if (format != SbomDocument.Format.CYCLONEDX_JSON && format != SbomDocument.Format.INCONNU) {
            return rejectFormat(projectId, format, sourceRef);
        }
        return importSbom(projectId, sbom.contenu(), sourceRef);
    }

    /** Repli sur les manifestes pris en charge que déclare le projet et que contient le dépôt. */
    private ImportResult importRepositoryManifests(UUID projectId, ProjectDirectory.Project project,
            RepositoryFiles.Lecture depot) {
        Map<String, byte[]> manifests = new LinkedHashMap<>();
        for (String path : project.manifestPaths()) {
            if (ManifestParser.supports(path)) {
                depot.file(path).ifPresent(content -> manifests.put(path, content));
            }
        }
        if (manifests.isEmpty()) {
            return new ImportResult("NONE", null, 0, 0, "ni SBOM ni manifeste lisible");
        }
        return importManifests(projectId, manifests);
    }

    /**
     * Refuse une SBOM reconnue dans un autre format que CycloneDX JSON (INV-01, INV-05) : le motif
     * nomme le format reçu, l'inventaire en place est conservé.
     */
    private ImportResult rejectFormat(UUID projectId, SbomDocument.Format format, String sourceRef) {
        String reason = "SBOM " + format.libelle() + " non acceptée : CycloneDX JSON 1.5 ou 1.6 attendu";
        store.recordFailure(projectId, reason, sourceRef);
        LOG.infof("SBOM refusée pour le projet %s : %s", projectId, reason);
        return new ImportResult("REJECTED", null, 0, 0, reason);
    }

    private ProjectDirectory.Project requireProject(UUID projectId) {
        return projects.find(projectId).orElseThrow(() -> new IllegalArgumentException("projet inconnu : " + projectId));
    }
}
