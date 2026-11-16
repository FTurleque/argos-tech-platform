-- Référentiel des standards et radar des sorties (changement OpenSpec construire-radar-et-standards).
-- Exigences : RAD-01 (entrée du référentiel), RAD-02 (gouvernance, suggestions en attente), RAD-03 (radar
-- par horizon), RAD-04 (rappels J-30, J-7, J0 paramétrables par espace, Q-1), RAD-05 (dette de version),
-- RAD-06 (annonce d'un changement de standard).
-- Protocole de cloisonnement : migration V2 (rôle argos_app, compte et espace courants).

-- ---------------------------------------------------------------------------
-- Module catalog : référentiel des standards d'un espace (Q-3 : aucun référentiel modèle)
-- ---------------------------------------------------------------------------
-- Une technologie est identifiée par sa clé de paquet (PURL sans version, PackageKey). Le produit
-- endoflife.date, facultatif, relie la technologie au calendrier public des sorties et fins de support.
CREATE TABLE standard_technologie (
    id                  uuid          PRIMARY KEY,
    espace_id           uuid          NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    cle                 varchar(500)  NOT NULL,
    libelle             varchar(200)  NOT NULL,
    statut              varchar(8)    NOT NULL CHECK (statut IN ('ADOPT', 'TRIAL', 'ASSESS', 'HOLD')),
    version_recommandee varchar(100),
    responsable         varchar(200)  NOT NULL CHECK (length(btrim(responsable)) > 0),
    justification       varchar(2000) NOT NULL CHECK (length(btrim(justification)) > 0),
    date_revue          date          NOT NULL,
    produit_cycle_vie   varchar(100)  CHECK (produit_cycle_vie ~ '^[a-z0-9][a-z0-9._+-]{0,99}$'),
    cree_le             timestamptz   NOT NULL,
    modifie_le          timestamptz   NOT NULL,
    modifie_par         uuid          NOT NULL REFERENCES compte (id),
    CONSTRAINT standard_technologie_uk UNIQUE (espace_id, cle),
    -- RAD-01 : une technologie ADOPT a toujours une version recommandée.
    CONSTRAINT standard_adopt_version_ck CHECK (statut <> 'ADOPT' OR version_recommandee IS NOT NULL)
);
CREATE INDEX standard_technologie_cle ON standard_technologie (cle);
CREATE INDEX standard_technologie_produit ON standard_technologie (produit_cycle_vie) WHERE produit_cycle_vie IS NOT NULL;

-- Historique des valeurs successives d'une entrée (RAD-01) : une ligne par création ou modification.
CREATE TABLE standard_historique (
    id                  uuid          PRIMARY KEY,
    standard_id         uuid          NOT NULL REFERENCES standard_technologie (id) ON DELETE CASCADE,
    espace_id           uuid          NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    statut              varchar(8)    NOT NULL,
    version_recommandee varchar(100),
    responsable         varchar(200)  NOT NULL,
    justification       varchar(2000) NOT NULL,
    date_revue          date          NOT NULL,
    origine             varchar(16)   NOT NULL CHECK (origine IN ('SAISIE', 'SUGGESTION')),
    modifie_par         uuid          NOT NULL REFERENCES compte (id),
    modifie_le          timestamptz   NOT NULL
);
CREATE INDEX standard_historique_standard ON standard_historique (standard_id, modifie_le);

-- Suggestion de l'IA (RAD-02-S2) : enregistrée en attente, sans effet sur le référentiel tant qu'un
-- administrateur de l'espace ne l'a pas acceptée.
CREATE TABLE standard_suggestion (
    id                  uuid          PRIMARY KEY,
    espace_id           uuid          NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    cle                 varchar(500)  NOT NULL,
    statut              varchar(8)    NOT NULL CHECK (statut IN ('ADOPT', 'TRIAL', 'ASSESS', 'HOLD')),
    version_recommandee varchar(100),
    justification       varchar(2000) NOT NULL,
    origine             varchar(16)   NOT NULL CHECK (origine IN ('IA')),
    etat                varchar(16)   NOT NULL DEFAULT 'EN_ATTENTE' CHECK (etat IN ('EN_ATTENTE', 'ACCEPTEE', 'REJETEE')),
    cree_le             timestamptz   NOT NULL,
    decide_par          uuid          REFERENCES compte (id),
    decide_le           timestamptz
);
CREATE INDEX standard_suggestion_espace ON standard_suggestion (espace_id, etat);

-- Paramètres du radar d'un espace : jours de rappel avant une échéance (Q-1 : J-30, J-7, J0 par défaut).
CREATE TABLE radar_parametres (
    espace_id     uuid        PRIMARY KEY REFERENCES espace (id) ON DELETE CASCADE,
    rappels_jours integer[]   NOT NULL CHECK (cardinality(rappels_jours) BETWEEN 1 AND 10
                                              AND 0 <= ALL (rappels_jours) AND 365 >= ALL (rappels_jours)),
    modifie_par   uuid        NOT NULL REFERENCES compte (id),
    modifie_le    timestamptz NOT NULL
);

-- ---------------------------------------------------------------------------
-- Module release-radar : calendrier public endoflife.date (Q-2), données publiques
-- ---------------------------------------------------------------------------
-- Produit suivi et fraîcheur de sa dernière synchronisation (observabilité : fraîcheur de la source).
CREATE TABLE cycle_vie_produit (
    produit             varchar(100)  PRIMARY KEY,
    etat                varchar(16)   NOT NULL CHECK (etat IN ('A_JOUR', 'EN_ERREUR')),
    derniere_synchro_le timestamptz   NOT NULL,
    dernier_succes_le   timestamptz,
    derniere_erreur     varchar(1000)
);

-- Cycle de vie d'une branche d'un produit : sortie, fin de support, dernier correctif.
CREATE TABLE cycle_vie (
    produit             varchar(100) NOT NULL REFERENCES cycle_vie_produit (produit) ON DELETE CASCADE,
    cycle               varchar(100) NOT NULL,
    sortie_le           date,
    fin_support_le      date,
    en_fin_support      boolean      NOT NULL,
    derniere_version    varchar(100),
    derniere_version_le date,
    PRIMARY KEY (produit, cycle)
);

-- ---------------------------------------------------------------------------
-- Module impact : dette de version (RAD-05)
-- ---------------------------------------------------------------------------
-- Composant d'un projet dont la version a atteint sa fin de support, avec la version standard de l'espace.
CREATE TABLE dette_version (
    id               uuid         PRIMARY KEY,
    project_id       uuid         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    workspace_id     uuid         NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    component_id     uuid         NOT NULL REFERENCES component (id) ON DELETE CASCADE,
    cle              varchar(500) NOT NULL,
    version          varchar(200) NOT NULL,
    cycle            varchar(100) NOT NULL,
    fin_support_le   date         NOT NULL,
    version_standard varchar(100),
    source           varchar(64)  NOT NULL,
    cree_le          timestamptz  NOT NULL,
    CONSTRAINT dette_version_uk UNIQUE (project_id, component_id)
);
CREATE INDEX dette_version_workspace ON dette_version (workspace_id);

-- ---------------------------------------------------------------------------
-- Module alerting : rappels d'échéance (RAD-04) et annonces de standard (RAD-06)
-- ---------------------------------------------------------------------------
-- Deux natures de message s'ajoutent aux alertes et résumés. Leur clé d'idempotence (cle_radar) désigne
-- l'échéance et le jour de rappel, ou le changement de standard : un rejeu ne produit pas de second message.
ALTER TABLE alerte DROP CONSTRAINT alerte_nature_check;
ALTER TABLE alerte DROP CONSTRAINT alerte_nature_ck;
ALTER TABLE alerte ADD COLUMN cle_radar varchar(600);
ALTER TABLE alerte ADD CONSTRAINT alerte_nature_check
    CHECK (nature IN ('IMMEDIATE', 'DAILY', 'WEEKLY', 'RAPPEL', 'ANNONCE'));
ALTER TABLE alerte ADD CONSTRAINT alerte_nature_ck CHECK (
    (nature = 'IMMEDIATE' AND signal_id IS NOT NULL AND fin_periode IS NULL AND cle_radar IS NULL)
    OR (nature IN ('DAILY', 'WEEKLY') AND signal_id IS NULL AND fin_periode IS NOT NULL AND cle_radar IS NULL)
    OR (nature IN ('RAPPEL', 'ANNONCE') AND signal_id IS NULL AND fin_periode IS NULL AND cle_radar IS NOT NULL));
-- alerte_resume_uk (V8) reste inchangé : fin_periode est absente des rappels et des annonces.
CREATE UNIQUE INDEX alerte_radar_uk ON alerte (compte_id, canal, cle_radar) WHERE nature IN ('RAPPEL', 'ANNONCE');

-- ---------------------------------------------------------------------------
-- Garde-fou en base (ADR-0016). Sous argos_app :
--   - le référentiel, son historique, les suggestions, les paramètres du radar et la dette de version
--     d'un espace sont lisibles par ses membres ;
--   - seul un administrateur de l'espace courant crée ou modifie une entrée, écrit l'historique, décide
--     d'une suggestion ou modifie les paramètres (RAD-02) ;
--   - une suggestion n'est créée que par un traitement technique (utilisateur de connexion) ;
--   - le calendrier endoflife.date est public : lisible par tout compte, écrit par la synchronisation.
-- ---------------------------------------------------------------------------
CREATE FUNCTION argos_administre(p_espace uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT argos_espace_autorise(p_espace) AND argos_role_dans(p_espace) = 'ADMINISTRATEUR' $$;
REVOKE EXECUTE ON FUNCTION argos_administre(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_administre(uuid) TO argos_app;

GRANT SELECT, INSERT ON standard_technologie TO argos_app;
GRANT UPDATE (libelle, statut, version_recommandee, responsable, justification, date_revue, produit_cycle_vie,
              modifie_le, modifie_par) ON standard_technologie TO argos_app;
GRANT SELECT, INSERT ON standard_historique TO argos_app;
GRANT SELECT ON standard_suggestion TO argos_app;
GRANT UPDATE (etat, decide_par, decide_le) ON standard_suggestion TO argos_app;
GRANT SELECT, INSERT ON radar_parametres TO argos_app;
GRANT UPDATE (rappels_jours, modifie_par, modifie_le) ON radar_parametres TO argos_app;
GRANT SELECT ON cycle_vie_produit, cycle_vie, dette_version TO argos_app;

ALTER TABLE standard_technologie ENABLE ROW LEVEL SECURITY;
CREATE POLICY standard_technologie_lecture ON standard_technologie FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY standard_technologie_creation ON standard_technologie FOR INSERT TO argos_app
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());
CREATE POLICY standard_technologie_modification ON standard_technologie FOR UPDATE TO argos_app
    USING (argos_administre(espace_id))
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());

ALTER TABLE standard_historique ENABLE ROW LEVEL SECURITY;
CREATE POLICY standard_historique_lecture ON standard_historique FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY standard_historique_ecriture ON standard_historique FOR INSERT TO argos_app
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());

ALTER TABLE standard_suggestion ENABLE ROW LEVEL SECURITY;
CREATE POLICY standard_suggestion_lecture ON standard_suggestion FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY standard_suggestion_decision ON standard_suggestion FOR UPDATE TO argos_app
    USING (argos_administre(espace_id) AND etat = 'EN_ATTENTE')
    WITH CHECK (argos_administre(espace_id) AND decide_par = argos_compte_courant());

ALTER TABLE radar_parametres ENABLE ROW LEVEL SECURITY;
CREATE POLICY radar_parametres_lecture ON radar_parametres FOR SELECT TO argos_app
    USING (argos_espace_lisible(espace_id));
CREATE POLICY radar_parametres_creation ON radar_parametres FOR INSERT TO argos_app
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());
CREATE POLICY radar_parametres_modification ON radar_parametres FOR UPDATE TO argos_app
    USING (argos_administre(espace_id))
    WITH CHECK (argos_administre(espace_id) AND modifie_par = argos_compte_courant());

ALTER TABLE dette_version ENABLE ROW LEVEL SECURITY;
CREATE POLICY dette_version_lecture ON dette_version FOR SELECT TO argos_app
    USING (argos_espace_lisible(workspace_id));
