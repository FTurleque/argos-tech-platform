package io.argos.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.scm.QuotaEpuise;
import io.argos.scm.RepositoryFiles;
import io.argos.scm.RepositoryRef;
import io.argos.scm.SbomDocument;
import io.argos.scm.ScmException;

/**
 * Constitution de l'inventaire d'un projet (DF-INV-1, DF-INV-2) : pour chaque dépôt, SBOM CycloneDX de la branche
 * principale en priorité (INV-01), repli sur les manifestes en l'absence de SBOM (INV-04), rejet motivé d'un
 * document non conforme (INV-05) ; l'inventaire du projet est l'union de ceux de ses dépôts (INV-10). L'import
 * manuel ({@link #importSbom}, {@link #importManifests}) et l'import depuis les dépôts ({@link #importerProjet},
 * appelé par l'import planifié {@link ImportPlanifie}) écrivent sous le même verrou par projet (INV-08).
 */
@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);

    /** Motif d'un dépôt dont le document lu sur la forge est celui de son dernier import (INV-14-S1). */
    public static final String MOTIF_DOCUMENT_INCHANGE = "document inchangé depuis le dernier import";

    /**
     * Issue d'un import : IMPORTED, REJECTED, KEPT (inventaire conservé, SBOM absente ou document inchangé), NONE
     * (aucune source), ERROR (forge en erreur) ou DEFERRED (quota de la forge épuisé pendant ce passage, dépôt non interrogé).
     * Pour KEPT, {@code components} et {@code ignored} valent 0 : ils ne décrivent pas l'inventaire conservé.
     * {@code ignored} compte les composants écartés faute de PURL exploitable ; {@code ignoredComponents}
     * les décrit, au plus {@value ParsedInventory#MAX_SIGNALED} (INV-01, décision D6 du 2026-11-18).
     */
    public record ImportResult(StatutImport status, String origin, int components, int ignored, String reason,
            List<ParsedInventory.IgnoredComponent> ignoredComponents) {

        public ImportResult(StatutImport status, String origin, int components, int ignored, String reason) {
            this(status, origin, components, ignored, reason, List.of());
        }
    }

    /** Issue de l'import d'un dépôt d'un projet. */
    public record ResultatDepot(UUID repositoryId, String provider, String repository, ImportResult result) {
    }

    /** Issue de l'import des dépôts d'un projet, le dépôt principal d'abord. */
    public record ImportProjet(UUID projectId, List<ResultatDepot> depots) {
    }

    /** Lecture d'un dépôt : son issue et, s'il est importé, l'inventaire à écrire. */
    private record InventaireLu(ImportResult result, Optional<InventoryStore.Remplacement> remplacement) {

        static InventaireLu sans(ImportResult result) {
            return new InventaireLu(result, Optional.empty());
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

    /**
     * Import manuel d'une SBOM (INV-01, INV-08-S1) : elle remplace l'inventaire du dépôt principal du projet, ou de
     * l'emplacement sans dépôt si le projet n'a pas de dépôt principal ([PROPOSITION] P-B1). Un document non
     * conforme est refusé sans modifier l'inventaire.
     */
    public ImportResult importSbom(UUID projectId, byte[] document, String sourceRef) {
        requireProject(projectId);
        UUID cible = depotPrincipal(projectId);
        ParsedInventory parsed;
        try {
            parsed = CycloneDxParser.parse(document, maxBytes);
        } catch (InvalidDocumentException erreur) {
            store.recordFailure(projectId, cible, erreur.getMessage(), sourceRef);
            LOG.infof(erreur, "SBOM refusée pour le projet %s : %s", projectId, erreur.getMessage());
            return new ImportResult(StatutImport.REJECTED, null, 0, 0, erreur.getMessage());
        }
        store.replace(projectId, new InventoryStore.Remplacement(cible, "SBOM", parsed, sourceRef,
                EmpreinteDocument.sbom(document), false));
        return new ImportResult(StatutImport.IMPORTED, "SBOM", parsed.components().size(), parsed.ignoredCount(), null,
                parsed.ignoredComponents());
    }

    /** Import manuel d'un inventaire de repli à partir de manifestes (chemin → contenu), même cible que la SBOM. */
    public ImportResult importManifests(UUID projectId, Map<String, byte[]> manifests) {
        requireProject(projectId);
        UUID cible = depotPrincipal(projectId);
        Optional<ParsedInventory> parsed = parseManifests(projectId, cible, manifests);
        if (parsed.isEmpty()) {
            return new ImportResult(StatutImport.NONE, null, 0, 0, "aucun manifeste lisible");
        }
        store.replace(projectId, new InventoryStore.Remplacement(cible, "MANIFEST", parsed.get(),
                String.join(",", manifests.keySet()), EmpreinteDocument.manifestes(manifests), false));
        return new ImportResult(StatutImport.IMPORTED, "MANIFEST", parsed.get().components().size(), 0, null);
    }

    /** Importe chaque dépôt du projet (INV-07, INV-10) ; voir {@link #importerProjet(UUID, Map)}. */
    public ImportProjet importerProjet(UUID projectId) {
        return importerProjet(projectId, new HashMap<>());
    }

    /**
     * Importe chaque dépôt du projet, en lecture seule sur sa forge, puis écrit en une transaction les dépôts
     * importés (INV-07, INV-10) : un dépôt refusé, en erreur ou sans nouvelle SBOM garde son dernier import
     * (INV-10-S2) et son échec est enregistré sans jeton (INV-07-S2). Sans dépôt importé, rien n'est écrit et
     * aucune recorrélation n'est lancée (INV-09-S3).
     *
     * @param quotas quotas épuisés pendant le passage, par forge : un dépôt d'une forge épuisée n'est pas
     *        interrogé (DEFERRED) ; un quota rencontré y est ajouté
     */
    public ImportProjet importerProjet(UUID projectId, Map<String, QuotaEpuise> quotas) {
        requireProject(projectId);
        List<ResultatDepot> resultats = new ArrayList<>();
        List<InventoryStore.Remplacement> remplacements = new ArrayList<>();
        for (ProjectDirectory.Depot depot : projects.depots(projectId)) {
            RepositoryRef ref = depot.ref();
            String forge = ref.provider() == null ? "" : ref.provider().toUpperCase(Locale.ROOT);
            QuotaEpuise quota = quotas.get(forge);
            InventaireLu lecture;
            if (quota != null) {
                lecture = InventaireLu.sans(new ImportResult(StatutImport.DEFERRED, null, 0, 0, quota.getMessage()));
            } else {
                lecture = lire(projectId, depot, rencontre -> quotas.put(forge, rencontre));
            }
            lecture.remplacement().ifPresent(remplacements::add);
            resultats.add(new ResultatDepot(depot.id(), ref.provider(), ref.repository(), lecture.result()));
        }
        Set<UUID> conserves = store.remplacer(projectId, remplacements);
        return new ImportProjet(projectId, resultats.stream().map(resultat -> conserves.contains(resultat.repositoryId())
                ? new ResultatDepot(resultat.repositoryId(), resultat.provider(), resultat.repository(),
                        new ImportResult(StatutImport.KEPT, resultat.result().origin(), 0, 0, MOTIF_DOCUMENT_INCHANGE))
                : resultat).toList());
    }

    /**
     * Détache un dépôt du projet et reconstitue l'inventaire (INV-10-S3) ; aucune API ne l'expose ([PROPOSITION]
     * P-7).
     */
    public boolean detacherDepot(UUID projectId, UUID repositoryId) {
        requireProject(projectId);
        return store.detacherDepot(projectId, repositoryId);
    }

    /** Lit un dépôt : SBOM, sinon inventaire SBOM conservé, sinon manifestes. */
    private InventaireLu lire(UUID projectId, ProjectDirectory.Depot depot, java.util.function.Consumer<QuotaEpuise> surQuota) {
        RepositoryRef repo = depot.ref();
        String sourceRef = repo.provider() + ":" + repo.repository() + "@" + repo.branch();
        try {
            // Une seule lecture par dépôt : le dépôt GitHub n'est vérifié qu'une fois.
            RepositoryFiles.Lecture lecture = repositoryFiles.lecture(repo);
            Optional<SbomDocument> sbom = lecture.latestSbom();
            if (sbom.isPresent()) {
                return lireSbom(projectId, depot.id(), sourceRef, sbom.get());
            }
            if (queries.originDepot(depot.id()).filter("SBOM"::equals).isPresent()) {
                return InventaireLu.sans(new ImportResult(StatutImport.KEPT, "SBOM", 0, 0, "aucune nouvelle SBOM disponible"));
            }
            Map<String, byte[]> manifests = new LinkedHashMap<>();
            for (String path : depot.manifestPaths()) {
                if (ManifestParser.supports(path)) {
                    lecture.file(path).ifPresent(content -> manifests.put(path, content));
                }
            }
            if (manifests.isEmpty()) {
                return InventaireLu.sans(new ImportResult(StatutImport.NONE, null, 0, 0, "ni SBOM ni manifeste lisible"));
            }
            Optional<ParsedInventory> parsed = parseManifests(projectId, depot.id(), manifests);
            if (parsed.isEmpty()) {
                return InventaireLu.sans(new ImportResult(StatutImport.NONE, null, 0, 0, "aucun manifeste lisible"));
            }
            return new InventaireLu(new ImportResult(StatutImport.IMPORTED, "MANIFEST", parsed.get().components().size(), 0, null),
                    Optional.of(new InventoryStore.Remplacement(depot.id(), "MANIFEST", parsed.get(),
                            String.join(",", manifests.keySet()), EmpreinteDocument.manifestes(manifests), true)));
        } catch (QuotaEpuise epuisement) {
            surQuota.accept(epuisement);
            store.recordFailure(projectId, depot.id(), epuisement.getMessage(), sourceRef);
            LOG.infof(epuisement, "import de l'inventaire du dépôt %s reporté : %s", sourceRef, epuisement.getMessage());
            return InventaireLu.sans(new ImportResult(StatutImport.ERROR, null, 0, 0, epuisement.getMessage()));
        } catch (ScmException erreur) {
            store.recordFailure(projectId, depot.id(), erreur.getMessage(), sourceRef);
            LOG.warnf(erreur, "import de l'inventaire du dépôt %s impossible : %s", sourceRef, erreur.getMessage());
            return InventaireLu.sans(new ImportResult(StatutImport.ERROR, null, 0, 0, erreur.getMessage()));
        }
    }

    /** SBOM lue dans un dépôt : refusée si elle n'est pas en CycloneDX JSON (INV-01, INV-05), sinon analysée. */
    private InventaireLu lireSbom(UUID projectId, UUID repositoryId, String sourceRef, SbomDocument sbom) {
        SbomDocument.Format format = sbom.format();
        String reason;
        if (format != SbomDocument.Format.CYCLONEDX_JSON && format != SbomDocument.Format.INCONNU) {
            reason = "SBOM " + format.libelle() + " non acceptée : CycloneDX JSON 1.5 ou 1.6 attendu";
        } else {
            try {
                ParsedInventory parsed = CycloneDxParser.parse(sbom.contenu(), maxBytes);
                return new InventaireLu(new ImportResult(StatutImport.IMPORTED, "SBOM", parsed.components().size(),
                        parsed.ignoredCount(), null, parsed.ignoredComponents()),
                        Optional.of(new InventoryStore.Remplacement(repositoryId, "SBOM", parsed, sourceRef,
                                EmpreinteDocument.sbom(sbom.contenu()), true)));
            } catch (InvalidDocumentException erreur) {
                reason = erreur.getMessage();
            }
        }
        store.recordFailure(projectId, repositoryId, reason, sourceRef);
        LOG.infof("SBOM refusée pour le projet %s : %s", projectId, reason);
        return InventaireLu.sans(new ImportResult(StatutImport.REJECTED, null, 0, 0, reason));
    }

    /** Inventaire de repli des manifestes lisibles ; vide si aucun ne l'est (chaque refus est enregistré). */
    private Optional<ParsedInventory> parseManifests(UUID projectId, UUID repositoryId, Map<String, byte[]> manifests) {
        Map<String, ParsedInventory.Item> items = new LinkedHashMap<>();
        int read = 0;
        for (Map.Entry<String, byte[]> manifeste : manifests.entrySet()) {
            try {
                for (ParsedInventory.Item item : ManifestParser.parse(manifeste.getKey(), manifeste.getValue())) {
                    items.putIfAbsent(item.packageKey() + "@" + (item.version() == null ? "" : item.version()), item);
                }
                read++;
            } catch (InvalidDocumentException erreur) {
                store.recordFailure(projectId, repositoryId, manifeste.getKey() + " : " + erreur.getMessage(), manifeste.getKey());
            }
        }
        return read == 0 ? Optional.empty() : Optional.of(new ParsedInventory(null, null, List.copyOf(items.values())));
    }

    /** Dépôt principal du projet, cible de l'import manuel ; {@code null} si le projet n'en a pas. */
    private UUID depotPrincipal(UUID projectId) {
        return projects.depots(projectId).stream().filter(ProjectDirectory.Depot::principal)
                .map(ProjectDirectory.Depot::id).findFirst().orElse(null);
    }

    private ProjectDirectory.Project requireProject(UUID projectId) {
        return projects.find(projectId).orElseThrow(() -> new IllegalArgumentException("projet inconnu : " + projectId));
    }
}
