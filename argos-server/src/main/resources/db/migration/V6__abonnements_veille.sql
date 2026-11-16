-- Abonnements de veille (changement OpenSpec formaliser-chaine-securite-poc, tâche 5.1).
-- Exigences : ABO-01 (portée), ABO-02 (attributs obligatoires), ABO-03 (socle TEAM), ABO-06 (TEAM obligatoire
-- ou facultatif). Invariant INV-D6 : exactement une portée et un propriétaire du type correspondant.
-- Propriétaire TEAM : un espace (table espace) ; propriétaire PERSONAL : un compte (table compte).

CREATE TABLE subscription (
    id                 uuid         PRIMARY KEY,
    scope              varchar(16)  NOT NULL CHECK (scope IN ('TEAM', 'PERSONAL')),
    owner_workspace_id uuid         REFERENCES espace (id) ON DELETE CASCADE,
    owner_user_id      uuid         REFERENCES compte (id),
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

-- Retrait d'un compte d'un abonnement TEAM facultatif (décision du 2026-11-16, Q-5).
CREATE TABLE subscription_opt_out (
    subscription_id uuid         NOT NULL REFERENCES subscription (id) ON DELETE CASCADE,
    user_id         uuid         NOT NULL REFERENCES compte (id),
    opted_out_at    timestamptz  NOT NULL,
    PRIMARY KEY (subscription_id, user_id)
);

-- Garde-fou en base (ADR-0016) : sous argos_app, un abonnement PERSONAL n'est visible et modifiable
-- que par son propriétaire ; un abonnement TEAM n'est visible que des membres de l'espace et n'est
-- créé que par un de ses administrateurs. Un abonnement n'élargit aucun droit (ABO-05).
GRANT SELECT, INSERT ON subscription, subscription_opt_out TO argos_app;
GRANT UPDATE (state) ON subscription TO argos_app;

ALTER TABLE subscription ENABLE ROW LEVEL SECURITY;
CREATE POLICY subscription_lecture ON subscription FOR SELECT TO argos_app
    USING ((scope = 'PERSONAL' AND owner_user_id = argos_compte_courant())
           OR (scope = 'TEAM' AND argos_espace_lisible(owner_workspace_id)));
CREATE POLICY subscription_creation ON subscription FOR INSERT TO argos_app
    WITH CHECK ((scope = 'PERSONAL' AND owner_user_id = argos_compte_courant())
                OR (scope = 'TEAM' AND argos_espace_lisible(owner_workspace_id)
                    AND argos_role_dans(owner_workspace_id) = 'ADMINISTRATEUR'));
CREATE POLICY subscription_desactivation ON subscription FOR UPDATE TO argos_app
    USING (scope = 'PERSONAL' AND owner_user_id = argos_compte_courant())
    WITH CHECK (scope = 'PERSONAL' AND owner_user_id = argos_compte_courant());

ALTER TABLE subscription_opt_out ENABLE ROW LEVEL SECURITY;
CREATE POLICY subscription_opt_out_compte ON subscription_opt_out FOR ALL TO argos_app
    USING (user_id = argos_compte_courant())
    WITH CHECK (user_id = argos_compte_courant());
