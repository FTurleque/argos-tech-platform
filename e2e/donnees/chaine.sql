-- Projet du parcours de la chaîne automatisée (changement automatiser-chaine-veille, tâche 4.3) : un projet de
-- l'espace « Espace E2E chaîne » qui regroupe deux dépôts (décision J6 du 2026-11-19), l'un sur GitLab (principal,
-- SBOM produite par le job « sbom »), l'autre sur GitHub (SBOM versionnée dans le dépôt). Les deux forges sont
-- simulées par support/sources-simulees.mjs ; l'import planifié (J3) lit leurs SBOM sans action d'un utilisateur.
-- Écrit comme ProjectDirectory#create puis #ajouterDepot, sans inventaire : le premier inventaire vient de l'import
-- planifié. Idempotent : identifiants fixes.
-- Variable : espace (identifiant de « Espace E2E chaîne »).
BEGIN;

INSERT INTO project (id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path)
VALUES ('0e2e0000-0000-4000-8000-0000000000c1', :'espace', 'Chaîne E2E', 'GITLAB', 'e2e/chaine-front', 'main', 'sbom',
        'bom.cdx.json')
ON CONFLICT DO NOTHING;

INSERT INTO project_repository (id, project_id, provider, repository, branch, sbom_job, sbom_path, principal)
VALUES
  ('0e2e0000-0000-4000-8000-0000000000d1', '0e2e0000-0000-4000-8000-0000000000c1', 'GITLAB', 'e2e/chaine-front', 'main',
   'sbom', 'bom.cdx.json', true),
  ('0e2e0000-0000-4000-8000-0000000000d2', '0e2e0000-0000-4000-8000-0000000000c1', 'GITHUB', 'e2e/chaine-api', 'main',
   NULL, 'bom.json', false)
ON CONFLICT DO NOTHING;

COMMIT;
