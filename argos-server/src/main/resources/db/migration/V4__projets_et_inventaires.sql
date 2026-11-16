-- Projets suivis et inventaires (changement OpenSpec formaliser-chaine-securite-poc, tâche 3.1).
-- Exigences : INV-01 à INV-05. Conception : DC-2, décision D2 (inventaire de référence remplacé en bloc).
-- Chaque projet appartient à un espace de la table espace (gerer-comptes-et-acces, ADR-0016).

-- Projet suivi, rattaché à un seul espace (module inventory, arc42 §5).
CREATE TABLE project (
    id                uuid         PRIMARY KEY,
    workspace_id      uuid         NOT NULL REFERENCES espace (id),
    name              varchar(200) NOT NULL,
    scm_provider      varchar(16)  CHECK (scm_provider IN ('GITLAB', 'GITHUB')),
    scm_repository    varchar(300),
    scm_branch        varchar(200),
    sbom_job          varchar(200),
    sbom_path         varchar(500),
    manifest_paths    varchar(1000),
    created_at        timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX project_workspace ON project (workspace_id);

-- Inventaire de référence : au plus un par projet (INV-01, D2).
CREATE TABLE inventory (
    project_id   uuid        PRIMARY KEY REFERENCES project (id),
    origin       varchar(16) NOT NULL CHECK (origin IN ('SBOM', 'MANIFEST')),
    source_date  timestamptz,
    spec_version varchar(16),
    source_ref   varchar(500),
    import_id    uuid        NOT NULL,
    imported_at  timestamptz NOT NULL
);

-- Composants de l'inventaire, identité stable par (projet, paquet, version) entre deux imports.
CREATE TABLE component (
    id          uuid         PRIMARY KEY,
    project_id  uuid         NOT NULL REFERENCES inventory (project_id) ON DELETE CASCADE,
    package_key varchar(500) NOT NULL,
    purl        varchar(1000) NOT NULL,
    version     varchar(200),
    transitive  boolean,
    import_id   uuid         NOT NULL
);
CREATE UNIQUE INDEX component_identity ON component (project_id, package_key, (coalesce(version, '')));
CREATE INDEX component_package_key ON component (package_key);

-- Imports refusés, avec leur motif (INV-05).
CREATE TABLE inventory_import_failure (
    id          uuid          PRIMARY KEY,
    project_id  uuid          NOT NULL REFERENCES project (id),
    reason      varchar(1000) NOT NULL,
    source_ref  varchar(500),
    received_at timestamptz   NOT NULL
);

-- Garde-fou en base (ADR-0016) : sous argos_app, un projet et son inventaire ne sont lisibles que
-- par un membre de son espace. Les imports s'exécutent sous l'utilisateur de connexion.
CREATE FUNCTION argos_projet_lisible(p_projet uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT EXISTS (SELECT 1 FROM project p WHERE p.id = p_projet AND argos_espace_lisible(p.workspace_id)) $$;
REVOKE EXECUTE ON FUNCTION argos_projet_lisible(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_projet_lisible(uuid) TO argos_app;

GRANT SELECT ON project, inventory, component, inventory_import_failure TO argos_app;

ALTER TABLE project ENABLE ROW LEVEL SECURITY;
CREATE POLICY project_lecture ON project FOR SELECT TO argos_app
    USING (argos_espace_lisible(workspace_id));
ALTER TABLE inventory ENABLE ROW LEVEL SECURITY;
CREATE POLICY inventory_lecture ON inventory FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
ALTER TABLE component ENABLE ROW LEVEL SECURITY;
CREATE POLICY component_lecture ON component FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
ALTER TABLE inventory_import_failure ENABLE ROW LEVEL SECURITY;
CREATE POLICY inventory_import_failure_lecture ON inventory_import_failure FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
