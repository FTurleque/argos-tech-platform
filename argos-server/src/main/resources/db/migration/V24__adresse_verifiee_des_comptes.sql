-- Adresse e-mail des comptes (ALR-08) : seule l'adresse que le fournisseur d'identité déclare vérifiée est
-- enregistrée, normalisée, ou aucune ; l'application la tient à jour à chaque connexion et peut donc modifier
-- cette colonne de son seul compte (sujet du contexte courant).
GRANT UPDATE (email) ON compte TO argos_app;
CREATE POLICY compte_adresse_verifiee ON compte FOR UPDATE TO argos_app
    USING (sujet_oidc = argos_sujet_courant())
    WITH CHECK (sujet_oidc = argos_sujet_courant());

-- Adresses enregistrées sans vérification : effacées. Un compte reçoit des e-mails après sa première connexion
-- dont le jeton déclare son adresse vérifiée ; ses alertes restent visibles dans Argos et sur le poste.
-- Non réversible automatiquement (ADR-0032) : les adresses se reconstituent à la connexion.
UPDATE compte SET email = NULL WHERE email IS NOT NULL;
