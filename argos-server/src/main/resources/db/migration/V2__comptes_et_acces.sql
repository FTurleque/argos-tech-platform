-- ARGOS — comptes, espaces, rôles, invitations et journal des refus d'accès.
-- Changement OpenSpec « gerer-comptes-et-acces » (exigences ACC-01 à ACC-06, ADR-0016, ADR-0027).
--
-- Cloisonnement en deux temps (design.md D1) :
--   1. le service vérifie l'appartenance et le rôle (module workspace) ;
--   2. PostgreSQL n'expose, sous le rôle argos_app, que les lignes autorisées par
--      l'espace courant et le compte courant transmis à chaque transaction
--      (paramètres argos.espace_courant, argos.compte_courant, argos.email_courant).
-- Sans contexte, une requête exécutée sous argos_app ne renvoie aucune ligne d'espace (ACC-04-S2).

-- ---------------------------------------------------------------------------
-- Tables du module workspace
-- ---------------------------------------------------------------------------
CREATE TABLE compte (
    id               uuid        PRIMARY KEY,
    sujet_oidc       text        NOT NULL UNIQUE,
    nom_affiche      text        NOT NULL,
    email            text,
    admin_plateforme boolean     NOT NULL DEFAULT false,
    cree_le          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE espace (
    id       uuid        PRIMARY KEY,
    nom      text        NOT NULL CHECK (length(btrim(nom)) BETWEEN 1 AND 120),
    cree_par uuid        NOT NULL REFERENCES compte (id),
    cree_le  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE appartenance (
    espace_id uuid        NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    compte_id uuid        NOT NULL REFERENCES compte (id),
    role      text        NOT NULL CHECK (role IN ('LECTEUR', 'CONTRIBUTEUR', 'ADMINISTRATEUR')),
    depuis    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (espace_id, compte_id)
);
CREATE INDEX appartenance_compte_idx ON appartenance (compte_id);

CREATE TABLE invitation (
    id              uuid        PRIMARY KEY,
    espace_id       uuid        NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    email           text        NOT NULL,
    role            text        NOT NULL CHECK (role IN ('LECTEUR', 'CONTRIBUTEUR', 'ADMINISTRATEUR')),
    etat            text        NOT NULL DEFAULT 'EN_ATTENTE' CHECK (etat IN ('EN_ATTENTE', 'ACCEPTEE', 'REVOQUEE')),
    invite_par      uuid        NOT NULL REFERENCES compte (id),
    destinataire_id uuid        REFERENCES compte (id),
    cree_le         timestamptz NOT NULL DEFAULT now(),
    expire_le       timestamptz NOT NULL
);
CREATE INDEX invitation_espace_idx ON invitation (espace_id);
CREATE INDEX invitation_email_idx ON invitation (lower(email));

-- ---------------------------------------------------------------------------
-- Table du module audit : aucune donnée de la ressource refusée (ACC-06).
-- ---------------------------------------------------------------------------
CREATE TABLE refus_acces (
    id          bigserial   PRIMARY KEY,
    compte_id   uuid,
    sujet_oidc  text,
    ressource   text        NOT NULL,
    motif       text        NOT NULL,
    horodatage  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refus_acces_horodatage_idx ON refus_acces (horodatage);

-- ---------------------------------------------------------------------------
-- Contexte transmis par l'application à chaque transaction
-- ---------------------------------------------------------------------------
CREATE FUNCTION argos_espace_courant() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('argos.espace_courant', true), '')::uuid $$;

CREATE FUNCTION argos_compte_courant() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('argos.compte_courant', true), '')::uuid $$;

CREATE FUNCTION argos_email_courant() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT lower(NULLIF(current_setting('argos.email_courant', true), '')) $$;

-- Fonctions SECURITY DEFINER : exécutées avec les droits du propriétaire des tables
-- (non soumis à la RLS, celle-ci n'étant pas forcée), elles évitent la récursion
-- des politiques qui consultent la table appartenance.
CREATE FUNCTION argos_role_dans(p_espace uuid) RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT role FROM appartenance
    WHERE espace_id = p_espace AND compte_id = argos_compte_courant()
$$;

CREATE FUNCTION argos_est_membre(p_espace uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT argos_role_dans(p_espace) IS NOT NULL $$;

-- Espace courant transmis ET compte courant membre de cet espace.
CREATE FUNCTION argos_espace_autorise(p_espace uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT p_espace IS NOT NULL
       AND p_espace = argos_espace_courant()
       AND argos_est_membre(p_espace)
$$;

-- Le compte courant peut-il entrer dans l'espace : créateur d'un espace encore vide,
-- ou destinataire d'une invitation en attente et non expirée.
CREATE FUNCTION argos_peut_rejoindre(p_espace uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT (
        EXISTS (SELECT 1 FROM espace e
                WHERE e.id = p_espace AND e.cree_par = argos_compte_courant())
        AND NOT EXISTS (SELECT 1 FROM appartenance a WHERE a.espace_id = p_espace)
    ) OR EXISTS (
        SELECT 1 FROM invitation i
        WHERE i.espace_id = p_espace
          AND i.etat = 'EN_ATTENTE'
          AND i.expire_le > now()
          AND lower(i.email) = argos_email_courant()
    )
$$;

-- ---------------------------------------------------------------------------
-- Rôle applicatif soumis à la RLS
-- ---------------------------------------------------------------------------
-- L'utilisateur de connexion (souvent superutilisateur ou propriétaire, donc exempté
-- de RLS) bascule sur argos_app par SET LOCAL ROLE dans chaque transaction applicative.
-- La création du rôle exige le droit CREATEROLE (ou superutilisateur) lors de la migration.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'argos_app') THEN
        CREATE ROLE argos_app NOLOGIN;
    END IF;
END
$$;
GRANT argos_app TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO argos_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON compte, espace, appartenance, invitation TO argos_app;
GRANT SELECT, INSERT ON refus_acces TO argos_app;
GRANT USAGE ON SEQUENCE refus_acces_id_seq TO argos_app;

-- ---------------------------------------------------------------------------
-- Politiques RLS
-- ---------------------------------------------------------------------------
ALTER TABLE espace ENABLE ROW LEVEL SECURITY;
CREATE POLICY espace_lecture ON espace FOR SELECT TO argos_app
    USING (argos_est_membre(id) OR cree_par = argos_compte_courant());
CREATE POLICY espace_creation ON espace FOR INSERT TO argos_app
    WITH CHECK (cree_par = argos_compte_courant());
CREATE POLICY espace_modification ON espace FOR UPDATE TO argos_app
    USING (argos_espace_autorise(id) AND argos_role_dans(id) = 'ADMINISTRATEUR')
    WITH CHECK (argos_espace_autorise(id));
CREATE POLICY espace_suppression ON espace FOR DELETE TO argos_app
    USING (argos_espace_autorise(id) AND argos_role_dans(id) = 'ADMINISTRATEUR');

ALTER TABLE appartenance ENABLE ROW LEVEL SECURITY;
CREATE POLICY appartenance_lecture ON appartenance FOR SELECT TO argos_app
    USING (compte_id = argos_compte_courant() OR argos_espace_autorise(espace_id));
CREATE POLICY appartenance_entree ON appartenance FOR INSERT TO argos_app
    WITH CHECK (compte_id = argos_compte_courant() AND argos_peut_rejoindre(espace_id));
CREATE POLICY appartenance_modification ON appartenance FOR UPDATE TO argos_app
    USING (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR')
    WITH CHECK (argos_espace_autorise(espace_id));
CREATE POLICY appartenance_revocation ON appartenance FOR DELETE TO argos_app
    USING (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR');

ALTER TABLE invitation ENABLE ROW LEVEL SECURITY;
CREATE POLICY invitation_lecture ON invitation FOR SELECT TO argos_app
    USING (argos_espace_autorise(espace_id)
           OR (etat = 'EN_ATTENTE' AND lower(email) = argos_email_courant()));
CREATE POLICY invitation_emission ON invitation FOR INSERT TO argos_app
    WITH CHECK (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR');
CREATE POLICY invitation_modification ON invitation FOR UPDATE TO argos_app
    USING ((argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR')
           OR (etat = 'EN_ATTENTE' AND lower(email) = argos_email_courant()))
    WITH CHECK (argos_espace_autorise(espace_id) OR lower(email) = argos_email_courant());
