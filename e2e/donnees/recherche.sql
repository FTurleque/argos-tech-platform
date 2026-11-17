-- Jeu de données du parcours « recherche Oracle, projet, 30 jours » (rechercher-et-filtrer 4.2).
-- Écrit sous l'utilisateur de connexion, hors RLS, comme la collecte et la corrélation
-- (même forme que JeuRecherche des tests serveur). Idempotent : identifiants fixes.
-- Variable : espace (identifiant de l'espace d'alice créé par l'API).
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

INSERT INTO project (id, workspace_id, name) VALUES
  ('0e2e0000-0000-4000-8000-000000000001', :'espace', 'Paie'),
  ('0e2e0000-0000-4000-8000-000000000002', :'espace', 'Comptabilité')
ON CONFLICT DO NOTHING;

INSERT INTO inventory
SELECT p.id, 'SBOM', now(), '1.6', 'e2e', gen_random_uuid(), now()
FROM project p
WHERE p.id IN ('0e2e0000-0000-4000-8000-000000000001', '0e2e0000-0000-4000-8000-000000000002')
ON CONFLICT DO NOTHING;

INSERT INTO component (id, project_id, package_key, purl, version, import_id) VALUES
  ('0e2e0000-0000-4000-8000-0000000000c1', '0e2e0000-0000-4000-8000-000000000001',
   'pkg:maven/org.test/composant', 'pkg:maven/org.test/composant@1', '1', gen_random_uuid()),
  ('0e2e0000-0000-4000-8000-0000000000c2', '0e2e0000-0000-4000-8000-000000000002',
   'pkg:maven/org.test/composant', 'pkg:maven/org.test/composant@1', '1', gen_random_uuid())
ON CONFLICT DO NOTHING;

-- Signaux : identifiant, titre, résumé, gravité, âge en jours, projet touché.
CREATE TEMP TABLE e2e_signal (id uuid, version uuid, ident text, titre text, resume text, gravite text,
                              jours int, projet uuid) ON COMMIT DROP;
INSERT INTO e2e_signal VALUES
  ('0e2e0000-0000-4000-8000-0000000000a1', '0e2e0000-0000-4000-8000-0000000000b1', 'E2E-ORACLE-0001',
   'Oracle Database : faille corrigée', 'Le correctif Oracle corrige une faille de la base.', 'HIGH', 5,
   '0e2e0000-0000-4000-8000-000000000001'),
  ('0e2e0000-0000-4000-8000-0000000000a2', '0e2e0000-0000-4000-8000-0000000000b2', 'E2E-ORACLE-0002',
   'Oracle Database : faille ancienne', 'Correctif Oracle publié il y a deux mois.', 'HIGH', 60,
   '0e2e0000-0000-4000-8000-000000000001'),
  ('0e2e0000-0000-4000-8000-0000000000a3', '0e2e0000-0000-4000-8000-0000000000b3', 'E2E-ORACLE-0003',
   'Oracle Java : faille', 'Correctif Oracle pour Java.', 'HIGH', 5,
   '0e2e0000-0000-4000-8000-000000000002'),
  ('0e2e0000-0000-4000-8000-0000000000a4', '0e2e0000-0000-4000-8000-0000000000b4', 'E2E-PG-0001',
   'PostgreSQL : faille', 'Correctif de la base PostgreSQL.', 'HIGH', 5,
   '0e2e0000-0000-4000-8000-000000000001');

INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                    collected_at, updated_at, content_hash, affected, current_version_id)
SELECT id, 'OSV', ident, titre, resume, gravite, now() - make_interval(days => jours),
       now() - make_interval(days => jours), now(), now(), md5(ident) || md5(titre), '[]', version
FROM e2e_signal
ON CONFLICT DO NOTHING;

INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
SELECT version, id, 1, md5(ident) || md5(titre), now() - make_interval(days => jours), now(), '{}'
FROM e2e_signal
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                            level, method, inventory_origin, provenance, created_at, updated_at)
SELECT gen_random_uuid(), s.id, s.version, p.id, p.workspace_id, c.id, 'EXACT', 'PURL_EXACT', 'SBOM',
       '{"e2e":true}', now(), now()
FROM e2e_signal s
JOIN project p ON p.id = s.projet
JOIN component c ON c.project_id = p.id
WHERE NOT EXISTS (SELECT 1 FROM project_impact i WHERE i.signal_id = s.id AND i.project_id = p.id);

COMMIT;
