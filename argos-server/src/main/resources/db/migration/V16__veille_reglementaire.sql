-- Veille réglementaire (changement OpenSpec suivre-veille-reglementaire, ADR-0018 ; mise à jour proposée
-- par ADR-0030). Exigences : REG-01 à REG-08. Conception : DC-1, DC-2.
-- Protocole de cloisonnement : migration V2 (rôle argos_app, compte et espace courants).

-- ---------------------------------------------------------------------------
-- Module catalog (paquetage dictionnaire) : domaines métier, experts et dictionnaire de chaque espace
-- (décision D1). Aucun dictionnaire type n'est fourni (Q-4) : chaque espace construit le sien.
-- ---------------------------------------------------------------------------
CREATE TABLE domaine_metier (
    espace_id   uuid         NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    code        varchar(64)  NOT NULL CHECK (code ~ '^[a-z0-9][a-z0-9-]{0,63}$'),
    libelle     varchar(200) NOT NULL CHECK (length(btrim(libelle)) > 0),
    modifie_par uuid         NOT NULL REFERENCES compte (id),
    modifie_le  timestamptz  NOT NULL,
    PRIMARY KEY (espace_id, code)
);

-- Expert métier d'un domaine, désigné par un administrateur de l'espace parmi ses membres. Le statut
-- d'expert n'est effectif que tant que le compte reste membre CONTRIBUTEUR ou ADMINISTRATEUR de l'espace
-- (fonction argos_expert_domaine) : une révocation ou une rétrogradation le retire aussitôt.
CREATE TABLE expert_domaine (
    espace_id   uuid        NOT NULL,
    domaine     varchar(64) NOT NULL,
    compte_id   uuid        NOT NULL REFERENCES compte (id) ON DELETE CASCADE,
    modifie_par uuid        NOT NULL REFERENCES compte (id),
    modifie_le  timestamptz NOT NULL,
    PRIMARY KEY (espace_id, domaine, compte_id),
    FOREIGN KEY (espace_id, domaine) REFERENCES domaine_metier (espace_id, code) ON DELETE CASCADE
);

-- Règle déterministe de rattachement d'un texte à un domaine (REG-02) : nature du texte, ministère,
-- préfixe NOR, code modifié (libellé ou identifiant LEGITEXT) ou mot-clé du titre.
CREATE TABLE regle_dictionnaire (
    espace_id   uuid         NOT NULL,
    domaine     varchar(64)  NOT NULL,
    type        varchar(16)  NOT NULL CHECK (type IN ('NATURE', 'MINISTERE', 'NOR', 'CODE', 'MOT_CLE')),
    valeur      varchar(200) NOT NULL CHECK (length(btrim(valeur)) > 0),
    modifie_par uuid         NOT NULL REFERENCES compte (id),
    modifie_le  timestamptz  NOT NULL,
    PRIMARY KEY (espace_id, domaine, type, valeur),
    FOREIGN KEY (espace_id, domaine) REFERENCES domaine_metier (espace_id, code) ON DELETE CASCADE
);

-- Jours de rappel avant une entrée en vigueur, réglés par un administrateur de l'espace (Q-2 : J-90,
-- J-30 et J-7 par défaut, argos.reglementaire.rappels-jours, appliqués tant qu'aucune ligne n'existe).
CREATE TABLE reg_parametres (
    espace_id     uuid        PRIMARY KEY REFERENCES espace (id) ON DELETE CASCADE,
    rappels_jours integer[]   NOT NULL CHECK (cardinality(rappels_jours) BETWEEN 1 AND 10
                                              AND 0 <= ALL (rappels_jours) AND 365 >= ALL (rappels_jours)),
    modifie_par   uuid        NOT NULL REFERENCES compte (id),
    modifie_le    timestamptz NOT NULL
);

