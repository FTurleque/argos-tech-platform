-- Dépôts d'un projet et occurrences des composants (changement OpenSpec automatiser-chaine-veille, volet B,
-- tâche 2.1). Exigences : INV-07 à INV-10. Conception : DC-2, DC-6, décision D6 (décision J6 du porteur du
-- projet, 2026-11-19 : un projet regroupe plusieurs dépôts, son inventaire est l'union des leurs).
--
-- Le composant reste unique par (projet, paquet, version) (index component_identity de V4) : l'union des
-- dépôts ne crée ni composant ni impact en double. component_occurrence dit dans quels dépôts il figure ;
-- une occurrence sans dépôt (repository_id nul) provient d'un import manuel d'un projet qui n'a aucun dépôt
-- principal ([PROPOSITION] P-B1 de la fiche inventaire-sbom). inventory reste la ligne de synthèse par projet
-- (compatibilité de l'API) ; project.scm_* reste en place et lu par projectintel (P-7).
-- Écritures sous l'utilisateur de connexion (imports, traitement technique) ; lecture sous argos_app
-- réservée aux membres de l'espace du projet (ADR-0016, argos_projet_lisible de V4).

-- Dépôt rattaché à un projet (1..n) ; au plus un dépôt principal par projet.
CREATE TABLE project_repository (
    id             uuid          PRIMARY KEY,
    project_id     uuid          NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    provider       varchar(16)   NOT NULL CHECK (provider IN ('GITLAB', 'GITHUB')),
    repository     varchar(300)  NOT NULL,
    branch         varchar(200),
    sbom_job       varchar(200),
    sbom_path      varchar(500),
    manifest_paths varchar(1000),
    principal      boolean       NOT NULL DEFAULT false,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT project_repository_uk UNIQUE (project_id, provider, repository)
);
CREATE UNIQUE INDEX project_repository_principal ON project_repository (project_id) WHERE principal;

-- Dernier import réussi de chaque dépôt : origine, date de la source, composants ignorés (INV-10-S1, S2).
CREATE TABLE repository_inventory (
    repository_id      uuid         PRIMARY KEY REFERENCES project_repository (id) ON DELETE CASCADE,
    project_id         uuid         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    origin             varchar(16)  NOT NULL CHECK (origin IN ('SBOM', 'MANIFEST')),
    source_date        timestamptz,
    spec_version       varchar(16),
    source_ref         varchar(500),
    import_id          uuid         NOT NULL,
    imported_at        timestamptz  NOT NULL,
    ignored_count      integer      NOT NULL DEFAULT 0 CHECK (ignored_count >= 0),
    ignored_components jsonb        NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(ignored_components) = 'array' AND jsonb_array_length(ignored_components) <= 100)
);
CREATE INDEX repository_inventory_project ON repository_inventory (project_id);

-- Présence d'un composant dans un dépôt (INV-10, invariant INV-B1 : un composant a au moins une occurrence).
CREATE TABLE component_occurrence (
    component_id  uuid        NOT NULL REFERENCES component (id) ON DELETE CASCADE,
    repository_id uuid        REFERENCES project_repository (id) ON DELETE CASCADE,
    project_id    uuid        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    transitive    boolean,
    import_id     uuid        NOT NULL
);
CREATE UNIQUE INDEX component_occurrence_uk ON component_occurrence
    (component_id, (coalesce(repository_id, '00000000-0000-0000-0000-000000000000'::uuid)));
CREATE INDEX component_occurrence_projet ON component_occurrence (project_id, repository_id);

-- Échec d'import rattaché au dépôt concerné (INV-07-S2) ; nul pour un import manuel ou un échec antérieur.
ALTER TABLE inventory_import_failure
    ADD COLUMN repository_id uuid REFERENCES project_repository (id) ON DELETE SET NULL;

-- Marqueur de recorrélation (INV-09-S4, P-10) : posé par l'import, effacé quand la recorrélation du même
-- import a réussi ; un marqueur resté posé est repris au passage planifié suivant.
ALTER TABLE inventory
    ADD COLUMN recorrelation_pending boolean NOT NULL DEFAULT false;

-- [reprise des données : début]
-- Reprise des projets existants (INV-10-S4) : chaque projet configuré reçoit son dépôt principal, l'inventaire
-- en place devient le dernier import de ce dépôt, chaque composant reçoit une occurrence (dans ce dépôt, ou
-- sans dépôt pour un projet qui n'en a pas). Composants, inventaires et impacts restent inchangés. Instructions
-- rejouables sans effet (NOT EXISTS) : le test de migration les rejoue sur un jeu antérieur à V19.
INSERT INTO project_repository (id, project_id, provider, repository, branch, sbom_job, sbom_path, manifest_paths,
                                principal, created_at)
SELECT gen_random_uuid(), p.id, p.scm_provider, p.scm_repository, p.scm_branch, p.sbom_job, p.sbom_path,
       p.manifest_paths, true, p.created_at
FROM project p
WHERE p.scm_provider IS NOT NULL AND p.scm_repository IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM project_repository r WHERE r.project_id = p.id);

INSERT INTO repository_inventory (repository_id, project_id, origin, source_date, spec_version, source_ref,
                                  import_id, imported_at, ignored_count, ignored_components)
SELECT r.id, i.project_id, i.origin, i.source_date, i.spec_version, i.source_ref, i.import_id, i.imported_at,
       i.ignored_count, i.ignored_components
FROM inventory i JOIN project_repository r ON r.project_id = i.project_id AND r.principal
WHERE NOT EXISTS (SELECT 1 FROM repository_inventory ri WHERE ri.repository_id = r.id);

INSERT INTO component_occurrence (component_id, repository_id, project_id, transitive, import_id)
SELECT c.id, r.id, c.project_id, c.transitive, c.import_id
FROM component c LEFT JOIN project_repository r ON r.project_id = c.project_id AND r.principal
WHERE NOT EXISTS (SELECT 1 FROM component_occurrence o WHERE o.component_id = c.id);
-- [reprise des données : fin]

GRANT SELECT ON project_repository, repository_inventory, component_occurrence TO argos_app;

ALTER TABLE project_repository ENABLE ROW LEVEL SECURITY;
CREATE POLICY project_repository_lecture ON project_repository FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
ALTER TABLE repository_inventory ENABLE ROW LEVEL SECURITY;
CREATE POLICY repository_inventory_lecture ON repository_inventory FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
ALTER TABLE component_occurrence ENABLE ROW LEVEL SECURITY;
CREATE POLICY component_occurrence_lecture ON component_occurrence FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
