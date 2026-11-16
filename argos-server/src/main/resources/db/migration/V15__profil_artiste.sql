-- Profil de veille « artiste » (changement OpenSpec ajouter-profil-artiste, ADR-0024).
-- Exigences : ART-02 (disciplines et territoires suivis), ART-03 (rapprochement), ART-04 (rappels),
-- ART-05 (sources vérifiées). Protocole de cloisonnement : migration V2 (rôle argos_app, compte courant).

-- ---------------------------------------------------------------------------
-- Module workspace (paquetage suivi) : objets suivis par un compte pour un profil de veille.
-- Une ligne par compte, profil et type d'objet (par exemple discipline, territoire) ; les valeurs sont
-- des identifiants des listes du manifeste, confrontées au catalogue par l'API avant l'écriture.
-- Aucune colonne ne porte de droit (PRF-05) : un suivi choisit ce que l'utilisateur veut voir.
CREATE TABLE suivi_profil (
    compte_id  uuid          NOT NULL REFERENCES compte (id) ON DELETE CASCADE,
    profil     varchar(64)   NOT NULL,
    type_objet varchar(64)   NOT NULL,
    valeurs    varchar(64)[] NOT NULL CHECK (cardinality(valeurs) BETWEEN 1 AND 50),
    modifie_le timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (compte_id, profil, type_objet)
);

-- Jours de rappel avant une date limite, choisis par le compte pour un profil (Q-4 : J-30, J-7, J-1
-- par défaut, appliqués tant qu'aucune ligne n'existe).
CREATE TABLE suivi_rappels (
    compte_id  uuid        NOT NULL REFERENCES compte (id) ON DELETE CASCADE,
    profil     varchar(64) NOT NULL,
    jours      integer[]   NOT NULL CHECK (cardinality(jours) BETWEEN 1 AND 10),
    modifie_le timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (compte_id, profil)
);

-- Garde-fou en base (ADR-0016) : sous argos_app, un compte ne lit et n'écrit que ses propres suivis.
GRANT SELECT, INSERT, UPDATE, DELETE ON suivi_profil, suivi_rappels TO argos_app;
ALTER TABLE suivi_profil ENABLE ROW LEVEL SECURITY;
CREATE POLICY suivi_profil_compte ON suivi_profil FOR ALL TO argos_app
    USING (compte_id = argos_compte_courant())
    WITH CHECK (compte_id = argos_compte_courant());
ALTER TABLE suivi_rappels ENABLE ROW LEVEL SECURITY;
CREATE POLICY suivi_rappels_compte ON suivi_rappels FOR ALL TO argos_app
    USING (compte_id = argos_compte_courant())
    WITH CHECK (compte_id = argos_compte_courant());

-- ---------------------------------------------------------------------------
-- Module signal (paquetage opportunite) : sources d'un profil et opportunités collectées.
-- Données de la plateforme, publiques, sans espace : comme les signaux, elles ne sont pas cloisonnées.
-- Les sources sont déclarées et vérifiées par l'administrateur plateforme (contrôle dans l'API).
--
-- Une source sans conditions d'usage enregistrées reste à l'état CONDITIONS_A_VERIFIER et n'est
-- jamais collectée (ART-05).
CREATE TABLE source_profil (
    id                      uuid          PRIMARY KEY,
    profil                  varchar(64)   NOT NULL,
    type                    varchar(16)   NOT NULL CHECK (type IN ('DATA_CULTURE', 'FLUX')),
    adresse                 varchar(2000) NOT NULL,
    libelle                 varchar(200)  NOT NULL,
    rubrique                varchar(64)   NOT NULL,
    disciplines             varchar(64)[] NOT NULL DEFAULT '{}',
    territoires             varchar(64)[] NOT NULL DEFAULT '{}',
    licence                 varchar(200),
    conditions_usage        varchar(2000),
    conditions_url          varchar(2000),
    conditions_verifiees_par uuid         REFERENCES compte (id) ON DELETE SET NULL,
    conditions_verifiees_le timestamptz,
    etat                    varchar(32)   NOT NULL DEFAULT 'CONDITIONS_A_VERIFIER'
                            CHECK (etat IN ('CONDITIONS_A_VERIFIER', 'ACTIVE', 'EN_ERREUR')),
    derniere_erreur         varchar(1000),
    derniere_collecte_le    timestamptz,
    cree_par                uuid          REFERENCES compte (id) ON DELETE SET NULL,
    cree_le                 timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT source_profil_adresse_uk UNIQUE (profil, adresse),
    -- Conditions d'usage : licence, texte et date de vérification enregistrés ensemble.
    CONSTRAINT source_profil_conditions_ck CHECK (
        (licence IS NULL AND conditions_usage IS NULL AND conditions_verifiees_le IS NULL)
        OR (licence IS NOT NULL AND conditions_usage IS NOT NULL AND conditions_verifiees_le IS NOT NULL)),
    -- Une source n'est active ou en erreur qu'une fois ses conditions vérifiées.
    CONSTRAINT source_profil_etat_ck CHECK (etat = 'CONDITIONS_A_VERIFIER' OR conditions_verifiees_le IS NOT NULL)
);

-- Opportunité (appel, aide, information, événement) collectée par une source vérifiée. Les textes sont
-- du texte brut extrait du contenu externe, conservé comme donnée non fiable ; le lien est une adresse
-- http(s) ou absent. Une date limite absente signifie « date à confirmer ».
CREATE TABLE opportunite (
    id          uuid          PRIMARY KEY,
    source_id   uuid          NOT NULL REFERENCES source_profil (id) ON DELETE CASCADE,
    empreinte   varchar(64)   NOT NULL,
    rubrique    varchar(64)   NOT NULL,
    titre       varchar(1000) NOT NULL,
    organisme   varchar(300),
    criteres    text          NOT NULL DEFAULT '',
    lien        varchar(2000),
    disciplines varchar(64)[] NOT NULL DEFAULT '{}',
    territoires varchar(64)[] NOT NULL DEFAULT '{}',
    date_limite date,
    recu_le     timestamptz   NOT NULL,
    modifie_le  timestamptz   NOT NULL,
    CONSTRAINT opportunite_source_empreinte_uk UNIQUE (source_id, empreinte)
);
CREATE INDEX opportunite_date_limite ON opportunite (date_limite);
CREATE INDEX opportunite_disciplines ON opportunite USING gin (disciplines);

GRANT SELECT ON opportunite TO argos_app;
