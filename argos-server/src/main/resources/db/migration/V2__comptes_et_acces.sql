-- ARGOS — comptes, espaces, rôles, invitations et journal des refus d'accès.
-- Changement OpenSpec « gerer-comptes-et-acces » (exigences ACC-01 à ACC-06, ADR-0016, ADR-0027).
--
-- Cloisonnement en deux temps (design.md D1) :
--   1. le service vérifie l'appartenance et le rôle (module workspace) ;
--   2. PostgreSQL n'expose, sous le rôle argos_app, que les lignes autorisées par le contexte
--      transmis à chaque transaction (paramètres argos.compte_courant, argos.sujet_courant,
--      argos.email_courant et argos.espace_courant) :
--        - espace, appartenance : les appartenances du compte courant, et les lignes d'un espace
--          courant dont il est membre ;
--        - invitation : les invitations d'un espace courant qu'il administre, et celles adressées
--          à son e-mail vérifié ;
--        - compte : le compte courant et les comptes qui partagent un espace avec lui ;
--        - données de veille (migrations V4 à V7) : les espaces dont le compte courant est membre,
--          restreints à l'espace courant s'il est transmis (argos_espace_lisible).
-- Sans contexte, une requête exécutée sous argos_app ne renvoie aucune ligne (ACC-04-S2).
-- Les traitements techniques (collecte, import d'inventaire, corrélation, journal des refus)
-- s'exécutent sous l'utilisateur de connexion, propriétaire des tables, pour qui la RLS n'est
-- pas forcée.

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
-- Table du module audit : journal unique des refus d'accès de toute l'API (ACC-06, COR-08-S2).
-- Compte (ou sujet OIDC), ressource demandée (méthode et chemin), motif et horodatage ;
-- aucune donnée de la ressource refusée.
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
CREATE INDEX refus_acces_compte_idx ON refus_acces (compte_id, horodatage);

-- ---------------------------------------------------------------------------
-- Contexte transmis par l'application à chaque transaction
-- ---------------------------------------------------------------------------
CREATE FUNCTION argos_espace_courant() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('argos.espace_courant', true), '')::uuid $$;

CREATE FUNCTION argos_compte_courant() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('argos.compte_courant', true), '')::uuid $$;

CREATE FUNCTION argos_sujet_courant() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('argos.sujet_courant', true), '') $$;

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

