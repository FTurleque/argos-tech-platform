-- Collecte des avis de sécurité (changement OpenSpec formaliser-chaine-securite-poc, tâche 2.1).
-- Exigences : COL-02 (provenance), COL-03 (idempotence, historique), COL-05 (curseur),
-- COL-06 / COL-07 (tentatives, état de source), COL-08 (zone d'échec). Conception : DC-2, DC-3.

CREATE TABLE source (
    id                   varchar(32)  PRIMARY KEY,
    label                varchar(200) NOT NULL,
    state                varchar(16)  NOT NULL DEFAULT 'NOMINAL'
                         CHECK (state IN ('NOMINAL', 'FAILING', 'DEGRADED')),
    consecutive_failures integer      NOT NULL DEFAULT 0,
    last_success_at      timestamptz,
    last_failure_at      timestamptz,
    last_error           varchar(1000)
);

INSERT INTO source (id, label) VALUES ('OSV', 'OSV — Open Source Vulnerabilities');

-- Position de synchronisation : n'avance qu'avec l'enregistrement des avis (INV-D5).
CREATE TABLE sync_cursor (
    source_id  varchar(32)  PRIMARY KEY REFERENCES source (id),
    position   varchar(300) NOT NULL,
    updated_at timestamptz  NOT NULL
);

-- Traitement de synchronisation (DC-3).
CREATE TABLE sync_run (
    id              uuid        PRIMARY KEY,
    source_id       varchar(32) NOT NULL REFERENCES source (id),
    state           varchar(16) NOT NULL CHECK (state IN ('RUNNING', 'RETRYABLE', 'SUCCEEDED', 'FAILED')),
    attempts        integer     NOT NULL DEFAULT 0,
    started_at      timestamptz NOT NULL,
    heartbeat_at    timestamptz NOT NULL,
    next_attempt_at timestamptz,
    finished_at     timestamptz,
    error_kind      varchar(16) CHECK (error_kind IN ('TRANSIENT', 'PERMANENT', 'EXHAUSTED')),
    last_error      varchar(1000)
);
CREATE INDEX sync_run_source_state ON sync_run (source_id, state);
-- Au plus un traitement actif par source.
CREATE UNIQUE INDEX sync_run_one_running ON sync_run (source_id) WHERE state = 'RUNNING';

-- Tentatives en échec et erreurs (COL-06-S1).
CREATE TABLE sync_attempt (
    id                  uuid        PRIMARY KEY,
    run_id              uuid        NOT NULL REFERENCES sync_run (id),
    attempt_no          integer     NOT NULL,
    occurred_at         timestamptz NOT NULL,
    http_status         integer,
    error_kind          varchar(16) NOT NULL CHECK (error_kind IN ('TRANSIENT', 'PERMANENT')),
    error               varchar(1000),
    retry_after_seconds bigint
);

-- Zone d'échec : avis non normalisables, avec motif et provenance (COL-08).
CREATE TABLE ingestion_failure (
    id          uuid          PRIMARY KEY,
    source_id   varchar(32)   NOT NULL REFERENCES source (id),
    external_id varchar(200),
    reason      varchar(1000) NOT NULL,
    raw_content text          NOT NULL,
    received_at timestamptz   NOT NULL,
    run_id      uuid          REFERENCES sync_run (id)
);

-- Événements d'exploitation (COL-07-S1).
CREATE TABLE ops_event (
    id          uuid        PRIMARY KEY,
    kind        varchar(40) NOT NULL,
    source_id   varchar(32) REFERENCES source (id),
    detail      varchar(1000),
    occurred_at timestamptz NOT NULL
);

-- Signal de sécurité normalisé : un seul par (source, identifiant externe) (INV-D1).
CREATE TABLE signal (
    id                 uuid         PRIMARY KEY,
    source             varchar(32)  NOT NULL REFERENCES source (id),
    external_id        varchar(200) NOT NULL,
    title              varchar(1000),
    summary            text,
    severity           varchar(300),
    published_at       timestamptz,
    modified_at        timestamptz  NOT NULL,
    collected_at       timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    content_hash       char(64)     NOT NULL,
    affected           jsonb        NOT NULL,
    current_version_id uuid         NOT NULL,
    CONSTRAINT signal_source_external_uk UNIQUE (source, external_id)
);

-- Historique des versions de l'avis, contenu brut compris (COL-02, COL-03-S2, INV-D2).
CREATE TABLE signal_version (
    id                 uuid        PRIMARY KEY,
    signal_id          uuid        NOT NULL REFERENCES signal (id),
    version_no         integer     NOT NULL,
    content_hash       char(64)    NOT NULL,
    source_modified_at timestamptz NOT NULL,
    collected_at       timestamptz NOT NULL,
    raw_content        text        NOT NULL,
    CONSTRAINT signal_version_hash_uk UNIQUE (signal_id, content_hash),
    CONSTRAINT signal_version_no_uk UNIQUE (signal_id, version_no)
);

ALTER TABLE signal ADD CONSTRAINT signal_current_version_fk
    FOREIGN KEY (current_version_id) REFERENCES signal_version (id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE signal_alias (
    signal_id uuid         NOT NULL REFERENCES signal (id) ON DELETE CASCADE,
    alias     varchar(200) NOT NULL,
    PRIMARY KEY (signal_id, alias)
);
CREATE INDEX signal_alias_alias ON signal_alias (alias);

-- Paquets affectés indexés par clé de paquet (PURL sans version), pour la corrélation.
CREATE TABLE signal_package (
    signal_id   uuid         NOT NULL REFERENCES signal (id) ON DELETE CASCADE,
    package_key varchar(500) NOT NULL,
    ecosystem   varchar(100),
    PRIMARY KEY (signal_id, package_key)
);
CREATE INDEX signal_package_key ON signal_package (package_key);

-- Enrichissements par les sources secondaires, rattachés au signal existant (COL-04).
CREATE TABLE enrichment (
    signal_id  uuid         NOT NULL REFERENCES signal (id),
    source     varchar(16)  NOT NULL CHECK (source IN ('NVD', 'KEV', 'EPSS', 'CERTFR')),
    reference  varchar(200) NOT NULL,
    data       jsonb        NOT NULL,
    fetched_at timestamptz  NOT NULL,
    PRIMARY KEY (signal_id, source, reference)
);
