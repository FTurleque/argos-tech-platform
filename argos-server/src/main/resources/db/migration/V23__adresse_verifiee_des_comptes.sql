-- Adresse e-mail des comptes : seule l'adresse que le fournisseur d'identité déclare vérifiée est enregistrée
-- (ALR-08, changement durcir-autorisations-et-debits).
--
-- L'application tient compte.email à jour à chaque connexion : l'adresse vérifiée normalisée du dernier jeton, ou
-- aucune. Elle peut donc modifier cette colonne de son seul compte (sujet du contexte courant).
GRANT UPDATE (email) ON compte TO argos_app;
CREATE POLICY compte_adresse_verifiee ON compte FOR UPDATE TO argos_app
    USING (sujet_oidc = argos_sujet_courant())
    WITH CHECK (sujet_oidc = argos_sujet_courant());

-- Les adresses déjà enregistrées ont pu venir d'un jeton non vérifié : elles sont effacées. Aucun e-mail ne part
-- vers un compte tant qu'il ne s'est pas reconnecté ; sa première connexion suivante réécrit l'adresse si le jeton
-- la déclare vérifiée. Les alertes restent visibles dans Argos et sur le poste. Non réversible automatiquement
-- (ADR-0032) : les adresses se reconstituent à la connexion.
UPDATE compte SET email = NULL;
