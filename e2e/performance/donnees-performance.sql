-- Jeu de données de référence de la campagne de performance (PERF-01, PERF-02 ; P-9, en attendant Q-PERF-1).
-- Écrit sous l'utilisateur de connexion, hors RLS, comme la collecte, l'import et la corrélation (même forme que
-- e2e/donnees/recherche.sql). Idempotent : identifiants dérivés des numéros (md5), ON CONFLICT DO NOTHING.
-- Variables : espace (espace d'alice créé par l'API), projets, composants (par projet), signaux, espace_petit (espace de
-- bob créé par l'API), projets_volumineux, impacts_petit.
-- Chaque signal affecte le paquet pkg:maven/org.perf/lib-<n> (versions antérieures à 2.0.0) présent en 1.0.0 dans
-- chaque projet : il porte un impact EXACT par projet (projets × signaux impacts), un sur dix confirmé. L'espace
-- volumineux porte projets_volumineux × signaux impacts résolus ou écartés ; le petit espace, impacts_petit impacts actifs.

-- Littéraux communs du jeu de données (variables psql).
\set paquet 'pkg:maven/org.perf/lib-'
\set version_composant '1.0.0'
\set origine 'SBOM'
\set reference 'performance'
\set methode 'PURL_RANGE_V1'
\set prefixe_projet 'perf-projet-'
\set veille '1 day'
SELECT md5('perf-espace-volumineux')::uuid AS espace_volumineux, md5('perf-petit-projet')::uuid AS projet_petit \gset

BEGIN;
SET CONSTRAINTS ALL DEFERRED;

INSERT INTO project (id, workspace_id, name)
SELECT md5(:'prefixe_projet' || p)::uuid, :'espace', 'Projet performance ' || lpad(p::text, 3, '0')
FROM generate_series(1, :projets) p
ON CONFLICT DO NOTHING;

INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
SELECT md5(:'prefixe_projet' || p)::uuid, :'origine', now() - :'veille'::interval, '1.6', :'reference', gen_random_uuid(), now()
FROM generate_series(1, :projets) p
ON CONFLICT DO NOTHING;

INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
SELECT md5('perf-composant-' || p || '-' || c)::uuid, md5(:'prefixe_projet' || p)::uuid,
       :'paquet' || c, :'paquet' || c || '@' || :'version_composant', :'version_composant', c > 1,
       (SELECT import_id FROM inventory WHERE project_id = md5(:'prefixe_projet' || p)::uuid)
FROM generate_series(1, :projets) p, generate_series(1, :composants) c
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE perf_signal ON COMMIT DROP AS
SELECT s, md5('perf-signal-' || s)::uuid AS id, md5('perf-version-' || s)::uuid AS version,
       'PERF-2099-' || lpad(s::text, 5, '0') AS ident, ((s - 1) % :composants) + 1 AS lib,
       jsonb_build_object('jeu', :'reference', 'signal', 'PERF-2099-' || lpad(s::text, 5, '0')) AS provenance
FROM generate_series(1, :signaux) s;

INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at, collected_at,
                    updated_at, content_hash, affected, current_version_id)
SELECT id, 'OSV', ident, 'Faille de performance ' || ident || ' dans org.perf:lib-' || lib,
       'Avis fictif du jeu de données de la campagne de performance.', CASE WHEN s % 3 = 0 THEN 'CRITICAL' ELSE 'HIGH' END,
       now() - make_interval(days => s % 300), now() - make_interval(days => s % 300), now(), now(),
       md5(ident) || md5(ident || 'perf'),
       jsonb_build_array(jsonb_build_object('packageKey', :'paquet' || lib, 'declaredPurl', true,
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
SELECT id, :'paquet' || lib, 'Maven' FROM perf_signal
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                            transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
SELECT md5('perf-impact-' || s.s || '-' || p)::uuid, s.id, s.version, md5(:'prefixe_projet' || p)::uuid, :'espace',
       md5('perf-composant-' || p || '-' || s.lib)::uuid, 'EXACT', :'methode', s.lib > 1, :'origine',
       now() - :'veille'::interval, s.provenance, now(), now()
FROM perf_signal s, generate_series(1, :projets) p
ON CONFLICT DO NOTHING;

UPDATE project_impact SET state = 'CONFIRME'
WHERE workspace_id = :'espace' AND state = 'OUVERT' AND ('x' || substr(id::text, 1, 2))::bit(8)::int % 10 = 0;

-- Espace volumineux, sans membre de la campagne (PERF-02-S4, PRF-07-S3) : projets_volumineux projets, chacun touché par
-- chaque signal ; impacts résolus (neuf sur dix) ou écartés, composant retiré, mis à jour récemment : ils précèdent les
-- impacts des autres espaces dans l'ordre des dates.
INSERT INTO espace (id, nom, cree_par)
SELECT :'espace_volumineux'::uuid, 'Espace volumineux', cree_par FROM espace WHERE id = :'espace'
ON CONFLICT DO NOTHING;

INSERT INTO project (id, workspace_id, name)
SELECT md5('perf-volumineux-' || p)::uuid, :'espace_volumineux'::uuid, 'Projet volumineux ' || lpad(p::text, 4, '0')
FROM generate_series(1, :projets_volumineux) p
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                            transitive, inventory_origin, inventory_date, provenance, created_at, updated_at, state,
                            package_key, component_purl, component_version)
SELECT md5('perf-impact-volumineux-' || s.s || '-' || p)::uuid, s.id, s.version, md5('perf-volumineux-' || p)::uuid,
       :'espace_volumineux'::uuid, NULL, 'EXACT', :'methode', false, :'origine', now() - :'veille'::interval,
       s.provenance, now() - interval '30 days',
       now() - make_interval(secs => (s.s * 7 + p) % 3600), CASE WHEN (s.s + p) % 10 = 0 THEN 'ECARTE' ELSE 'RESOLU' END,
       :'paquet' || s.lib, :'paquet' || s.lib || '@' || :'version_composant', :'version_composant'
FROM perf_signal s, generate_series(1, :projets_volumineux) p
ON CONFLICT DO NOTHING;

-- Petit espace de bob (PRF-07-S3) : un projet, impacts actifs des impacts_petit premiers signaux (moins de 200).
INSERT INTO project (id, workspace_id, name)
VALUES (:'projet_petit'::uuid, :'espace_petit', 'Projet petit espace')
ON CONFLICT DO NOTHING;

INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
VALUES (:'projet_petit'::uuid, :'origine', now() - :'veille'::interval, '1.6', :'reference', gen_random_uuid(), now())
ON CONFLICT DO NOTHING;

INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
SELECT md5('perf-composant-petit-' || c)::uuid, :'projet_petit'::uuid, :'paquet' || c,
       :'paquet' || c || '@' || :'version_composant', :'version_composant', c > 1,
       (SELECT import_id FROM inventory WHERE project_id = :'projet_petit'::uuid)
FROM generate_series(1, :composants) c
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                            transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
SELECT md5('perf-impact-petit-' || s.s)::uuid, s.id, s.version, :'projet_petit'::uuid, :'espace_petit',
       md5('perf-composant-petit-' || s.lib)::uuid, 'EXACT', :'methode', s.lib > 1, :'origine', now() - :'veille'::interval,
       s.provenance, now() - interval '60 days', now() - interval '60 days'
FROM perf_signal s
WHERE s.s <= :impacts_petit
ON CONFLICT DO NOTHING;

COMMIT;
ANALYZE;
