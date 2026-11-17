-- Émission d'une alerte immédiate du canal DESKTOP pour les parcours du client desktop
-- (notifier-sur-le-poste, tâches 1.1, 1.2 et 3.2). Écrite sous l'utilisateur de connexion, hors RLS,
-- comme l'écrit le moteur d'alertes (MoteurAlertes : signal, puis alerte IMMEDIATE à l'état
-- EN_ATTENTE avec son contenu propre au destinataire). Aucun canal ne pousse le DESKTOP côté serveur :
-- l'alerte reste EN_ATTENTE jusqu'à la confirmation de remise par le client desktop.
-- Idempotent : identifiants fixes ; une nouvelle émission remet l'alerte EN_ATTENTE, datée de l'instant.
-- Textes fictifs. Le titre du signal et les noms de projets ne doivent jamais apparaître dans une
-- notification (NOT-05).
-- Variables : compte (destinataire), numero (01 à 06, alerte du tableau ci-dessous).
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

-- Alertes : numéro, titre du signal, gravité, projets cités (noms fictifs).
CREATE TEMP TABLE e2e_alerte_poste (numero text, titre text, gravite text, projets text[]) ON COMMIT DROP;
INSERT INTO e2e_alerte_poste VALUES
  ('01', 'Bibliothèque e2e-journal : déni de service', 'HIGH', ARRAY['Paie E2E poste']),
  ('02', 'Pilote e2e-pdf : lecture hors limites', 'MEDIUM', ARRAY['Paie E2E poste', 'RH E2E poste']),
  ('03', 'Composant e2e-log4x : exécution de code à distance', 'CRITICAL',
   ARRAY['Paie E2E poste', 'RH E2E poste', 'Achats E2E poste']),
  ('04', 'Serveur e2e-mail : contournement d''authentification', 'CRITICAL', ARRAY['RH E2E poste']),
  ('05', 'Bibliothèque e2e-xml : injection d''entité externe', 'CRITICAL', ARRAY['Paie E2E poste']),
  ('06', 'Cadriciel e2e-web : falsification de requête', 'CRITICAL', ARRAY['Achats E2E poste']);

CREATE TEMP TABLE e2e_choisie ON COMMIT DROP AS
SELECT a.*,
       ('0e2e0000-0000-4000-8000-0000000003' || a.numero)::uuid AS signal_id,
       ('0e2e0000-0000-4000-8000-0000000004' || a.numero)::uuid AS version_id,
       ('0e2e0000-0000-4000-8000-0000000005' || a.numero)::uuid AS alerte_id,
       'E2E-POSTE-00' || a.numero AS ident
FROM e2e_alerte_poste a
WHERE a.numero = :'numero';

DO $$
BEGIN
  IF (SELECT count(*) FROM e2e_choisie) <> 1 THEN
    RAISE EXCEPTION 'alerte de test inconnue';
  END IF;
END $$;

INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                    collected_at, updated_at, content_hash, affected, current_version_id)
SELECT signal_id, 'OSV', ident, titre, 'Avis fictif des parcours du client desktop.', gravite, now(), now(),
       now(), now(), md5(ident) || md5(titre), '[]', version_id
FROM e2e_choisie
ON CONFLICT DO NOTHING;

INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
SELECT version_id, signal_id, 1, md5(ident) || md5(titre), now(), now(), '{}'
FROM e2e_choisie
ON CONFLICT DO NOTHING;

-- Contenu d'une alerte immédiate (ContenuAlerte) : un élément, ses projets, sa source et son lien.
INSERT INTO alerte (id, nature, compte_id, canal, signal_id, fin_periode, titre, gravite, contenu,
                    resume_ia, etat_resume_ia, etat, tentatives, cree_le)
SELECT alerte_id, 'IMMEDIATE', :'compte', 'DESKTOP', signal_id, NULL, titre, gravite,
       jsonb_build_object(
         'elements', jsonb_build_array(jsonb_build_object(
           'signalId', signal_id,
           'titre', titre,
           'gravite', gravite,
           -- identifiant de projet fictif, stable, dérivé du nom
           'projets', (SELECT jsonb_agg(jsonb_build_object('id', md5(p)::uuid, 'nom', p, 'niveau', 'EXACT')
                                        ORDER BY o)
                       FROM unnest(projets) WITH ORDINALITY AS u(p, o)),
           'sources', jsonb_build_array(jsonb_build_object('nom', 'OSV', 'identifiant', ident, 'url', NULL)),
           'lien', 'http://localhost:5173/#/signaux/' || signal_id)),
         'resumeIa', NULL, 'etatResumeIa', 'SANS_OBJET', 'mentionResumeIa', NULL),
       NULL, 'SANS_OBJET', 'EN_ATTENTE', 0, now()
FROM e2e_choisie
ON CONFLICT (id) DO UPDATE SET etat = 'EN_ATTENTE', envoye_le = NULL, cree_le = now();

COMMIT;
