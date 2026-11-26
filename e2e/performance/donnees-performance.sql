-- Jeu de données de référence de la campagne de performance (PERF-01, PERF-02 ; P-9, en attendant Q-PERF-1).
-- Écrit sous l'utilisateur de connexion, hors RLS, comme la collecte, l'import et la corrélation (même forme que
-- e2e/donnees/recherche.sql). Idempotent : identifiants dérivés des numéros (md5), ON CONFLICT DO NOTHING.
-- Variables : espace (espace d'alice créé par l'API), projets, composants (par projet), signaux, espace_petit (espace de
-- bob créé par l'API), projets_volumineux, impacts_petit.
-- Chaque signal affecte le paquet pkg:maven/org.perf/lib-<n> (versions antérieures à 2.0.0) présent en 1.0.0 dans
-- chaque projet : il porte un impact EXACT par projet (projets × signaux impacts), un sur dix confirmé. L'espace
-- volumineux porte projets_volumineux × signaux impacts résolus ou écartés ; le petit espace, impacts_petit impacts actifs.
--
-- Les constantes du jeu et les variables psql de la commande sont réunies une fois dans la table temporaire
-- perf_constantes (alias k), que chaque instruction lit.
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

CREATE TEMP TABLE perf_constantes ON COMMIT DROP AS
SELECT :'espace'::uuid AS espace, :'espace_petit'::uuid AS espace_petit, :projets AS projets, :composants AS composants,
       :signaux AS signaux, :projets_volumineux AS projets_volumineux, :impacts_petit AS impacts_petit,
       'pkg:maven/org.perf/lib-'::text AS paquet, '1.0.0'::text AS version_composant, 'SBOM'::text AS origine,
       'performance'::text AS reference, 'PURL_RANGE_V1'::text AS methode, 'EXACT'::text AS niveau, '1.6'::text AS spec,
       'Maven'::text AS ecosysteme, 'perf-projet-'::text AS prefixe_projet, 'perf-composant-'::text AS prefixe_composant,
       'PERF-2099-'::text AS prefixe_avis, interval '1 day' AS veille, '-'::text AS tiret, '@'::text AS arobase, '0'::text AS zero,
       md5('perf-espace-volumineux')::uuid AS espace_volumineux, md5('perf-petit-projet')::uuid AS projet_petit;

INSERT INTO project (id, workspace_id, name)
SELECT md5(k.prefixe_projet || p)::uuid, k.espace, 'Projet performance ' || lpad(p::text, 3, k.zero)
FROM perf_constantes k, generate_series(1, k.projets) p
ON CONFLICT DO NOTHING;

INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
SELECT md5(k.prefixe_projet || p)::uuid, k.origine, now() - k.veille, k.spec, k.reference, gen_random_uuid(), now()
FROM perf_constantes k, generate_series(1, k.projets) p
ON CONFLICT DO NOTHING;

INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
SELECT md5(k.prefixe_composant || p || k.tiret || c)::uuid, md5(k.prefixe_projet || p)::uuid,
       k.paquet || c, k.paquet || c || k.arobase || k.version_composant, k.version_composant, c > 1,
       (SELECT i.import_id FROM inventory i WHERE i.project_id = md5(k.prefixe_projet || p)::uuid)
FROM perf_constantes k, generate_series(1, k.projets) p, generate_series(1, k.composants) c
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE perf_signal ON COMMIT DROP AS
SELECT avis.s, md5('perf-signal-' || avis.s)::uuid AS id, md5('perf-version-' || avis.s)::uuid AS version,
       avis.ident, avis.lib, jsonb_build_object('jeu', avis.reference, 'signal', avis.ident) AS provenance,
       md5(avis.ident) || md5(avis.ident || avis.reference) AS empreinte,
       now() - make_interval(days => avis.s % 300) AS modifie
FROM (SELECT s, k.prefixe_avis || lpad(s::text, 5, k.zero) AS ident, ((s - 1) % k.composants) + 1 AS lib, k.reference
      FROM perf_constantes k, generate_series(1, k.signaux) s) avis;

INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at, collected_at,
                    updated_at, content_hash, affected, current_version_id)
SELECT g.id, 'OSV', g.ident, 'Faille de performance ' || g.ident || ' dans org.perf:lib-' || g.lib,
       'Avis fictif du jeu de données de la campagne de performance.', CASE WHEN g.s % 3 = 0 THEN 'CRITICAL' ELSE 'HIGH' END,
       g.modifie, g.modifie, now(), now(), g.empreinte,
       jsonb_build_array(jsonb_build_object('packageKey', k.paquet || g.lib, 'declaredPurl', true,
           'ecosystem', k.ecosysteme, 'name', 'org.perf:lib-' || g.lib,
           'ranges', jsonb_build_array(jsonb_build_object('type', 'ECOSYSTEM', 'introduced', k.zero, 'fixed', '2.0.0',
               'lastAffected', null)),
           'versions', '[]'::jsonb)),
       g.version
