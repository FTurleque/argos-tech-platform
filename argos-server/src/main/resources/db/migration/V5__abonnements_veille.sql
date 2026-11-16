-- Abonnements de veille (changement OpenSpec formaliser-chaine-securite-poc, tâche 5.1).
-- Exigences : ABO-01 (portée), ABO-02 (attributs obligatoires), ABO-03 (socle TEAM), ABO-06 (TEAM obligatoire
-- ou facultatif). Invariant INV-D6 : exactement une portée et un propriétaire du type correspondant.
-- Aucune table d'autorisation n'est référencée (ABO-05, INV-D7).

CREATE TABLE subscription (
    id                 uuid         PRIMARY KEY,
    scope              varchar(16)  NOT NULL CHECK (scope IN ('TEAM', 'PERSONAL')),
    owner_workspace_id uuid         REFERENCES workspace (id),
    owner_user_id      varchar(200),
    filters            jsonb        NOT NULL
                       CHECK (jsonb_typeof(filters) = 'array' AND jsonb_array_length(filters) > 0),
    channel            varchar(40)  NOT NULL CHECK (channel <> ''),
    frequency          varchar(40)  NOT NULL CHECK (frequency <> ''),
    mandatory          boolean      NOT NULL,
    state              varchar(16)  NOT NULL CHECK (state IN ('ACTIVE', 'INACTIVE')),
    created_at         timestamptz  NOT NULL,
    CONSTRAINT subscription_owner_ck CHECK (
        (scope = 'TEAM' AND owner_workspace_id IS NOT NULL AND owner_user_id IS NULL)
        OR (scope = 'PERSONAL' AND owner_user_id IS NOT NULL AND owner_workspace_id IS NULL))
);
CREATE INDEX subscription_owner_workspace ON subscription (owner_workspace_id) WHERE scope = 'TEAM';
CREATE INDEX subscription_owner_user ON subscription (owner_user_id) WHERE scope = 'PERSONAL';

-- Retrait d'un utilisateur d'un abonnement TEAM facultatif (décision du 2026-11-16, Q-5).
CREATE TABLE subscription_opt_out (
    subscription_id uuid         NOT NULL REFERENCES subscription (id) ON DELETE CASCADE,
    user_id         varchar(200) NOT NULL,
    opted_out_at    timestamptz  NOT NULL,
    PRIMARY KEY (subscription_id, user_id)
);
