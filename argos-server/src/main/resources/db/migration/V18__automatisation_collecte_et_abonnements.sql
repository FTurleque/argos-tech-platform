-- Automatisation de la collecte et état des abonnements (changement OpenSpec automatiser-chaine-veille,
-- volet A, tâche 1.1). Exigences : COL-10 (empreinte du contenu normalisé), ABO-07 (abonnement actif ou
-- suspendu), ABO-08 (suppression distincte). Décisions J2 et J7 du porteur du projet (2026-11-19).

-- 1. Empreinte du contenu normalisé (COL-10, proposition P-2). Les colonnes sont nullables : les signaux
--    enregistrés avant cette migration reçoivent leur empreinte au premier passage de la collecte, sans
--    nouvelle version (COL-10-S4) ; la normalisation est faite en Java et n'est pas dupliquée en SQL.
--    L'empreinte du contenu brut (content_hash) reste enregistrée pour l'audit.
ALTER TABLE signal ADD COLUMN normalized_hash char(64);
ALTER TABLE signal_version ADD COLUMN normalized_hash char(64);

-- Invariant INV-A1 : deux versions d'un signal n'ont jamais la même empreinte normalisée ; la base
-- arbitre les collectes concurrentes (COL-03-S3).
CREATE UNIQUE INDEX signal_version_normalized_uk ON signal_version (signal_id, normalized_hash)
    WHERE normalized_hash IS NOT NULL;

-- 2. État d'un abonnement : ACTIVE ou SUSPENDED (ABO-07). Les abonnements INACTIVE (retrait d'un
--    PERSONAL par son propriétaire) deviennent SUSPENDED, critères, canal, fréquence et retraits intacts
--    (invariant INV-A2).
ALTER TABLE subscription DROP CONSTRAINT subscription_state_check;
UPDATE subscription SET state = 'SUSPENDED' WHERE state = 'INACTIVE';
ALTER TABLE subscription ADD CONSTRAINT subscription_state_check CHECK (state IN ('ACTIVE', 'SUSPENDED'));

-- 3. Garde-fou en base (ADR-0016) : sous argos_app, seuls le propriétaire d'un PERSONAL et un
--    administrateur de l'espace d'un TEAM changent l'état d'un abonnement (suspension, réactivation) ou
--    le suppriment (ABO-07, ABO-08, proposition P-8). La suppression retire les retraits des membres par
--    la clé étrangère ON DELETE CASCADE de subscription_opt_out.
GRANT DELETE ON subscription TO argos_app;

DROP POLICY subscription_desactivation ON subscription;
CREATE POLICY subscription_mise_a_jour ON subscription FOR UPDATE TO argos_app
    USING ((scope = 'PERSONAL' AND owner_user_id = argos_compte_courant())
           OR (scope = 'TEAM' AND argos_espace_lisible(owner_workspace_id)
               AND argos_role_dans(owner_workspace_id) = 'ADMINISTRATEUR'))
    WITH CHECK ((scope = 'PERSONAL' AND owner_user_id = argos_compte_courant())
                OR (scope = 'TEAM' AND argos_espace_lisible(owner_workspace_id)
                    AND argos_role_dans(owner_workspace_id) = 'ADMINISTRATEUR'));
CREATE POLICY subscription_suppression ON subscription FOR DELETE TO argos_app
    USING ((scope = 'PERSONAL' AND owner_user_id = argos_compte_courant())
           OR (scope = 'TEAM' AND argos_espace_lisible(owner_workspace_id)
               AND argos_role_dans(owner_workspace_id) = 'ADMINISTRATEUR'));