FROM perf_signal g, perf_constantes k
ON CONFLICT DO NOTHING;

INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
SELECT g.version, g.id, 1, g.empreinte, g.modifie, now(), '{}'
FROM perf_signal g
ON CONFLICT DO NOTHING;

INSERT INTO signal_package (signal_id, package_key, ecosystem)
SELECT g.id, k.paquet || g.lib, k.ecosysteme FROM perf_signal g, perf_constantes k
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                            transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
SELECT md5('perf-impact-' || g.s || k.tiret || p)::uuid, g.id, g.version, md5(k.prefixe_projet || p)::uuid, k.espace,
       md5(k.prefixe_composant || p || k.tiret || g.lib)::uuid, k.niveau, k.methode, g.lib > 1, k.origine,
       now() - k.veille, g.provenance, now(), now()
FROM perf_signal g, perf_constantes k, generate_series(1, k.projets) p
ON CONFLICT DO NOTHING;

UPDATE project_impact SET state = 'CONFIRME'
FROM perf_constantes k
WHERE project_impact.workspace_id = k.espace AND project_impact.state = 'OUVERT'
  AND ('x' || substr(project_impact.id::text, 1, 2))::bit(8)::int % 10 = 0;

-- Espace volumineux, sans membre de la campagne (PERF-02-S4, PRF-07-S3) : projets_volumineux projets, chacun touché par
-- chaque signal ; impacts résolus (neuf sur dix) ou écartés, composant retiré, mis à jour récemment : ils précèdent les
-- impacts des autres espaces dans l'ordre des dates.
INSERT INTO espace (id, nom, cree_par)
SELECT k.espace_volumineux, 'Espace volumineux', e.cree_par FROM espace e, perf_constantes k WHERE e.id = k.espace
ON CONFLICT DO NOTHING;

INSERT INTO project (id, workspace_id, name)
SELECT md5('perf-volumineux-' || p)::uuid, k.espace_volumineux, 'Projet volumineux ' || lpad(p::text, 4, k.zero)
FROM perf_constantes k, generate_series(1, k.projets_volumineux) p
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                            transitive, inventory_origin, inventory_date, provenance, created_at, updated_at, state,
                            package_key, component_purl, component_version)
SELECT md5('perf-impact-volumineux-' || g.s || k.tiret || p)::uuid, g.id, g.version, md5('perf-volumineux-' || p)::uuid,
       k.espace_volumineux, NULL, k.niveau, k.methode, false, k.origine, now() - k.veille,
       g.provenance, now() - interval '30 days',
       now() - make_interval(secs => (g.s * 7 + p) % 3600), CASE WHEN (g.s + p) % 10 = 0 THEN 'ECARTE' ELSE 'RESOLU' END,
       k.paquet || g.lib, k.paquet || g.lib || k.arobase || k.version_composant, k.version_composant
FROM perf_signal g, perf_constantes k, generate_series(1, k.projets_volumineux) p
ON CONFLICT DO NOTHING;

-- Petit espace de bob (PRF-07-S3) : un projet, impacts actifs des impacts_petit premiers signaux (moins de 200).
INSERT INTO project (id, workspace_id, name)
SELECT k.projet_petit, k.espace_petit, 'Projet petit espace' FROM perf_constantes k
ON CONFLICT DO NOTHING;

INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
SELECT k.projet_petit, k.origine, now() - k.veille, k.spec, k.reference, gen_random_uuid(), now() FROM perf_constantes k
ON CONFLICT DO NOTHING;

INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
SELECT md5('perf-composant-petit-' || c)::uuid, k.projet_petit, k.paquet || c,
       k.paquet || c || k.arobase || k.version_composant, k.version_composant, c > 1,
       (SELECT i.import_id FROM inventory i WHERE i.project_id = k.projet_petit)
FROM perf_constantes k, generate_series(1, k.composants) c
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id, level, method,
                            transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
SELECT md5('perf-impact-petit-' || g.s)::uuid, g.id, g.version, k.projet_petit, k.espace_petit,
       md5('perf-composant-petit-' || g.lib)::uuid, k.niveau, k.methode, g.lib > 1, k.origine, now() - k.veille,
       g.provenance, now() - interval '60 days', now() - interval '60 days'
FROM perf_signal g, perf_constantes k
WHERE g.s <= k.impacts_petit
ON CONFLICT DO NOTHING;

COMMIT;
ANALYZE;
