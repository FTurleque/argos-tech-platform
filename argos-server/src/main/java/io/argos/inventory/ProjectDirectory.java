package io.argos.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.commun.Jdbc;
import io.argos.scm.RepositoryRef;

/**
 * Projets suivis, chacun rattaché à un seul espace (table {@code espace}), et leurs dépôts (INV-10, décision J6 du
 * porteur du projet, 2026-11-19) : un projet regroupe zéro, un ou plusieurs dépôts ({@code project_repository}),
 * dont au plus un principal. Le dépôt donné à la création reste aussi décrit par les colonnes {@code scm_*} du
 * projet, lues par {@code projectintel} ([PROPOSITION] P-7 du changement automatiser-chaine-veille).
 */
@ApplicationScoped
public class ProjectDirectory {

    /** Manifestes lus par défaut pour l'inventaire de repli. */
    public static final List<String> DEFAULT_MANIFESTS = List.of("package.json", "requirements.txt");

    /** Projet suivi ; {@code repository} est absent si aucun dépôt n'est rattaché par les colonnes {@code scm_*}. */
    public record Project(UUID id, UUID workspaceId, String name, Optional<RepositoryRef> repository,
            List<String> manifestPaths) {
    }

    /**
     * Dépôt rattaché à un projet.
     *
     * @param ref fournisseur, identifiant, branche et emplacement de la SBOM du dépôt
     * @param manifestPaths manifestes lus pour l'inventaire de repli de ce dépôt
     * @param principal vrai pour le dépôt principal du projet (au plus un)
     */
    public record Depot(UUID id, UUID projectId, RepositoryRef ref, List<String> manifestPaths, boolean principal) {
    }

    /** Dépôt rattaché ; {@code principal} est vrai si le projet n'avait pas de dépôt principal. */
    public record Rattachement(UUID id, boolean principal) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    Jdbc jdbc;

    /**
     * Crée un projet ; un dépôt donné devient son dépôt principal ({@code project_repository}) et reste décrit par
     * les colonnes {@code scm_*} du projet.
     */
    @Transactional
    public UUID create(UUID workspaceId, String name, RepositoryRef repository, List<String> manifestPaths) {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO project(id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path,
                                    manifest_paths)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Optional<RepositoryRef> depot = Optional.ofNullable(repository);
        try {
            ecrire(sql, id, workspaceId, name, depot.map(RepositoryRef::provider).orElse(null),
                    depot.map(RepositoryRef::repository).orElse(null), depot.map(RepositoryRef::branch).orElse(null),
                    depot.map(RepositoryRef::sbomJob).orElse(null), depot.map(RepositoryRef::sbomPath).orElse(null),
                    manifestes(manifestPaths));
        } catch (SQLException erreur) {
            if ("23503".equals(erreur.getSQLState())) {
                throw new IllegalArgumentException("espace inconnu : " + workspaceId, erreur);
            }
            throw new IllegalStateException("création du projet impossible", erreur);
        }
        if (repository != null) {
            insererDepot(id, repository, manifestPaths, true);
        }
        return id;
    }

    /**
     * Rattache un dépôt à un projet (INV-10, INV-12) ; il devient principal si le projet n'en a pas.
     *
     * @throws DepotDejaRattache si le même dépôt (forge et identifiant) est déjà rattaché au projet
     */
    @Transactional
    public Rattachement ajouterDepot(UUID projectId, RepositoryRef repository, List<String> manifestPaths) {
        boolean principal = depots(projectId).stream().noneMatch(Depot::principal);
        return new Rattachement(insererDepot(projectId, repository, manifestPaths, principal), principal);
    }

    /**
     * Crée un projet demandé par l'administrateur de son espace (INV-12) ; le dépôt donné, facultatif, devient son
     * dépôt principal.
     */
    public UUID creer(UUID workspaceId, String name, Optional<DepotProjet> depot) {
        return create(workspaceId, name, depot.map(DepotProjet::reference).orElse(null),
                depot.map(DepotProjet::manifestesOuDefaut).orElse(null));
    }

    /**
     * Rattache au projet un dépôt demandé par l'administrateur de son espace (INV-12).
     *
     * @throws DepotDejaRattache si le même dépôt est déjà rattaché au projet
     */
    public Rattachement rattacher(UUID projectId, DepotProjet depot) {
        return ajouterDepot(projectId, depot.reference(), depot.manifestesOuDefaut());
    }

