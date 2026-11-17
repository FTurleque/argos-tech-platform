-- Jeu de données des parcours « Ma veille » (personnaliser-profils-et-affichage, tâches 2.4, 2.6, 3.4,
-- 3.9 et 5.2) : avis de sécurité, impacts et fiches réglementaires de deux espaces, écrits sous
-- l'utilisateur de connexion, hors RLS, comme les écrivent la collecte, la corrélation et la préparation
-- des fiches (même forme que ReglementaireFixtures et JeuRecherche des tests serveur). Les espaces, les
-- comptes, les membres, les profils et l'abonnement personnel sont créés par l'API (support/donnees.ts).
-- Idempotent : identifiants fixes. Textes fictifs.
-- Variables : espace_a (espace d'ines, où jules est lecteur), espace_b (espace de karim), ines (compte
--             d'ines, expert du domaine « paie »).
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

-- Projets : « Paie E2E veille » (espace A), « RH confidentiel » (espace B, invisible de jules).
INSERT INTO project (id, workspace_id, name) VALUES
  ('0e2e0000-0000-4000-8000-000000000101', :'espace_a', 'Paie E2E veille'),
  ('0e2e0000-0000-4000-8000-000000000102', :'espace_b', 'RH confidentiel')
ON CONFLICT DO NOTHING;

INSERT INTO inventory
SELECT p.id, 'SBOM', now(), '1.6', 'e2e', gen_random_uuid(), now()
FROM project p
WHERE p.id IN ('0e2e0000-0000-4000-8000-000000000101', '0e2e0000-0000-4000-8000-000000000102')
ON CONFLICT DO NOTHING;

INSERT INTO component (id, project_id, package_key, purl, version, import_id) VALUES
  ('0e2e0000-0000-4000-8000-0000000001c1', '0e2e0000-0000-4000-8000-000000000101',
   'pkg:maven/org.e2e/moteur-paie', 'pkg:maven/org.e2e/moteur-paie@1', '1', gen_random_uuid()),
  ('0e2e0000-0000-4000-8000-0000000001c2', '0e2e0000-0000-4000-8000-000000000102',
   'pkg:maven/org.e2e/export-rh', 'pkg:maven/org.e2e/export-rh@1', '1', gen_random_uuid())
ON CONFLICT DO NOTHING;

-- Avis de sécurité : identifiant, titre, gravité, âge en jours, projet touché (ou aucun), paquet affecté
-- (critère PACKAGE de l'abonnement personnel de jules, ou aucun).
--   V1 : touche « Paie E2E veille » et est retenu par l'abonnement de jules (dédoublonnage profil + abonnement).
--   V2 : touche « RH confidentiel » seulement ; retenu par l'abonnement de jules, qui n'en voit pas le projet.
--   V3 : touche « RH confidentiel » seulement ; aucun abonnement de jules ne le retient.
CREATE TEMP TABLE e2e_avis (id uuid, version uuid, ident text, titre text, gravite text, jours int, projet uuid,
                            paquet text) ON COMMIT DROP;
INSERT INTO e2e_avis VALUES
  ('0e2e0000-0000-4000-8000-0000000001a1', '0e2e0000-0000-4000-8000-0000000001b1', 'E2E-VEILLE-0001',
   'Moteur de paie : faille d''injection corrigée', 'CRITICAL', 1, '0e2e0000-0000-4000-8000-000000000101',
   'pkg:npm/e2e-veille-commun'),
  ('0e2e0000-0000-4000-8000-0000000001a2', '0e2e0000-0000-4000-8000-0000000001b2', 'E2E-VEILLE-0002',
   'Bibliothèque d''export : fuite de données', 'MEDIUM', 2, '0e2e0000-0000-4000-8000-000000000102',
   'pkg:npm/e2e-veille-abonne'),
  ('0e2e0000-0000-4000-8000-0000000001a3', '0e2e0000-0000-4000-8000-0000000001b3', 'E2E-VEILLE-0003',
   'Export RH : faille réservée à l''espace B', 'HIGH', 3, '0e2e0000-0000-4000-8000-000000000102', NULL);

INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                    collected_at, updated_at, content_hash, affected, current_version_id)
SELECT id, 'OSV', ident, titre, NULL, gravite, now() - make_interval(days => jours),
       now() - make_interval(days => jours), now(), now(), md5(ident) || md5(titre), '[]', version
FROM e2e_avis
ON CONFLICT DO NOTHING;

INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
SELECT version, id, 1, md5(ident) || md5(titre), now() - make_interval(days => jours), now(), '{}'
FROM e2e_avis
ON CONFLICT DO NOTHING;

INSERT INTO signal_package (signal_id, package_key, ecosystem)
SELECT id, paquet, 'npm' FROM e2e_avis WHERE paquet IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                            level, method, inventory_origin, provenance, created_at, updated_at)
SELECT gen_random_uuid(), a.id, a.version, p.id, p.workspace_id, c.id, 'EXACT', 'PURL_EXACT', 'SBOM',
       '{"e2e":true}', now(), now()
