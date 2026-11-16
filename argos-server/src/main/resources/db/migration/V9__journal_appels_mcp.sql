-- Module audit : journal des appels d'outils MCP (changement OpenSpec exposer-serveur-mcp, MCP-06).
--
-- Une ligne par appel d'outil : compte (ou, à défaut, sujet OIDC), outil, paramètres bornés,
-- durée et issue. Le résultat renvoyé n'est jamais recopié. Les refus d'accès et de limite de
-- débit sont aussi inscrits dans le journal unique des refus (refus_acces, V2).
--
-- Comme refus_acces, la table n'est écrite et lue que par le module audit, sous l'utilisateur de
-- connexion : le rôle applicatif argos_app n'y a aucun droit (ADR-0016, moindre privilège).
CREATE TABLE mcp_appel (
    id          bigserial    PRIMARY KEY,
    compte_id   uuid,
    sujet_oidc  text,
    outil       varchar(100) NOT NULL,
    parametres  jsonb        NOT NULL,
    duree_ms    integer      NOT NULL CHECK (duree_ms >= 0),
    issue       varchar(32)  NOT NULL CHECK (issue IN ('SUCCES', 'PARAMETRES_INVALIDES', 'REFUS_ACCES',
                                                       'REFUS_LIMITE', 'ERREUR')),
    horodatage  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX mcp_appel_horodatage_idx ON mcp_appel (horodatage);
CREATE INDEX mcp_appel_compte_idx ON mcp_appel (compte_id, horodatage);
