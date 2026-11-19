-- État de traitement des impacts et historique de ses transitions (changement OpenSpec
-- automatiser-chaine-veille, volet C, tâche 3.1 ; décision J5 du porteur du projet, 2026-11-19).
-- Exigences : COR-09 (niveau et état distincts), COR-10 (transitions par un utilisateur autorisé),
-- COR-11 (résolution automatique), COR-12 (historique), ALR-07 (alertes). Conception : DC-2, DC-5, V20.
--
-- Deux axes distincts : le niveau de confiance (colonne level, ADR-0013, inchangée) et l'état de
-- traitement (colonne state). Un impact existant devient OUVERT. Un impact devenu sans objet n'est plus
-- supprimé : il passe RESOLU (CorrelationEngine) et reste consultable avec son historique.

-- ---------------------------------------------------------------------------
-- État et instantané du composant
-- ---------------------------------------------------------------------------
ALTER TABLE project_impact
    ADD COLUMN state             varchar(16)  NOT NULL DEFAULT 'OUVERT'
        CHECK (state IN ('OUVERT', 'CONFIRME', 'ECARTE', 'RESOLU')),
    ADD COLUMN package_key       varchar(500),
    ADD COLUMN component_purl    varchar(1000),
    ADD COLUMN component_version varchar(200);

-- Instantané paquet, PURL et version du composant, pour la traçabilité d'un impact dont le composant a
-- été retiré de l'inventaire (INV-C4).
UPDATE project_impact i SET package_key = c.package_key, component_purl = c.purl, component_version = c.version
FROM component c WHERE c.id = i.component_id;
ALTER TABLE project_impact ALTER COLUMN package_key SET NOT NULL;
ALTER TABLE project_impact ALTER COLUMN component_purl SET NOT NULL;

-- L'instantané suit le composant référencé à chaque écriture de la référence, quel que soit l'écrivain
-- (corrélation, jeux de données de test) ; la mise à nul par le retrait du composant le conserve.
CREATE FUNCTION argos_instantane_composant_impact() RETURNS trigger
    LANGUAGE plpgsql SET search_path = public
AS $$
BEGIN
    IF NEW.component_id IS NOT NULL THEN
        SELECT c.package_key, c.purl, c.version INTO NEW.package_key, NEW.component_purl, NEW.component_version
        FROM component c WHERE c.id = NEW.component_id;
    END IF;
    RETURN NEW;
END
$$;
REVOKE EXECUTE ON FUNCTION argos_instantane_composant_impact() FROM PUBLIC;
CREATE TRIGGER project_impact_instantane_composant
    BEFORE INSERT OR UPDATE OF component_id ON project_impact
    FOR EACH ROW EXECUTE FUNCTION argos_instantane_composant_impact();

-- Le retrait d'un composant de l'inventaire ne supprime plus ses impacts : la référence devient nulle
-- et la recorrélation du projet résout l'impact (COR-11-S1, S2). INV-C3 (composant nul => RESOLU ou
-- ECARTE) est un invariant au repos, vérifié par le contrôle d'intégrité : une contrainte CHECK ferait
-- échouer l'import qui retire le composant, la recorrélation s'exécutant après sa validation (P-13).
ALTER TABLE project_impact ALTER COLUMN component_id DROP NOT NULL;
ALTER TABLE project_impact DROP CONSTRAINT project_impact_component_id_fkey;
ALTER TABLE project_impact ADD CONSTRAINT project_impact_component_id_fkey
    FOREIGN KEY (component_id) REFERENCES component (id) ON DELETE SET NULL;

-- Listes filtrées par état (COR-12-S2) ; les impacts résolus s'accumulent.
CREATE INDEX project_impact_projet_etat ON project_impact (project_id, state);
CREATE INDEX project_impact_signal_etat ON project_impact (signal_id, state);

