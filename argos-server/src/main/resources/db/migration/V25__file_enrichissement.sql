-- File d'enrichissement des signaux (changement rendre-ingestion-asynchrone, COL-04, ADR-0039).
--
-- L'enregistrement d'un signal y place le signal ; la tâche planifiée vulnintel-enrichissement interroge les sources
-- secondaires hors de toute transaction, puis retire le signal quand toutes ont répondu, ou le reporte à
-- prochain_essai_le avec un délai croissant. Table technique de vulnintel, sans donnée personnelle ni RLS, comme
-- enrichment ; argos_technique y reçoit ses droits par les privilèges par défaut (V22).
CREATE TABLE enrichissement_attente (
    signal_id         uuid        PRIMARY KEY REFERENCES signal (id) ON DELETE CASCADE,
    demande_le        timestamptz NOT NULL,
    tentatives        integer     NOT NULL DEFAULT 0 CHECK (tentatives >= 0),
    prochain_essai_le timestamptz NOT NULL,
    derniere_erreur   varchar(1000)
);
CREATE INDEX enrichissement_attente_echeance ON enrichissement_attente (prochain_essai_le);