-- ---------------------------------------------------------------------------
-- Module reg-intel : textes officiels collectés (données publiques de la plateforme, sans espace)
-- Source : jeux de données JORF de la DILA, Licence Ouverte / Open Licence (Q-1) ; l'API PISTE de
-- Légifrance n'est pas utilisée.
-- ---------------------------------------------------------------------------
-- Un texte est enregistré une seule fois par identifiant officiel (REG-01-S1). Les textes sont des
-- données externes non fiables : texte brut, longueurs bornées.
CREATE TABLE texte_officiel (
    id                uuid          PRIMARY KEY,
    identifiant       varchar(32)   NOT NULL UNIQUE CHECK (identifiant ~ '^[A-Z]{4}TEXT[0-9]{12}$'),
    source            varchar(16)   NOT NULL CHECK (source IN ('JORF')),
    nature            varchar(64),
    numero            varchar(64),
    nor               varchar(16),
    ministere         varchar(500),
    titre             varchar(2000) NOT NULL,
    date_publication  date          NOT NULL,
    date_signature    date,
    -- Date d'entrée en vigueur extraite du texte, absente : « date à confirmer » (REG-03-S2).
    date_effet        date,
    motif_date_effet  varchar(300),
    codes_ids         varchar(32)[] NOT NULL DEFAULT '{}',
    codes_libelles    varchar(300)[] NOT NULL DEFAULT '{}',
    articles_modifies varchar(300)[] NOT NULL DEFAULT '{}',
    extrait           text          NOT NULL DEFAULT '' CHECK (length(extrait) <= 20000),
    archive           varchar(200)  NOT NULL,
    collecte_le       timestamptz   NOT NULL,
    CONSTRAINT texte_officiel_codes_ck CHECK (cardinality(codes_ids) = cardinality(codes_libelles))
);
CREATE INDEX texte_officiel_publication ON texte_officiel (date_publication);

-- État de la source (observabilité) : DEGRADE après un échec de collecte, NOMINAL au retour (REG-01-S2).
CREATE TABLE reg_source (
    id                 varchar(16)   PRIMARY KEY,
    libelle            varchar(200)  NOT NULL,
    etat               varchar(16)   NOT NULL DEFAULT 'NOMINAL' CHECK (etat IN ('NOMINAL', 'DEGRADE')),
    echecs_consecutifs integer       NOT NULL DEFAULT 0,
    dernier_succes_le  timestamptz,
    dernier_echec_le   timestamptz,
    derniere_erreur    varchar(1000)
);
INSERT INTO reg_source (id, libelle) VALUES ('JORF', 'Journal officiel — jeux de données JORF de la DILA (Licence Ouverte)');

-- Jour de publication collecté ou en échec : un jour en échec est repris à la collecte suivante.
CREATE TABLE reg_collecte_jour (
    jour            date          PRIMARY KEY,
    etat            varchar(16)   NOT NULL CHECK (etat IN ('COLLECTE', 'EN_ECHEC')),
    tentatives      integer       NOT NULL DEFAULT 1,
    archives        integer       NOT NULL DEFAULT 0,
    textes          integer       NOT NULL DEFAULT 0,
    derniere_erreur varchar(1000),
    modifie_le      timestamptz   NOT NULL
);

