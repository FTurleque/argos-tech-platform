-- Jeu de données du parcours « vue d'inventaire » (formaliser-chaine-securite-poc, tâches 3.7 et 3.8 ;
-- INV-01-S3, INV-06 ; décisions E4, F1 et F5 du porteur du projet, 2026-11-19). Écrit sous l'utilisateur de
-- connexion, hors RLS, comme l'import d'une SBOM : inventaire de référence, 25 composants inventoriés (deux
-- pages de 20 dans la vue) et composants écartés faute de PURL (colonnes ignored_count et ignored_components
-- de la migration V17). Idempotent : identifiants fixes.
-- Variable : espace (identifiant de l'espace « Espace E2E inventaire » d'alice, créé par l'API).
-- Aucun signal ni impact : le projet n'apparaît dans aucune recherche ni veille des autres parcours.
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

INSERT INTO project (id, workspace_id, name) VALUES
  ('0e2e0000-0000-4000-8000-0000000000e1', :'espace', 'Inventaire E2E'),
  ('0e2e0000-0000-4000-8000-0000000000e2', :'espace', 'Sans SBOM E2E')
ON CONFLICT DO NOTHING;

-- Dernier import : 3 composants écartés, les 2 premiers décrits ; le nom du second est une valeur hostile
-- de la SBOM, qui doit être affichée comme texte.
INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at,
                       ignored_count, ignored_components)
VALUES ('0e2e0000-0000-4000-8000-0000000000e1', 'SBOM', now() - interval '2 days', '1.6', 'e2e',
        '0e2e0000-0000-4000-8000-0000000000f1', now(), 3,
        '[{"name": "outil-interne", "group": "fr.e2e", "version": "1.0", "reason": "SANS_PURL"},
          {"name": "<img src=x onerror=alert(1)>", "group": null, "version": null, "reason": "PURL_INVALIDE"}]')
ON CONFLICT (project_id) DO UPDATE
  SET ignored_count = EXCLUDED.ignored_count, ignored_components = EXCLUDED.ignored_components;

INSERT INTO component (id, project_id, package_key, purl, version, import_id) VALUES
  ('0e2e0000-0000-4000-8000-0000000000e3', '0e2e0000-0000-4000-8000-0000000000e1',
   'pkg:maven/org.e2e/inventaire', 'pkg:maven/org.e2e/inventaire@1.0', '1.0',
   '0e2e0000-0000-4000-8000-0000000000f1')
ON CONFLICT DO NOTHING;

-- 24 composants npm e2e-paquet-01 à e2e-paquet-24 : le premier direct, les autres transitifs. Triés par nom,
-- ils précèdent org.e2e/inventaire (lien inconnu), dernier composant de la seconde page.
INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
SELECT format('0e2e0000-0000-4000-8000-0000000001%s', lpad(n::text, 2, '0'))::uuid,
       '0e2e0000-0000-4000-8000-0000000000e1',
       format('pkg:npm/e2e-paquet-%s', lpad(n::text, 2, '0')),
       format('pkg:npm/e2e-paquet-%s@1.0.%s', lpad(n::text, 2, '0'), n),
       format('1.0.%s', n),
       n > 1,
       '0e2e0000-0000-4000-8000-0000000000f1'
FROM generate_series(1, 24) AS n
ON CONFLICT DO NOTHING;

COMMIT;
