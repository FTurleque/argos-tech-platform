-- Rôle de connexion d'Argos distinct du propriétaire de la base (EXP-05, ADR-0035).
--
-- Les migrations s'appliquent sous le propriétaire de la base ; l'application se connecte avec un rôle qui n'est ni
-- superutilisateur, ni propriétaire, ni BYPASSRLS. Ce rôle de connexion est membre, depuis l'initialisation de
-- l'instance (deploiement/postgres/initialiser-bases.sh) :
--   - d'argos_app, sans en hériter les droits : il y bascule par SET LOCAL ROLE dans chaque transaction d'un
--     utilisateur (ContexteBase, migration V2) ;
--   - d'argos_technique, dont il hérite les droits : traitements de la plateforme sans contexte utilisateur
--     (collectes, imports, corrélation, diffusion des alertes, journaux).
--
-- argos_technique lit et écrit toutes les tables sauf l'historique des migrations, sans aucun droit sur le schéma.
-- Sur une table sous RLS, sa politique ne s'applique que hors de tout contexte utilisateur : une session qui quitte
-- argos_app par RESET ROLE garde le contexte posé par ContexteBase et ne lit aucune ligne cloisonnée. Limite : ces
-- paramètres de transaction sont modifiables par toute session ; une instruction injectée qui les efface puis quitte
-- argos_app lit toutes les lignes. La RLS protège d'un défaut de filtrage dans le code, non d'une injection SQL.
-- Toute nouvelle table sous RLS reçoit la même politique dans sa migration (vérifié par RolesBaseTest).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'argos_technique') THEN
        CREATE ROLE argos_technique NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

-- Vrai lorsque la transaction ne porte aucun contexte utilisateur (compte, sujet, e-mail, espace) : ces paramètres
-- sont posés par ContexteBase, locaux à la transaction. Fonction SQL simple, intégrée aux requêtes par le planificateur.
CREATE FUNCTION argos_hors_contexte_utilisateur() RETURNS boolean
    LANGUAGE sql STABLE
AS $$
    SELECT coalesce(pg_catalog.current_setting('argos.compte_courant', true), '') = ''
       AND coalesce(pg_catalog.current_setting('argos.sujet_courant', true), '') = ''
       AND coalesce(pg_catalog.current_setting('argos.email_courant', true), '') = ''
       AND coalesce(pg_catalog.current_setting('argos.espace_courant', true), '') = ''
$$;

GRANT USAGE ON SCHEMA public TO argos_technique;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO argos_technique;
REVOKE ALL ON flyway_schema_history FROM argos_technique;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO argos_technique;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO argos_technique;

-- Objets créés par les migrations suivantes (sous le propriétaire de la base).
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO argos_technique;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO argos_technique;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO argos_technique;

DO $$
DECLARE
    table_cloisonnee record;
BEGIN
    FOR table_cloisonnee IN
        SELECT relname FROM pg_class
        WHERE relnamespace = 'public'::regnamespace AND relkind IN ('r', 'p') AND relrowsecurity
    LOOP
        EXECUTE format('CREATE POLICY %I ON %I FOR ALL TO argos_technique'
                       || ' USING (argos_hors_contexte_utilisateur()) WITH CHECK (argos_hors_contexte_utilisateur())',
                       table_cloisonnee.relname || '_technique', table_cloisonnee.relname);
    END LOOP;
END
$$;
