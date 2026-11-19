-- Jeu de données de référence de la campagne de performance (PERF-01, PERF-02 ; P-9, en attendant Q-PERF-1).
-- Écrit sous l'utilisateur de connexion, hors RLS, comme la collecte, l'import et la corrélation (même forme que
-- e2e/donnees/recherche.sql). Idempotent : identifiants dérivés des numéros (md5), ON CONFLICT DO NOTHING.
-- Variables : espace (espace d'alice créé par l'API), projets, composants (par projet), signaux.
-- Chaque signal affecte le paquet pkg:maven/org.perf/lib-<n> (versions antérieures à 2.0.0) présent en 1.0.0 dans
-- chaque projet : il porte un impact EXACT par projet (projets × signaux impacts), un sur dix confirmé.
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

INSERT INTO project (id, workspace_id, name)
SELECT md5('perf-projet-' || p)::uuid, :'espace', 'Projet performance ' || lpad(p::text, 3, '0')
FROM generate_series(1, :projets) p
ON CONFLICT DO NOTHING;

INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
SELECT md5('perf-projet-' || p)::uuid, 'SBOM', now() - interval '1 day', '1.6', 'performance', gen_random_uuid(), now()
FROM generate_series(1, :projets) p
ON CONFLICT DO NOTHING;

INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
SELECT md5('perf-composant-' || p || '-' || c)::uuid, md5('perf-projet-' || p)::uuid,
       'pkg:maven/org.perf/lib-' || c, 'pkg:maven/org.perf/lib-' || c || '@1.0.0', '1.0.0', c > 1,
       (SELECT import_id FROM inventory WHERE project_id = md5('perf-projet-' || p)::uuid)
FROM generate_series(1, :projets) p, generate_series(1, :composants) c
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE perf_signal ON COMMIT DROP AS
SELECT s, md5('perf-signal-' || s)::uuid AS id, md5('perf-version-' || s)::uuid AS version,
       'PERF-2099-' || lpad(s::text, 5, '0') AS ident, ((s - 1) % :composants) + 1 AS lib
FROM generate_series(1, :signaux) s;

INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at, collected_at,
                    updated_at, content_hash, affected, current_version_id)
SELECT id, 'OSV', ident, 'Faille de performance ' || ident || ' dans org.perf:lib-' || lib,
       'Avis fictif du jeu de données de la campagne de performance.', CASE WHEN s % 3 = 0 THEN 'CRITICAL' ELSE 'HIGH' END,
       now() - make_interval(days => s % 300), now() - make_interval(days => s % 300), now(), now(),
       md5(ident) || md5(ident || 'perf'),
       jsonb_build_array(jsonb_build_object('packageKey', 'pkg:maven/org.perf/lib-' || lib, 'declaredPurl', true,
           'ecosystem', 'Maven', 'name', 'org.perf:lib-' || lib,
           'ranges', jsonb_build_array(jsonb_build_object('type', 'ECOSYSTEM', 'introduced', '0', 'fixed', '2.0.0',
               'lastAffected', null)),
           'versions', '[]'::jsonb)),
       version
FROM perf_signal
ON CONFLICT DO NOTHING;

INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
SELECT version, id, 1, md5(ident) || md5(ident || 'perf'), now() - make_interval(days => s % 300), now(), '{}'
FROM perf_signal
ON CONFLICT DO NOTHING;

INSERT INTO signal_package (signal_id, package_key, ecosystem)
SELECT id, 'pkg:maven/org.perf/lib-' || lib, 'Maven' FROM perf_signal
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                            transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
SELECT md5('perf-impact-' || s.s || '-' || p)::uuid, s.id, s.version, md5('perf-projet-' || p)::uuid, :'espace',
       md5('perf-composant-' || p || '-' || s.lib)::uuid, 'EXACT', 'PURL_RANGE_V1', s.lib > 1, 'SBOM',
       now() - interval '1 day', jsonb_build_object('jeu', 'performance', 'signal', s.ident), now(), now()
FROM perf_signal s, generate_series(1, :projets) p
ON CONFLICT DO NOTHING;

UPDATE project_impact SET state = 'CONFIRME'
WHERE workspace_id = :'espace' AND state = 'OUVERT' AND ('x' || substr(id::text, 1, 2))::bit(8)::int % 10 = 0;

COMMIT;
ANALYZE;