-- ---------------------------------------------------------------------------
-- Module reg-intel : fiches d'analyse par espace, domaines rattachés et historique (REG-02 à REG-08)
-- ---------------------------------------------------------------------------
CREATE TABLE fiche_reglementaire (
    id                 uuid          PRIMARY KEY,
    texte_id           uuid          NOT NULL REFERENCES texte_officiel (id) ON DELETE CASCADE,
    espace_id          uuid          NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    etat_validation    varchar(24)   NOT NULL DEFAULT 'EN_ATTENTE_VALIDATION'
                       CHECK (etat_validation IN ('EN_ATTENTE_VALIDATION', 'VALIDEE', 'REJETEE')),
    etat_suivi         varchar(16)   NOT NULL DEFAULT 'NOUVEAU'
                       CHECK (etat_suivi IN ('NOUVEAU', 'EN_ANALYSE', 'SANS_IMPACT', 'IMPACT_PRODUIT', 'SPECIFIE', 'LIVRE')),
    resume             varchar(2000),
    resume_origine     varchar(8)    NOT NULL CHECK (resume_origine IN ('IA', 'EXPERT', 'AUCUN')),
    date_effet         date,
    date_effet_origine varchar(8)    CHECK (date_effet_origine IN ('TEXTE', 'EXPERT')),
    -- Date d'effet proposée par l'IA : affichée comme proposition, jamais retenue sans l'expert.
    date_effet_ia      date,
    articles_modifies  varchar(300)[] NOT NULL DEFAULT '{}',
    demande_url        varchar(500),
    demande_forge      varchar(8)    CHECK (demande_forge IN ('GITHUB', 'GITLAB')),
    demande_etat       varchar(16),
    decide_par         uuid          REFERENCES compte (id) ON DELETE SET NULL,
    decide_le          timestamptz,
    cree_le            timestamptz   NOT NULL,
    modifie_par        uuid          REFERENCES compte (id) ON DELETE SET NULL,
    modifie_le         timestamptz   NOT NULL,
    CONSTRAINT fiche_reglementaire_texte_espace_uk UNIQUE (texte_id, espace_id),
    CONSTRAINT fiche_reglementaire_id_espace_uk UNIQUE (id, espace_id),
    CONSTRAINT fiche_reglementaire_resume_ck CHECK ((resume_origine = 'AUCUN') = (resume IS NULL)),
    CONSTRAINT fiche_reglementaire_date_ck CHECK ((date_effet IS NULL) = (date_effet_origine IS NULL)),
    CONSTRAINT fiche_reglementaire_decision_ck CHECK (etat_validation = 'EN_ATTENTE_VALIDATION' OR decide_le IS NOT NULL),
    -- Le suivi ne quitte l'état NOUVEAU qu'après la validation de la fiche (DC-2).
    CONSTRAINT fiche_reglementaire_suivi_ck CHECK (etat_suivi = 'NOUVEAU' OR etat_validation = 'VALIDEE'),
    CONSTRAINT fiche_reglementaire_demande_ck CHECK ((demande_url IS NULL) = (demande_forge IS NULL))
);
CREATE INDEX fiche_reglementaire_espace ON fiche_reglementaire (espace_id, etat_validation);
CREATE INDEX fiche_reglementaire_effet ON fiche_reglementaire (date_effet) WHERE etat_validation = 'VALIDEE';

-- Domaine rattaché à une fiche : par une règle du dictionnaire, proposé par l'IA avec son niveau de
-- confiance (REG-02-S2), ou choisi par l'expert. La validation de la fiche confirme ses domaines.
CREATE TABLE fiche_domaine (
    fiche_id  uuid         NOT NULL,
    espace_id uuid         NOT NULL,
    domaine   varchar(64)  NOT NULL,
    origine   varchar(8)   NOT NULL CHECK (origine IN ('REGLE', 'IA', 'EXPERT')),
    confiance integer      CHECK (confiance BETWEEN 0 AND 100),
    motif     varchar(500) NOT NULL,
    PRIMARY KEY (fiche_id, domaine),
    FOREIGN KEY (fiche_id, espace_id) REFERENCES fiche_reglementaire (id, espace_id) ON DELETE CASCADE,
    FOREIGN KEY (espace_id, domaine) REFERENCES domaine_metier (espace_id, code) ON DELETE CASCADE,
    CONSTRAINT fiche_domaine_confiance_ck CHECK ((origine = 'IA') = (confiance IS NOT NULL))
);

-- Historique d'une fiche : préparation, corrections (valeur avant et après), décisions, transitions et
-- demande d'évolution associée (REG-05-S1). Une ligne n'est jamais modifiée.
CREATE TABLE fiche_historique (
    id        uuid        PRIMARY KEY,
    fiche_id  uuid        NOT NULL,
    espace_id uuid        NOT NULL,
    action    varchar(24) NOT NULL
              CHECK (action IN ('PREPARATION', 'CORRECTION', 'VALIDATION', 'REJET', 'TRANSITION', 'DEMANDE_EVOLUTION')),
    detail    jsonb       NOT NULL,
    auteur    uuid        REFERENCES compte (id) ON DELETE SET NULL,
    le        timestamptz NOT NULL,
    FOREIGN KEY (fiche_id, espace_id) REFERENCES fiche_reglementaire (id, espace_id) ON DELETE CASCADE
);
CREATE INDEX fiche_historique_fiche ON fiche_historique (fiche_id, le);

