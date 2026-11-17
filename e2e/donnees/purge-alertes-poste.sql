-- Remise à zéro des alertes DESKTOP d'un compte avant un parcours du client desktop
-- (notifier-sur-le-poste) : chaque parcours part d'un compte sans alerte en attente.
-- Variable : compte.
DELETE FROM alerte WHERE compte_id = :'compte' AND canal = 'DESKTOP';