-- ---------------------------------------------------------------------------
-- Historique des transitions (COR-10, COR-11, COR-12)
-- ---------------------------------------------------------------------------
-- Une ligne par changement d'état, humain (actor_account_id) ou système (actor_system, motif normalisé :
-- COMPOSANT_RETIRE, VERSION_HORS_PLAGE, AVIS_MODIFIE, AVIS_RETIRE, DE_NOUVEAU_VERIFIE). workspace_id
-- recopie l'espace de l'impact pour la politique RLS.
CREATE TABLE project_impact_transition (
    id               uuid          PRIMARY KEY,
    impact_id        uuid          NOT NULL REFERENCES project_impact (id) ON DELETE CASCADE,
    workspace_id     uuid          NOT NULL REFERENCES espace (id),
    from_state       varchar(16)   NOT NULL CHECK (from_state IN ('OUVERT', 'CONFIRME', 'ECARTE', 'RESOLU')),
    to_state         varchar(16)   NOT NULL CHECK (to_state IN ('OUVERT', 'CONFIRME', 'ECARTE', 'RESOLU')),
    actor_account_id uuid          REFERENCES compte (id) ON DELETE SET NULL,
    actor_system     boolean       NOT NULL,
    reason           varchar(1000),
    comment          varchar(1000),
    occurred_at      timestamptz   NOT NULL,
    CONSTRAINT project_impact_transition_etats_ck CHECK (from_state <> to_state),
    CONSTRAINT project_impact_transition_auteur_ck CHECK (NOT actor_system OR actor_account_id IS NULL),
    -- Écarter un impact est toujours motivé (COR-10-S4).
    CONSTRAINT project_impact_transition_motif_ck CHECK (to_state <> 'ECARTE' OR length(btrim(coalesce(reason, ''))) > 0)
);
CREATE INDEX project_impact_transition_impact ON project_impact_transition (impact_id, occurred_at, id);

-- ---------------------------------------------------------------------------
-- Alertes : une alerte immédiate en attente dont tous les impacts du destinataire ont été écartés ou
-- résolus avant l'envoi n'est pas diffusée (ALR-07-S1) ; elle reste consultable à l'état ANNULEE.
-- ---------------------------------------------------------------------------
ALTER TABLE alerte DROP CONSTRAINT alerte_etat_check;
ALTER TABLE alerte ADD CONSTRAINT alerte_etat_check CHECK (etat IN ('EN_ATTENTE', 'ENVOYEE', 'EN_ECHEC', 'ANNULEE'));

-- ---------------------------------------------------------------------------
-- Droits et politiques RLS (ADR-0016) sous argos_app
-- ---------------------------------------------------------------------------
-- Une transition demandée par un utilisateur s'exécute sous argos_app avec son compte : seul un membre
-- CONTRIBUTEUR ou ADMINISTRATEUR de l'espace de l'impact peut en changer l'état (COR-10-S2), même si la
-- couche API se trompait. Les transitions système (CorrelationEngine) s'exécutent sous l'utilisateur de
-- connexion, comme la corrélation.
CREATE FUNCTION argos_traite_impacts(p_espace uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT argos_espace_lisible(p_espace) AND COALESCE(argos_role_dans(p_espace) IN ('CONTRIBUTEUR', 'ADMINISTRATEUR'), false) $$;
REVOKE EXECUTE ON FUNCTION argos_traite_impacts(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_traite_impacts(uuid) TO argos_app;

GRANT UPDATE (state) ON project_impact TO argos_app;
CREATE POLICY project_impact_etat ON project_impact FOR UPDATE TO argos_app
    USING (argos_traite_impacts(workspace_id))
    WITH CHECK (argos_traite_impacts(workspace_id));

GRANT SELECT, INSERT ON project_impact_transition TO argos_app;
ALTER TABLE project_impact_transition ENABLE ROW LEVEL SECURITY;
CREATE POLICY project_impact_transition_lecture ON project_impact_transition FOR SELECT TO argos_app
    USING (argos_espace_lisible(workspace_id));
CREATE POLICY project_impact_transition_creation ON project_impact_transition FOR INSERT TO argos_app
    WITH CHECK (NOT actor_system AND actor_account_id = argos_compte_courant() AND argos_traite_impacts(workspace_id)
                AND EXISTS (SELECT 1 FROM project_impact i WHERE i.id = project_impact_transition.impact_id
                              AND i.workspace_id = project_impact_transition.workspace_id));
