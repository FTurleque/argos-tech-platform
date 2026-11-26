-- Index des requêtes chaudes (changement OpenSpec accelerer-requetes-chaudes, tâche 1.1 ; constats AUD-PER-09,
-- AUD-PER-26, AUD-PER-29 et AUD-PER-06 de l'audit du 2026-11-21). Exigences : COR-13, PRF-07. Conception : design.md,
-- « Persistance et intégrité », D1 et D6.
--
-- Aucune donnée, aucun droit ni aucune politique RLS ne change. Retour arrière : une migration suivante recrée les
-- index supprimés et supprime ceux-ci (arc42 §8.7).

-- ---------------------------------------------------------------------------
-- Clés étrangères vers component : le retrait d'un composant (import d'inventaire) cherche ses lignes par
-- component_id (ON DELETE SET NULL sur project_impact, ON DELETE CASCADE sur dette_version).
-- ---------------------------------------------------------------------------
CREATE INDEX project_impact_composant ON project_impact (component_id) WHERE component_id IS NOT NULL;
CREATE INDEX dette_version_composant ON dette_version (component_id);

-- ---------------------------------------------------------------------------
-- Listes d'exploitation : filtre et ordre de lecture (du plus récent au plus ancien, puis identifiant).
-- ---------------------------------------------------------------------------
CREATE INDEX ingestion_failure_source ON ingestion_failure (source_id, received_at DESC, id DESC);
CREATE INDEX ops_event_source ON ops_event (source_id, occurred_at DESC, id DESC);
CREATE INDEX sync_attempt_run ON sync_attempt (run_id);
CREATE INDEX inventory_import_failure_projet ON inventory_import_failure (project_id, received_at DESC, id DESC);
CREATE INDEX inventory_import_failure_depot ON inventory_import_failure (repository_id, received_at)
    WHERE repository_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Purges planifiées des journaux (changement retenir-importer-et-paginer) : lignes cherchées par date de réception
-- ou par traitement de synchronisation.
-- ---------------------------------------------------------------------------
CREATE INDEX actualite_recue ON actualite (recu_le);
CREATE INDEX ingestion_failure_run ON ingestion_failure (run_id) WHERE run_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- project_impact : index redondants. signal_id est en tête de project_impact_signal_etat et de
-- project_impact_triplet_uk ; project_id est en tête de project_impact_projet_etat.
-- ---------------------------------------------------------------------------
DROP INDEX project_impact_signal;
DROP INDEX project_impact_project;

-- ---------------------------------------------------------------------------
-- Impacts actifs les plus récents d'un espace (« Ma veille », outil MCP get_security_alerts) : lus espace par
-- espace (ImpactQueries.forWorkspaces), chaque branche parcourt au plus la limite demandée dans l'ordre de cet index,
-- sans lire les impacts des autres espaces ni les impacts résolus ou écartés, qui s'accumulent.
-- ---------------------------------------------------------------------------
DROP INDEX project_impact_recents;
CREATE INDEX project_impact_actifs_recents ON project_impact (workspace_id, updated_at DESC, id)
    WHERE state IN ('OUVERT', 'CONFIRME');

-- ---------------------------------------------------------------------------
-- Critères de technologie des abonnements et de la recherche (SignalQueries.search, argos_selectionner_veille) :
-- index sur les expressions exactes des requêtes, qui doivent rester identiques.
-- ---------------------------------------------------------------------------
CREATE INDEX signal_package_ecosysteme ON signal_package (lower(ecosystem));
CREATE INDEX signal_package_type ON signal_package ((split_part(substr(package_key, 5), '/', 1)));
CREATE INDEX signal_package_nom ON signal_package ((lower(regexp_replace(package_key, '^.*/', ''))));
CREATE INDEX signal_modifie ON signal (modified_at DESC, id);