-- ---------------------------------------------------------------------------
-- Fonctions de droits (SECURITY DEFINER, comme en V2 et V12)
-- ---------------------------------------------------------------------------
-- Le compte courant est expert du domaine : désigné, et membre CONTRIBUTEUR ou ADMINISTRATEUR de l'espace.
CREATE FUNCTION argos_expert_domaine(p_espace uuid, p_domaine varchar) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT COALESCE(argos_role_dans(p_espace) IN ('CONTRIBUTEUR', 'ADMINISTRATEUR'), false)
       AND EXISTS (SELECT 1 FROM expert_domaine x
                   WHERE x.espace_id = p_espace AND x.domaine = p_domaine AND x.compte_id = argos_compte_courant())
$$;

-- Le compte courant est expert d'au moins un domaine de l'espace.
CREATE FUNCTION argos_expert_espace(p_espace uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT COALESCE(argos_role_dans(p_espace) IN ('CONTRIBUTEUR', 'ADMINISTRATEUR'), false)
       AND EXISTS (SELECT 1 FROM expert_domaine x WHERE x.espace_id = p_espace AND x.compte_id = argos_compte_courant())
$$;

-- Le compte courant est expert d'un des domaines rattachés à la fiche.
CREATE FUNCTION argos_expert_fiche(p_fiche uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (SELECT 1 FROM fiche_domaine fd
                   WHERE fd.fiche_id = p_fiche AND argos_expert_domaine(fd.espace_id, fd.domaine))
$$;

-- Fiche visible du compte courant : membre de l'espace et fiche validée, ou expert d'un de ses
-- domaines (REG-05-S2 : une fiche non validée n'est restituée qu'aux experts).
CREATE FUNCTION argos_fiche_lisible(p_fiche uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (SELECT 1 FROM fiche_reglementaire f
                   WHERE f.id = p_fiche AND argos_espace_lisible(f.espace_id)
                     AND (f.etat_validation = 'VALIDEE' OR argos_expert_fiche(f.id)))
$$;

REVOKE EXECUTE ON FUNCTION argos_expert_domaine(uuid, varchar), argos_expert_espace(uuid), argos_expert_fiche(uuid),
    argos_fiche_lisible(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_expert_domaine(uuid, varchar), argos_expert_espace(uuid), argos_expert_fiche(uuid),
    argos_fiche_lisible(uuid) TO argos_app;

-- ---------------------------------------------------------------------------
-- Droits et politiques RLS (ADR-0016) sous argos_app. La collecte, la qualification et la préparation
-- des fiches s'exécutent sous l'utilisateur de connexion (traitement planifié, comme la collecte des avis).
-- ---------------------------------------------------------------------------
GRANT SELECT ON texte_officiel TO argos_app;

GRANT SELECT, INSERT, DELETE ON domaine_metier TO argos_app;
GRANT UPDATE (libelle, modifie_par, modifie_le) ON domaine_metier TO argos_app;
GRANT SELECT, INSERT, DELETE ON expert_domaine, regle_dictionnaire TO argos_app;
GRANT SELECT, INSERT ON reg_parametres TO argos_app;
GRANT UPDATE (rappels_jours, modifie_par, modifie_le) ON reg_parametres TO argos_app;

ALTER TABLE domaine_metier ENABLE ROW LEVEL SECURITY;
CREATE POLICY domaine_metier_lecture ON domaine_metier FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY domaine_metier_creation ON domaine_metier FOR INSERT TO argos_app
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());
CREATE POLICY domaine_metier_modification ON domaine_metier FOR UPDATE TO argos_app
    USING (argos_administre(espace_id))
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());
CREATE POLICY domaine_metier_suppression ON domaine_metier FOR DELETE TO argos_app
    USING (argos_administre(espace_id));

ALTER TABLE expert_domaine ENABLE ROW LEVEL SECURITY;
CREATE POLICY expert_domaine_lecture ON expert_domaine FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY expert_domaine_creation ON expert_domaine FOR INSERT TO argos_app
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());
CREATE POLICY expert_domaine_suppression ON expert_domaine FOR DELETE TO argos_app
    USING (argos_administre(espace_id));

ALTER TABLE regle_dictionnaire ENABLE ROW LEVEL SECURITY;
CREATE POLICY regle_dictionnaire_lecture ON regle_dictionnaire FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY regle_dictionnaire_creation ON regle_dictionnaire FOR INSERT TO argos_app
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());
CREATE POLICY regle_dictionnaire_suppression ON regle_dictionnaire FOR DELETE TO argos_app
    USING (argos_administre(espace_id));

