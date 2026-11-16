-- Module workspace : préférences d'affichage et de veille d'un utilisateur.
-- Changement OpenSpec « personnaliser-profils-et-affichage » (AFF-01, AFF-03, AFF-04, PRF-01).
--
-- La version datée évite une collision de numéro avec les migrations des changements
-- menés en parallèle.
--
-- utilisateur    : identifiant stable de l'utilisateur authentifié (sujet de son identité).
--                  Pas de clé étrangère tant que la table des comptes n'existe pas.
-- mise_en_page   : identifiant de mise en page du catalogue ; NULL = jamais choisie
--                  (la mise en page « classique » s'applique, AFF-04).
-- profils_actifs : identifiants de profils du catalogue ; NULL = jamais choisis.
--                  Un ensemble enregistré n'est jamais vide (INV-1, PRF-01).
--
-- Les valeurs ne sont pas confrontées au catalogue en base : un retrait ultérieur
-- ne casse pas la ligne, et le défaut s'applique à la lecture (INV-2).
-- Aucune colonne ne porte de droit (INV-3, PRF-05).
CREATE TABLE workspace_preference_utilisateur (
    utilisateur    varchar(255) PRIMARY KEY,
    mise_en_page   varchar(64),
    profils_actifs varchar(64)[],
    modifie_le     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT workspace_preference_au_moins_un_profil
        CHECK (profils_actifs IS NULL OR cardinality(profils_actifs) >= 1)
);

COMMENT ON TABLE workspace_preference_utilisateur IS
    'Préférences d''un utilisateur (mise en page, profils de veille actifs). Jamais utilisées pour une autorisation.';
