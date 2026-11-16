-- Impacts projet et journal des refus d'accès
-- (changement OpenSpec formaliser-chaine-securite-poc, tâche 4.1).
-- Exigences : COR-02, COR-03 (provenance), COR-07 (unicité), COR-08 (cloisonnement). Conception : DC-2, INV-D3, INV-D4.

CREATE TABLE project_impact (
    id                uuid         PRIMARY KEY,
    signal_id         uuid         NOT NULL REFERENCES signal (id),
    signal_version_id uuid         NOT NULL REFERENCES signal_version (id),
    project_id        uuid         NOT NULL REFERENCES project (id),
    workspace_id      uuid         NOT NULL REFERENCES workspace (id),
    component_id      uuid         NOT NULL REFERENCES component (id) ON DELETE CASCADE,
    level             varchar(16)  NOT NULL CHECK (level IN ('EXACT', 'PROBABLE', 'A_VERIFIER')),
    method            varchar(40)  NOT NULL,
    transitive        boolean,
    inventory_origin  varchar(16)  NOT NULL CHECK (inventory_origin IN ('SBOM', 'MANIFEST')),
    inventory_date    timestamptz,
    provenance        jsonb        NOT NULL,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT project_impact_triplet_uk UNIQUE (signal_id, project_id, component_id)
);
CREATE INDEX project_impact_signal ON project_impact (signal_id);
CREATE INDEX project_impact_workspace ON project_impact (workspace_id);

-- Garde-fou en base (ADR-0016) : une ligne n'est visible que pour les espaces déclarés dans la
-- transaction (paramètre argos.workspaces, liste d'UUID séparés par des virgules), ou pour un
-- traitement technique explicitement déclaré (argos.technical = 'on').
-- FORCE s'applique aussi au propriétaire de la table ; un super-utilisateur PostgreSQL n'est
-- jamais soumis à RLS : le compte applicatif de production ne doit pas l'être.
ALTER TABLE project_impact ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_impact FORCE ROW LEVEL SECURITY;
CREATE POLICY project_impact_workspace_isolation ON project_impact
    USING (
        coalesce(current_setting('argos.technical', true), '') = 'on'
        OR workspace_id::text = ANY (string_to_array(coalesce(current_setting('argos.workspaces', true), ''), ','))
    )
    WITH CHECK (
        coalesce(current_setting('argos.technical', true), '') = 'on'
        OR workspace_id::text = ANY (string_to_array(coalesce(current_setting('argos.workspaces', true), ''), ','))
    );

-- Refus d'accès journalisés : utilisateur, ressource demandée, date (COR-08-S2, ADR-0016).
CREATE TABLE access_refusal (
    id            uuid         PRIMARY KEY,
    user_id       varchar(200) NOT NULL,
    resource_type varchar(40)  NOT NULL,
    resource_id   varchar(200) NOT NULL,
    reason        varchar(300),
    occurred_at   timestamptz  NOT NULL
);
CREATE INDEX access_refusal_user ON access_refusal (user_id, occurred_at);
