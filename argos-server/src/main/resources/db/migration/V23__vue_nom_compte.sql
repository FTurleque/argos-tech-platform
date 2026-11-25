-- Vue publiée par le module workspace, propriétaire de la table compte (changement OpenSpec
-- extraire-noyau-commun, tâche 5.2 ; ADR-0037, ADR-0032). Le module impact lit le nom affiché de l'acteur
-- d'une transition d'impact sans dépendre du module workspace.
--
-- La vue s'exécute avec les droits de l'appelant (security_invoker) : la politique compte_lecture de la
-- migration V2 s'applique donc au rôle argos_app comme à une lecture directe de la table. Seules les colonnes
-- id et nom_affiche sont exposées. Aucune donnée ni politique RLS ne change. Retour arrière :
-- DROP VIEW compte_nom_public (arc42 §8.7).
CREATE VIEW compte_nom_public WITH (security_invoker = true) AS
    SELECT id, nom_affiche FROM compte;

GRANT SELECT ON compte_nom_public TO argos_app;