FROM e2e_avis a
JOIN project p ON p.id = a.projet
JOIN component c ON c.project_id = p.id
WHERE NOT EXISTS (SELECT 1 FROM project_impact i WHERE i.signal_id = a.id AND i.project_id = p.id);

-- Veille réglementaire de l'espace A : domaine « paie », expert ines, trois fiches.
--   F1 : validée, résumé produit par l'IA (mention « produit par l'IA »).
--   F2 : validée, résumé relu par l'expert.
--   F3 : en attente de validation (garde-fou : jamais restituée).
INSERT INTO domaine_metier (espace_id, code, libelle, modifie_par, modifie_le)
VALUES (:'espace_a', 'paie', 'Paie', :'ines', now())
ON CONFLICT DO NOTHING;

INSERT INTO expert_domaine (espace_id, domaine, compte_id, modifie_par, modifie_le)
VALUES (:'espace_a', 'paie', :'ines', :'ines', now())
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE e2e_fiche (texte uuid, fiche uuid, ident text, titre text, publication int, effet date,
                             etat text, resume text, origine text) ON COMMIT DROP;
INSERT INTO e2e_fiche VALUES
  ('0e2e0000-0000-4000-8000-0000000001d1', '0e2e0000-0000-4000-8000-0000000001f1', 'JORFTEXT000098800001',
   'Décret E2E relatif au calcul de la paie des agents', 4, DATE '2027-01-01', 'VALIDEE',
   'Le décret modifie l''assiette des cotisations prises en compte dans la paie.', 'IA'),
  ('0e2e0000-0000-4000-8000-0000000001d2', '0e2e0000-0000-4000-8000-0000000001f2', 'JORFTEXT000098800002',
   'Arrêté E2E fixant le barème des indemnités', 6, NULL, 'VALIDEE',
   'Résumé relu par l''expert : nouveau barème applicable aux indemnités.', 'EXPERT'),
  ('0e2e0000-0000-4000-8000-0000000001d3', '0e2e0000-0000-4000-8000-0000000001f3', 'JORFTEXT000098800003',
   'Arrêté E2E en attente de validation', 5, NULL, 'EN_ATTENTE_VALIDATION',
   'Résumé proposé, non relu.', 'IA');

INSERT INTO texte_officiel (id, identifiant, source, nature, nor, titre, date_publication, date_effet,
                            motif_date_effet, archive, collecte_le)
SELECT texte, ident, 'JORF', 'DECRET', 'TEST2600001D', titre, current_date - publication, effet,
       CASE WHEN effet IS NULL THEN NULL ELSE 'e2e' END, 'e2e', now()
FROM e2e_fiche
ON CONFLICT DO NOTHING;

INSERT INTO fiche_reglementaire (id, texte_id, espace_id, etat_validation, etat_suivi, resume, resume_origine,
                                 date_effet, date_effet_origine, decide_par, decide_le, cree_le, modifie_le)
SELECT f.fiche, f.texte, :'espace_a', f.etat, CASE WHEN f.etat = 'VALIDEE' THEN 'EN_ANALYSE' ELSE 'NOUVEAU' END,
       f.resume, f.origine, f.effet, CASE WHEN f.effet IS NULL THEN NULL ELSE 'TEXTE' END,
       CASE WHEN f.etat = 'VALIDEE' THEN :'ines'::uuid END, CASE WHEN f.etat = 'VALIDEE' THEN now() END, now(), now()
FROM e2e_fiche f
ON CONFLICT DO NOTHING;

INSERT INTO fiche_domaine (fiche_id, espace_id, domaine, origine, motif)
SELECT fiche, :'espace_a', 'paie', 'REGLE', 'e2e' FROM e2e_fiche
ON CONFLICT DO NOTHING;

COMMIT;
