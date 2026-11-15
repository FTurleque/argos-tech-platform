-- ARGOS — migration initiale du socle.
-- Aucune table métier ici : les tables SOURCE, SIGNAL, INVENTORY, PROJECT_IMPACT,
-- SUBSCRIPTION, etc. sont créées par les tâches 2.1, 3.1, 4.1 et 5.1 du changement
-- OpenSpec « formaliser-chaine-securite-poc », une fois les arbitrages levés.
-- Cette migration vérifie seulement que la chaîne Flyway fonctionne.
SELECT 1;