ALTER TABLE reg_parametres ENABLE ROW LEVEL SECURITY;
CREATE POLICY reg_parametres_lecture ON reg_parametres FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY reg_parametres_creation ON reg_parametres FOR INSERT TO argos_app
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());
CREATE POLICY reg_parametres_modification ON reg_parametres FOR UPDATE TO argos_app
    USING (argos_administre(espace_id))
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());

-- Fiches : lecture selon argos_fiche_lisible ; correction, décision, transition et demande d'évolution
-- par un expert d'un des domaines de la fiche, dans l'espace courant. Aucune insertion ni suppression
-- sous argos_app : la préparation est un traitement de la plateforme.
GRANT SELECT ON fiche_reglementaire TO argos_app;
GRANT UPDATE (etat_validation, etat_suivi, resume, resume_origine, date_effet, date_effet_origine, articles_modifies,
              demande_url, demande_forge, demande_etat, decide_par, decide_le, modifie_par, modifie_le)
    ON fiche_reglementaire TO argos_app;
GRANT SELECT, INSERT, DELETE ON fiche_domaine TO argos_app;
GRANT SELECT, INSERT ON fiche_historique TO argos_app;

ALTER TABLE fiche_reglementaire ENABLE ROW LEVEL SECURITY;
CREATE POLICY fiche_reglementaire_lecture ON fiche_reglementaire FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id) AND (etat_validation = 'VALIDEE' OR argos_expert_fiche(id)));
CREATE POLICY fiche_reglementaire_expertise ON fiche_reglementaire FOR UPDATE TO argos_app
    USING (argos_espace_autorise(espace_id) AND argos_expert_fiche(id))
    WITH CHECK (argos_espace_autorise(espace_id) AND argos_expert_fiche(id) AND modifie_par = argos_compte_courant());

ALTER TABLE fiche_domaine ENABLE ROW LEVEL SECURITY;
CREATE POLICY fiche_domaine_lecture ON fiche_domaine FOR SELECT TO argos_app
    USING (argos_fiche_lisible(fiche_id));
CREATE POLICY fiche_domaine_ajout ON fiche_domaine FOR INSERT TO argos_app
    WITH CHECK (argos_espace_autorise(espace_id) AND argos_expert_espace(espace_id) AND origine = 'EXPERT');
CREATE POLICY fiche_domaine_retrait ON fiche_domaine FOR DELETE TO argos_app
    USING (argos_espace_autorise(espace_id) AND argos_expert_fiche(fiche_id));

ALTER TABLE fiche_historique ENABLE ROW LEVEL SECURITY;
CREATE POLICY fiche_historique_lecture ON fiche_historique FOR SELECT TO argos_app
    USING (argos_fiche_lisible(fiche_id));
CREATE POLICY fiche_historique_ajout ON fiche_historique FOR INSERT TO argos_app
    WITH CHECK (argos_espace_autorise(espace_id) AND argos_expert_fiche(fiche_id) AND auteur = argos_compte_courant());
