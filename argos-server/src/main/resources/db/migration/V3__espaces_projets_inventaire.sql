-- Espaces, projets suivis et inventaires (changement OpenSpec formaliser-chaine-securite-poc, tâche 3.1).
-- Exigences : INV-01 à INV-05. Conception : DC-2, décision D2 (inventaire de référence remplacé en bloc).

-- Espace d'équipe (module workspace, ADR-0016). Comptes, rôles et appartenances relèvent
-- du changement gerer-comptes-et-acces.
CREATE TABLE workspace (
    id         uuid         PRIMARY KEY,
    name       varchar(200) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

-- Projet suivi, rattaché à un seul espace (module inventory, arc42 §5).
CREATE TABLE project (
    id                uuid         PRIMARY KEY,
    workspace_id      uuid         NOT NULL REFERENCES workspace (id),
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
