-- Impacts projet (changement OpenSpec formaliser-chaine-securite-poc, tâche 4.1).
-- Exigences : COR-02, COR-03 (provenance), COR-07 (unicité), COR-08 (cloisonnement). Conception : DC-2, INV-D3, INV-D4.
-- Les refus d'accès sont journalisés dans la table refus_acces (V2), commune à toute l'API.

CREATE TABLE project_impact (
    id                uuid         PRIMARY KEY,
    signal_id         uuid         NOT NULL REFERENCES signal (id),
    signal_version_id uuid         NOT NULL REFERENCES signal_version (id),
    project_id        uuid         NOT NULL REFERENCES project (id),
    workspace_id      uuid         NOT NULL REFERENCES espace (id),
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

-- Garde-fou en base (ADR-0016, ACC-04-S2) : sous argos_app, un impact n'est visible que d'un membre de
-- son espace (argos_espace_lisible, V2). La corrélation écrit sous l'utilisateur de connexion.
-- Les signaux sont des données publiques, lisibles par tout compte.
GRANT SELECT ON project_impact, signal TO argos_app;
ALTER TABLE project_impact ENABLE ROW LEVEL SECURITY;
CREATE POLICY project_impact_lecture ON project_impact FOR SELECT TO argos_app
    USING (argos_espace_lisible(workspace_id));
