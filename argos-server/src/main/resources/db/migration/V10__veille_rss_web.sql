-- Veille RSS/Atom et pages surveillées (changement OpenSpec collecter-veille-rss-web, ADR-0028).
-- Exigences : WEB-01 (flux), WEB-02 (page surveillée), WEB-03 (adresses interdites, état d'erreur),
-- WEB-04 (contenu externe conservé comme donnée), WEB-05 (déduplication), WEB-06 (rapprochement par nom).
-- Protocole de cloisonnement : migration V2 (rôle argos_app, compte et espace courants).

-- Source de veille web : flux RSS/Atom ou page surveillée, d'un espace (module signal) ou personnelle.
CREATE TABLE source_web (
    id                    uuid          PRIMARY KEY,
    portee                varchar(16)   NOT NULL CHECK (portee IN ('ESPACE', 'PERSONNELLE')),
    espace_id             uuid          REFERENCES espace (id) ON DELETE CASCADE,
    proprietaire_id       uuid          REFERENCES compte (id),
    type                  varchar(8)    NOT NULL CHECK (type IN ('FLUX', 'PAGE')),
    adresse               varchar(2000) NOT NULL,
    libelle               varchar(200)  NOT NULL,
    etat                  varchar(16)   NOT NULL DEFAULT 'ACTIVE' CHECK (etat IN ('ACTIVE', 'EN_ERREUR')),
    echecs_consecutifs    integer       NOT NULL DEFAULT 0,
    derniere_erreur       varchar(1000),
    derniere_collecte_le  timestamptz,
    dernier_succes_le     timestamptz,
    prochaine_collecte_le timestamptz   NOT NULL DEFAULT now(),
    page_empreinte        char(64),
    page_texte            text,
    cree_par              uuid          NOT NULL REFERENCES compte (id),
    cree_le               timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT source_web_portee_ck CHECK (
        (portee = 'ESPACE' AND espace_id IS NOT NULL AND proprietaire_id IS NULL)
        OR (portee = 'PERSONNELLE' AND proprietaire_id IS NOT NULL AND espace_id IS NULL))
);
-- Une adresse n'est suivie qu'une fois par espace ou par propriétaire.
CREATE UNIQUE INDEX source_web_adresse_uk ON source_web ((coalesce(espace_id, proprietaire_id)), adresse);
CREATE INDEX source_web_echeance ON source_web (prochaine_collecte_le);
CREATE INDEX source_web_proprietaire ON source_web (proprietaire_id) WHERE portee = 'PERSONNELLE';

-- Actualité dédupliquée : une ligne par empreinte, quel que soit le nombre de sources (WEB-05).
-- Le texte est du texte brut extrait du contenu externe, conservé comme donnée non fiable (WEB-04).
CREATE TABLE actualite (
    id        uuid          PRIMARY KEY,
    empreinte char(64)      NOT NULL UNIQUE,
    type      varchar(20)   NOT NULL CHECK (type IN ('ARTICLE', 'CHANGEMENT_PAGE')),
    titre     varchar(1000) NOT NULL,
    lien      varchar(2000),
    texte     text          NOT NULL DEFAULT '',
    publie_le timestamptz,
    recu_le   timestamptz   NOT NULL
);
CREATE INDEX actualite_date ON actualite ((coalesce(publie_le, recu_le)) DESC);

-- Citation d'une actualité par une source.
CREATE TABLE actualite_source (
    actualite_id uuid          NOT NULL REFERENCES actualite (id) ON DELETE CASCADE,
    source_id    uuid          NOT NULL REFERENCES source_web (id) ON DELETE CASCADE,
    lien         varchar(2000),
    recu_le      timestamptz   NOT NULL,
    PRIMARY KEY (actualite_id, source_id)
);
CREATE INDEX actualite_source_source ON actualite_source (source_id);

-- Rapprochement par nom de technologie (module impact, WEB-06) : niveau « à vérifier » seulement.
CREATE TABLE actualite_impact (
    id           uuid         PRIMARY KEY,
    actualite_id uuid         NOT NULL REFERENCES actualite (id) ON DELETE CASCADE,
    project_id   uuid         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    workspace_id uuid         NOT NULL REFERENCES espace (id) ON DELETE CASCADE,
    technologie  varchar(200) NOT NULL,
    niveau       varchar(16)  NOT NULL CHECK (niveau = 'A_VERIFIER'),
    methode      varchar(40)  NOT NULL,
    cree_le      timestamptz  NOT NULL,
    CONSTRAINT actualite_impact_uk UNIQUE (actualite_id, project_id, technologie)
);
CREATE INDEX actualite_impact_workspace ON actualite_impact (workspace_id);

-- Garde-fou en base (ADR-0016). Sous argos_app :
--   - une source personnelle n'est visible, créée et supprimée que par son propriétaire ;
--   - une source d'espace est visible des membres de l'espace, créée et supprimée par ses administrateurs ;
--   - une actualité et ses citations ne sont visibles que par une source visible ;
--   - un rapprochement n'est visible que des membres de son espace.
-- La collecte et le rapprochement écrivent sous l'utilisateur de connexion.
CREATE FUNCTION argos_source_web_lisible(p_source uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM source_web s
        WHERE s.id = p_source
          AND ((s.portee = 'PERSONNELLE' AND s.proprietaire_id = argos_compte_courant())
               OR (s.portee = 'ESPACE' AND argos_espace_lisible(s.espace_id))))
$$;

CREATE FUNCTION argos_actualite_lisible(p_actualite uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM actualite_source x
        WHERE x.actualite_id = p_actualite AND argos_source_web_lisible(x.source_id))
$$;

REVOKE EXECUTE ON FUNCTION argos_source_web_lisible(uuid), argos_actualite_lisible(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_source_web_lisible(uuid), argos_actualite_lisible(uuid) TO argos_app;

GRANT SELECT, INSERT, DELETE ON source_web TO argos_app;
GRANT SELECT ON actualite, actualite_source, actualite_impact TO argos_app;

ALTER TABLE source_web ENABLE ROW LEVEL SECURITY;
CREATE POLICY source_web_lecture ON source_web FOR SELECT TO argos_app
    USING ((portee = 'PERSONNELLE' AND proprietaire_id = argos_compte_courant())
           OR (portee = 'ESPACE' AND argos_espace_lisible(espace_id)));
CREATE POLICY source_web_creation ON source_web FOR INSERT TO argos_app
    WITH CHECK (cree_par = argos_compte_courant()
                AND ((portee = 'PERSONNELLE' AND proprietaire_id = argos_compte_courant())
                     OR (portee = 'ESPACE' AND argos_espace_autorise(espace_id)
                         AND argos_role_dans(espace_id) = 'ADMINISTRATEUR')));
CREATE POLICY source_web_suppression ON source_web FOR DELETE TO argos_app
    USING ((portee = 'PERSONNELLE' AND proprietaire_id = argos_compte_courant())
           OR (portee = 'ESPACE' AND argos_espace_autorise(espace_id)
               AND argos_role_dans(espace_id) = 'ADMINISTRATEUR'));

ALTER TABLE actualite ENABLE ROW LEVEL SECURITY;
CREATE POLICY actualite_lecture ON actualite FOR SELECT TO argos_app
    USING (argos_actualite_lisible(id));

ALTER TABLE actualite_source ENABLE ROW LEVEL SECURITY;
CREATE POLICY actualite_source_lecture ON actualite_source FOR SELECT TO argos_app
    USING (argos_source_web_lisible(source_id));

ALTER TABLE actualite_impact ENABLE ROW LEVEL SECURITY;
CREATE POLICY actualite_impact_lecture ON actualite_impact FOR SELECT TO argos_app
    USING (argos_espace_lisible(workspace_id));
