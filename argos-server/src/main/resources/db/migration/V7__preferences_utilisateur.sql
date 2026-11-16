-- Module workspace : préférences d'affichage et de veille d'un compte.
-- Changement OpenSpec « personnaliser-profils-et-affichage » (AFF-01, AFF-03, AFF-04, PRF-01).
--
-- compte_id      : compte Argos (gerer-comptes-et-acces), identifiant stable relié au sujet OIDC.
-- mise_en_page   : identifiant de mise en page du catalogue ; NULL = jamais choisie
--                  (la mise en page « classique » s'applique, AFF-04).
-- profils_actifs : identifiants de profils du catalogue ; NULL = jamais choisis.
--                  Un ensemble enregistré n'est jamais vide (INV-1, PRF-01).
--
-- Les valeurs ne sont pas confrontées au catalogue en base : un retrait ultérieur
-- ne casse pas la ligne, et le défaut s'applique à la lecture (INV-2).
-- Aucune colonne ne porte de droit (INV-3, PRF-05).
CREATE TABLE workspace_preference_utilisateur (
    compte_id      uuid         PRIMARY KEY REFERENCES compte (id) ON DELETE CASCADE,
    mise_en_page   varchar(64),
    profils_actifs varchar(64)[],
    modifie_le     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT workspace_preference_au_moins_un_profil
        CHECK (profils_actifs IS NULL OR cardinality(profils_actifs) >= 1)
);

COMMENT ON TABLE workspace_preference_utilisateur IS
    'Préférences d''un compte (mise en page, profils de veille actifs). Jamais utilisées pour une autorisation.';

-- Garde-fou en base (ADR-0016) : sous argos_app, un compte ne lit et n'écrit que ses préférences.
GRANT SELECT, INSERT, UPDATE ON workspace_preference_utilisateur TO argos_app;
ALTER TABLE workspace_preference_utilisateur ENABLE ROW LEVEL SECURITY;
CREATE POLICY workspace_preference_compte ON workspace_preference_utilisateur FOR ALL TO argos_app
    USING (compte_id = argos_compte_courant())
    WITH CHECK (compte_id = argos_compte_courant());