-- Données de veille d'un espace : compte courant membre de l'espace et, si un espace courant
-- est transmis, espace égal à l'espace courant (migrations V4 à V6).
CREATE FUNCTION argos_espace_lisible(p_espace uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT p_espace IS NOT NULL
       AND (argos_espace_courant() IS NULL OR p_espace = argos_espace_courant())
       AND argos_est_membre(p_espace)
$$;

-- Le compte courant peut-il entrer dans l'espace avec ce rôle : créateur d'un espace encore vide
-- (rôle administrateur), ou destinataire d'une invitation en attente, non expirée, émise pour ce rôle.
CREATE FUNCTION argos_peut_rejoindre(p_espace uuid, p_role text) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT (
        p_role = 'ADMINISTRATEUR'
        AND EXISTS (SELECT 1 FROM espace e
                    WHERE e.id = p_espace AND e.cree_par = argos_compte_courant())
        AND NOT EXISTS (SELECT 1 FROM appartenance a WHERE a.espace_id = p_espace)
    ) OR EXISTS (
        SELECT 1 FROM invitation i
        WHERE i.espace_id = p_espace
          AND i.role = p_role
          AND i.etat = 'EN_ATTENTE'
          AND i.expire_le > now()
          AND lower(i.email) = argos_email_courant()
    )
$$;

-- Un autre compte est-il visible du compte courant : membre d'un espace commun, ou auteur d'une
-- invitation adressée au compte courant ou émise dans un espace dont il est membre.
CREATE FUNCTION argos_compte_visible(p_compte uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM appartenance moi
        JOIN appartenance autre ON autre.espace_id = moi.espace_id
        WHERE moi.compte_id = argos_compte_courant() AND autre.compte_id = p_compte
    ) OR EXISTS (
        SELECT 1 FROM invitation i
        WHERE i.invite_par = p_compte
          AND (lower(i.email) = argos_email_courant() OR argos_est_membre(i.espace_id))
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
        CREATE ROLE argos_app NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;
GRANT argos_app TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO argos_app;
-- Moindre privilège (ADR-0016) : seules les colonnes modifiées par l'application sont modifiables.
-- Le journal des refus n'est écrit et lu que par le module audit, sous l'utilisateur de connexion :
-- argos_app n'y a aucun droit.
GRANT SELECT, INSERT ON compte TO argos_app;
GRANT SELECT, INSERT, DELETE ON espace TO argos_app;
GRANT UPDATE (nom) ON espace TO argos_app;
GRANT SELECT, INSERT, DELETE ON appartenance TO argos_app;
GRANT UPDATE (role) ON appartenance TO argos_app;
GRANT SELECT, INSERT ON invitation TO argos_app;
GRANT UPDATE (etat, destinataire_id) ON invitation TO argos_app;

REVOKE EXECUTE ON FUNCTION argos_espace_courant(), argos_compte_courant(), argos_sujet_courant(),
    argos_email_courant(), argos_role_dans(uuid), argos_est_membre(uuid), argos_espace_autorise(uuid),
    argos_espace_lisible(uuid), argos_peut_rejoindre(uuid, text), argos_compte_visible(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_espace_courant(), argos_compte_courant(), argos_sujet_courant(),
    argos_email_courant(), argos_role_dans(uuid), argos_est_membre(uuid), argos_espace_autorise(uuid),
    argos_espace_lisible(uuid), argos_peut_rejoindre(uuid, text), argos_compte_visible(uuid) TO argos_app;

-- ---------------------------------------------------------------------------
-- Politiques RLS
-- ---------------------------------------------------------------------------
ALTER TABLE compte ENABLE ROW LEVEL SECURITY;
CREATE POLICY compte_lecture ON compte FOR SELECT TO argos_app
    USING (id = argos_compte_courant() OR sujet_oidc = argos_sujet_courant() OR argos_compte_visible(id));
CREATE POLICY compte_creation ON compte FOR INSERT TO argos_app
    WITH CHECK (sujet_oidc = argos_sujet_courant());

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
    WITH CHECK (compte_id = argos_compte_courant() AND argos_peut_rejoindre(espace_id, role));
CREATE POLICY appartenance_modification ON appartenance FOR UPDATE TO argos_app
    USING (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR')
    WITH CHECK (argos_espace_autorise(espace_id));
CREATE POLICY appartenance_revocation ON appartenance FOR DELETE TO argos_app
    USING (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR');

ALTER TABLE invitation ENABLE ROW LEVEL SECURITY;
CREATE POLICY invitation_lecture ON invitation FOR SELECT TO argos_app
    USING (argos_espace_autorise(espace_id) OR lower(email) = argos_email_courant());
CREATE POLICY invitation_emission ON invitation FOR INSERT TO argos_app
    WITH CHECK (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR');
-- Un administrateur de l'espace révoque ; le destinataire ne peut que faire passer son invitation
-- en attente à l'état accepté, à son nom. Les autres colonnes ne sont pas modifiables (droits par colonne).
CREATE POLICY invitation_revocation ON invitation FOR UPDATE TO argos_app
    USING (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR')
    WITH CHECK (argos_espace_autorise(espace_id) AND argos_role_dans(espace_id) = 'ADMINISTRATEUR');
CREATE POLICY invitation_acceptation ON invitation FOR UPDATE TO argos_app
    USING (etat = 'EN_ATTENTE' AND lower(email) = argos_email_courant())
    WITH CHECK (etat = 'ACCEPTEE' AND destinataire_id = argos_compte_courant()
                AND lower(email) = argos_email_courant());
