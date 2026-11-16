-- Activité factuelle des projets (changement OpenSpec mesurer-activite-projets).
-- Exigences : PIN-01 à PIN-05. Conception : décision D1 (agréger avant de stocker).
--
-- Aucune table ne contient d'identité de personne (PIN-03, risque R-09) : ni auteur, ni assigné,
-- ni identifiant de compte de forge. Seuls des compteurs par projet, jour et type d'événement,
-- l'état des jalons et l'état de la collecte sont conservés. L'historique est conservé 13 mois puis
-- supprimé par la tâche planifiée de purge (décision du 2026-11-16, Q-2).

-- Compteurs quotidiens par projet et type d'événement (PIN-01). Le jour suit le fuseau commun
-- (argos.alerting.fuseau) ; une collecte remplace les compteurs des jours de sa fenêtre.
CREATE TABLE activite_compteur (
    project_id uuid        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    jour       date        NOT NULL,
    type       varchar(32) NOT NULL CHECK (type IN ('TICKET_OUVERT', 'TICKET_FERME', 'FUSION_OUVERTE',
                                                    'FUSION_FUSIONNEE', 'PIPELINE_REUSSI', 'PIPELINE_ECHOUE',
                                                    'PIPELINE_AUTRE', 'VERSION_PUBLIEE')),
    valeur     integer     NOT NULL CHECK (valeur >= 0),
    PRIMARY KEY (project_id, jour, type)
);
CREATE INDEX activite_compteur_jour ON activite_compteur (jour);

-- État des jalons au dernier relevé (PIN-01, PIN-02) : tickets fermés et total, dénominateur explicite.
CREATE TABLE activite_jalon (
    project_id     uuid         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    reference      varchar(64)  NOT NULL,
    titre          varchar(200) NOT NULL,
    etat           varchar(16)  NOT NULL CHECK (etat IN ('OUVERT', 'FERME')),
    echeance       date,
    tickets_fermes integer      NOT NULL CHECK (tickets_fermes >= 0),
    tickets_total  integer      NOT NULL CHECK (tickets_total >= 0),
    releve_le      timestamptz  NOT NULL,
    PRIMARY KEY (project_id, reference)
);
CREATE INDEX activite_jalon_releve ON activite_jalon (releve_le);

-- État de la collecte d'un projet : fraîcheur des données et report en cas de quota épuisé (PIN-05).
CREATE TABLE activite_collecte (
    project_id            uuid          PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    etat                  varchar(16)   NOT NULL CHECK (etat IN ('A_JOUR', 'REPORTEE_QUOTA', 'ECHEC')),
    derniere_mise_a_jour  timestamptz,
    prochaine_collecte    timestamptz   NOT NULL,
    complet               boolean       NOT NULL DEFAULT true,
    donnees_depuis        date,
    motif                 varchar(500),
    tentee_le             timestamptz   NOT NULL
);
CREATE INDEX activite_collecte_prochaine ON activite_collecte (prochaine_collecte);

-- Garde-fou en base (ADR-0016) : sous argos_app, l'activité d'un projet n'est lisible que par un
-- membre de son espace. La collecte et la purge s'exécutent sous l'utilisateur de connexion.
GRANT SELECT ON activite_compteur, activite_jalon, activite_collecte TO argos_app;

ALTER TABLE activite_compteur ENABLE ROW LEVEL SECURITY;
CREATE POLICY activite_compteur_lecture ON activite_compteur FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
ALTER TABLE activite_jalon ENABLE ROW LEVEL SECURITY;
CREATE POLICY activite_jalon_lecture ON activite_jalon FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
ALTER TABLE activite_collecte ENABLE ROW LEVEL SECURITY;
CREATE POLICY activite_collecte_lecture ON activite_collecte FOR SELECT TO argos_app
    USING (argos_projet_lisible(project_id));