    /** Dépôts d'un projet, le principal d'abord, puis par date de rattachement (traitement technique). */
    @Transactional
    public List<Depot> depots(UUID projectId) {
        String sql = """
                SELECT id, project_id, provider, repository, branch, sbom_job, sbom_path, manifest_paths, principal
                FROM project_repository WHERE project_id = ? ORDER BY principal DESC, created_at, id
                """;
        return jdbc.lister(sql, Jdbc.parametres(projectId), ProjectDirectory::depot);
    }

    /** Projets ayant au moins un dépôt, par date de création puis identifiant (import planifié, INV-07). */
    @Transactional
    public List<UUID> avecDepots() {
        String sql = """
                SELECT p.id FROM project p WHERE EXISTS (SELECT 1 FROM project_repository r WHERE r.project_id = p.id)
                ORDER BY p.created_at, p.id
                """;
        return jdbc.lister(sql, Jdbc.sansParametre(), resultat -> resultat.getObject(1, UUID.class));
    }

    @Transactional
    public Optional<Project> find(UUID id) {
        String sql = """
                SELECT id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path, manifest_paths
                FROM project WHERE id = ?
                """;
        return jdbc.unique(sql, Jdbc.parametres(id), ProjectDirectory::project);
    }

    /** Projets rattachés à un dépôt, pour les traitements techniques (collecte de l'activité). */
    @Transactional
    public List<Project> withRepository() {
        String sql = """
                SELECT id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path, manifest_paths
                FROM project WHERE scm_provider IS NOT NULL AND scm_repository IS NOT NULL ORDER BY created_at, id
                """;
        return jdbc.lister(sql, Jdbc.sansParametre(), ProjectDirectory::project);
    }

    private UUID insererDepot(UUID projectId, RepositoryRef repository, List<String> manifestPaths, boolean principal) {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO project_repository(id, project_id, provider, repository, branch, sbom_job, sbom_path,
                                               manifest_paths, principal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try {
            ecrire(sql, id, projectId, repository.provider(), repository.repository(), repository.branch(), repository.sbomJob(),
                    repository.sbomPath(), manifestes(manifestPaths), principal);
        } catch (SQLException erreur) {
            if ("23503".equals(erreur.getSQLState())) {
                throw new IllegalArgumentException("projet inconnu : " + projectId, erreur);
            }
            if ("23505".equals(erreur.getSQLState())) {
                throw new DepotDejaRattache(repository, erreur);
            }
            throw new IllegalStateException("rattachement du dépôt impossible", erreur);
        }
        return id;
    }

    /** Exécute une écriture avec les valeurs données ; l'échec SQL est laissé à l'appelant, qui lit son code. */
    private void ecrire(String sql, Object... valeurs) throws SQLException {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            Jdbc.parametres(valeurs).lier(requete);
            requete.executeUpdate();
        }
    }

    /** Manifestes enregistrés, séparés par des virgules ; à défaut, {@link #DEFAULT_MANIFESTS}. */
    private static String manifestes(List<String> manifestPaths) {
        return String.join(",", manifestPaths == null ? DEFAULT_MANIFESTS : manifestPaths);
    }

    private static Depot depot(ResultSet resultat) throws SQLException {
        return new Depot(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                new RepositoryRef(resultat.getString(3), resultat.getString(4), resultat.getString(5), resultat.getString(6),
                        resultat.getString(7)),
                chemins(resultat.getString(8)), resultat.getBoolean(9));
    }

    private static Project project(ResultSet resultat) throws SQLException {
        Optional<RepositoryRef> depotScm = resultat.getString(4) == null ? Optional.empty()
                : Optional.of(new RepositoryRef(resultat.getString(4), resultat.getString(5), resultat.getString(6),
                        resultat.getString(7), resultat.getString(8)));
        return new Project(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class), resultat.getString(3), depotScm,
                chemins(resultat.getString(9)));
    }

    private static List<String> chemins(String manifests) {
        return manifests == null || manifests.isBlank() ? List.of()
                : Arrays.stream(manifests.split(",")).map(String::strip).filter(chemin -> !chemin.isEmpty()).toList();
    }
}
