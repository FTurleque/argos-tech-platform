-- Alertes immédiates et résumés périodiques (changement OpenSpec diffuser-alertes-et-digests, tâche 2.1).
-- Exigences : ALR-01 à ALR-06. Conception : DC-1, décision D2 (idempotence par signal, utilisateur et canal).
--
-- Une ligne de la table alerte est un message pour un destinataire sur un canal : alerte immédiate
-- (IMMEDIATE) ou résumé (DAILY, WEEKLY), avec son état d'envoi. Le contenu (titre, gravité, projets
-- cités, sources, lien, résumé IA) est construit pour son seul destinataire à partir des impacts qu'il
-- est autorisé à voir (ACC-04, ALR-06) ; il n'est lisible que de lui sous argos_app.
-- L'évaluation des règles et l'envoi sont des traitements techniques, exécutés sous l'utilisateur de
-- connexion (même règle que la corrélation, V2).

CREATE TABLE alerte (
    id               uuid         PRIMARY KEY,
    nature           varchar(16)  NOT NULL CHECK (nature IN ('IMMEDIATE', 'DAILY', 'WEEKLY')),
    compte_id        uuid         NOT NULL REFERENCES compte (id) ON DELETE CASCADE,
    canal            varchar(40)  NOT NULL CHECK (canal IN ('EMAIL', 'DESKTOP')),
    signal_id        uuid         REFERENCES signal (id) ON DELETE CASCADE,
    fin_periode      timestamptz,
    titre            text         NOT NULL,
    gravite          varchar(64),
    contenu          jsonb        NOT NULL,
    resume_ia        text,
    etat_resume_ia   varchar(24)  NOT NULL
                     CHECK (etat_resume_ia IN ('SANS_OBJET', 'DISPONIBLE', 'INDISPONIBLE', 'REFUSE', 'PLAFOND_ATTEINT')),
    etat             varchar(16)  NOT NULL CHECK (etat IN ('EN_ATTENTE', 'ENVOYEE', 'EN_ECHEC')),
    tentatives       integer      NOT NULL DEFAULT 0 CHECK (tentatives >= 0),
    prochain_essai   timestamptz,
    derniere_erreur  text,
    cree_le          timestamptz  NOT NULL,
    envoye_le        timestamptz,
    CONSTRAINT alerte_nature_ck CHECK (
        (nature = 'IMMEDIATE' AND signal_id IS NOT NULL AND fin_periode IS NULL)
        OR (nature <> 'IMMEDIATE' AND signal_id IS NULL AND fin_periode IS NOT NULL))
);
-- Clé d'idempotence d'une alerte immédiate (ALR-03, D2) : un rejeu des impacts ou plusieurs règles
-- satisfaites ne produisent jamais une seconde alerte pour le même signal, destinataire et canal.
CREATE UNIQUE INDEX alerte_immediate_uk ON alerte (signal_id, compte_id, canal) WHERE nature = 'IMMEDIATE';
-- Un seul résumé par destinataire, canal, fréquence et échéance.
CREATE UNIQUE INDEX alerte_resume_uk ON alerte (compte_id, canal, nature, fin_periode) WHERE nature <> 'IMMEDIATE';
CREATE INDEX alerte_compte ON alerte (compte_id, cree_le);
CREATE INDEX alerte_a_envoyer ON alerte (prochain_essai) WHERE etat = 'EN_ATTENTE';

-- Éléments en attente du prochain résumé d'un destinataire (ALR-01-S2, ALR-02-S1, ALR-04).
-- Les données du signal sont publiques ; les projets cités sont relus sous le contexte du
-- destinataire au moment de composer le résumé. Un élément n'entre qu'une fois dans un résumé
-- d'une fréquence et d'un canal donnés.
CREATE TABLE resume_element (
    compte_id           uuid         NOT NULL REFERENCES compte (id) ON DELETE CASCADE,
    signal_id           uuid         NOT NULL REFERENCES signal (id) ON DELETE CASCADE,
    frequence           varchar(16)  NOT NULL CHECK (frequence IN ('DAILY', 'WEEKLY')),
    canal               varchar(40)  NOT NULL CHECK (canal IN ('EMAIL', 'DESKTOP')),
    titre               text         NOT NULL,
    gravite             varchar(64),
    source              varchar(64)  NOT NULL,
    identifiant_externe text         NOT NULL,
    enregistre_le       timestamptz  NOT NULL,
    alerte_id           uuid         REFERENCES alerte (id) ON DELETE SET NULL,
    PRIMARY KEY (compte_id, signal_id, frequence, canal)
);
CREATE INDEX resume_element_en_attente ON resume_element (frequence, compte_id, canal) WHERE alerte_id IS NULL;

-- Garde-fou en base (ADR-0016) : sous argos_app, un compte ne lit que ses alertes et ses éléments de
-- résumé, et n'enregistre que la remise de ses propres alertes au client desktop.
GRANT SELECT ON alerte, resume_element TO argos_app;
GRANT UPDATE (etat, envoye_le) ON alerte TO argos_app;

ALTER TABLE alerte ENABLE ROW LEVEL SECURITY;
CREATE POLICY alerte_lecture ON alerte FOR SELECT TO argos_app
    USING (compte_id = argos_compte_courant());
CREATE POLICY alerte_remise ON alerte FOR UPDATE TO argos_app
    USING (compte_id = argos_compte_courant() AND canal = 'DESKTOP')
    WITH CHECK (compte_id = argos_compte_courant() AND canal = 'DESKTOP');

ALTER TABLE resume_element ENABLE ROW LEVEL SECURITY;
CREATE POLICY resume_element_lecture ON resume_element FOR SELECT TO argos_app
    USING (compte_id = argos_compte_courant());
