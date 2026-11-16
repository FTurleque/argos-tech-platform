-- Recherche plein texte, filtres combinables et vues enregistrées (changement OpenSpec rechercher-et-filtrer).
-- Exigences : RCH-01 (plein texte), RCH-02 (filtres), RCH-03 (droits), RCH-04 (vues), RCH-05 (temps de réponse).
-- Décisions : ADR-0015 (PostgreSQL FTS, aucun moteur externe) ; Q-1 du 2026-11-16 (configurations
-- `french` et `english`) ; protocole de cloisonnement de la migration V2 (rôle argos_app).

-- ---------------------------------------------------------------------------
-- Vecteur et requête plein texte bilingues (Q-1)
-- ---------------------------------------------------------------------------
-- Chaque texte est analysé par les configurations french et english (racines des deux langues) et par
-- simple (identifiants tels que GHSA-xxxx ou CVE-2026-1234, noms de projets). Les textes longs sont
-- bornés à 100 000 caractères : un tsvector est limité à 1 Mio.
CREATE FUNCTION argos_vecteur_recherche(p_titre text, p_contenu text) RETURNS tsvector
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT setweight(to_tsvector('french'::regconfig, coalesce(p_titre, '')), 'A')
        || setweight(to_tsvector('english'::regconfig, coalesce(p_titre, '')), 'A')
        || setweight(to_tsvector('simple'::regconfig, coalesce(p_titre, '')), 'A')
        || setweight(to_tsvector('french'::regconfig, left(coalesce(p_contenu, ''), 100000)), 'B')
        || setweight(to_tsvector('english'::regconfig, left(coalesce(p_contenu, ''), 100000)), 'B')
        || setweight(to_tsvector('simple'::regconfig, left(coalesce(p_contenu, ''), 100000)), 'B')
$$;

-- Requête saisie par l'utilisateur (syntaxe « web » : mots, "expression exacte", or, -exclusion),
-- analysée dans les trois configurations ; un élément correspond s'il satisfait l'une d'elles.
-- websearch_to_tsquery ne lève jamais d'erreur de syntaxe : aucune saisie ne fait échouer la requête.
CREATE FUNCTION argos_requete_recherche(p_texte text) RETURNS tsquery
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT websearch_to_tsquery('french'::regconfig, p_texte)
        || websearch_to_tsquery('english'::regconfig, p_texte)
        || websearch_to_tsquery('simple'::regconfig, p_texte)
$$;

-- Index plein texte des éléments de veille (module signal) : signaux de sécurité et actualités web.
-- toast_tuple_target = 256 : le vecteur et les textes longs sont rangés hors de la ligne (TOAST), pour que
-- les parcours de la recherche (filtres sans mots, dédoublonnage) lisent des lignes étroites.
ALTER TABLE signal SET (toast_tuple_target = 256);
ALTER TABLE actualite SET (toast_tuple_target = 256);
ALTER TABLE signal ADD COLUMN recherche tsvector
    GENERATED ALWAYS AS (argos_vecteur_recherche(external_id || ' ' || coalesce(title, ''), summary)) STORED;
CREATE INDEX signal_recherche ON signal USING gin (recherche);
CREATE INDEX signal_date ON signal ((coalesce(published_at, modified_at)) DESC);

ALTER TABLE actualite ADD COLUMN recherche tsvector
    GENERATED ALWAYS AS (argos_vecteur_recherche(titre, texte)) STORED;
CREATE INDEX actualite_recherche ON actualite USING gin (recherche);

-- Filtre par projet et correspondance par nom de projet.
CREATE INDEX project_impact_project ON project_impact (project_id);
CREATE INDEX actualite_impact_project ON actualite_impact (project_id);

-- ---------------------------------------------------------------------------
-- Sélection des éléments correspondant à une recherche (RCH-01 à RCH-03)
-- ---------------------------------------------------------------------------
-- Les opérateurs plein texte de PostgreSQL ne sont pas « leakproof » : sous la RLS, le planificateur
-- évalue d'abord la politique de chaque ligne et ne peut pas utiliser l'index GIN d'une table protégée
-- (actualite, project_impact). La sélection s'exécute donc dans cette fonction SECURITY DEFINER, qui
-- applique explicitement, de façon ensembliste, les mêmes règles de visibilité que les politiques :
--   - espaces lisibles : appartenances du compte courant, restreintes à l'espace courant s'il est
--     transmis (argos_espace_lisible, V2) ;
--   - actualité : de son propriétaire ou d'un espace lisible, citée par une source visible (V10) ;
--   - impact et rapprochement : d'un espace lisible (V5, V10) ;
--   - signal : donnée publique (V5).
-- Elle ne renvoie que des identifiants, une pertinence et une date. L'application relit ensuite chaque
-- élément restitué sous argos_app : la RLS reste le garde-fou de la restitution.
--
-- Paramètres (NULL : filtre absent ; les filtres se combinent par ET, les valeurs d'un filtre par OU) :
--   p_requete          mots recherchés (argos_requete_recherche) ;
--   p_depuis           horizon : élément daté (publication, sinon modification ou réception) après cette date ;
--   p_types            AVIS_SECURITE, ARTICLE, CHANGEMENT_PAGE ;
--   p_projets          projets : impact ou rapprochement visible sur l'un d'eux ;
--   p_techno_requete   technologies cherchées dans le texte ;
--   p_techno_termes    technologies en minuscules : écosystème ou nom de paquet affecté, technologie rapprochée ;
--   p_techno_ecosystemes écosystèmes associés aux technologies (minuscules) ;
--   p_gravites         gravités déclarées admises (majuscules) ; exclut les éléments sans gravité ;
--   p_paquets          clés de paquet (PURL sans version) affectées ;
--   p_sources          sources des signaux (OSV…) ; exclut les actualités.
CREATE FUNCTION argos_rechercher_veille(p_requete tsquery, p_depuis timestamptz, p_types varchar[],
        p_projets uuid[], p_techno_requete tsquery, p_techno_termes varchar[], p_techno_ecosystemes varchar[],
        p_gravites varchar[], p_paquets varchar[], p_sources varchar[])
    RETURNS TABLE (type varchar, id uuid, rang real, date_ref timestamptz)
    LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE
    v_compte  uuid := argos_compte_courant();
    v_espaces uuid[];
BEGIN
    IF v_compte IS NULL THEN
        RETURN;
    END IF;
    SELECT coalesce(array_agg(ap.espace_id), '{}') INTO v_espaces FROM appartenance ap
    WHERE ap.compte_id = v_compte
      AND (argos_espace_courant() IS NULL OR ap.espace_id = argos_espace_courant());

    -- Chaque requête est un texte constant exécuté par EXECUTE ... USING : les saisies ne sont que des
    -- paramètres, et le plan, établi avec leurs valeurs, écarte les filtres absents et utilise l'index GIN.
    -- $1 requête, $2 depuis, $3 types, $4 projets, $5 requête technologies, $6 termes, $7 écosystèmes,
    -- $8 gravités, $9 paquets, $10 sources, $11 espaces lisibles, $12 compte courant.

    -- Signaux de sécurité.
    IF p_types IS NULL OR 'AVIS_SECURITE' = ANY (p_types) THEN
        RETURN QUERY EXECUTE $q$
        SELECT 'AVIS_SECURITE'::varchar, s.id,
               CASE WHEN $1 IS NULL THEN 0::real ELSE ts_rank(s.recherche, $1) END,
               coalesce(s.published_at, s.modified_at)
        FROM signal s
        WHERE ($1 IS NULL OR s.id IN (
                  SELECT t.id FROM signal t WHERE t.recherche @@ $1
                  UNION
                  SELECT i.signal_id FROM project_impact i
                  WHERE i.workspace_id = ANY ($11)
                    AND i.project_id IN (SELECT p.id FROM project p
                                         WHERE p.workspace_id = ANY ($11)
                                           AND argos_vecteur_recherche(p.name, NULL) @@ $1)))
          AND ($2 IS NULL OR coalesce(s.published_at, s.modified_at) >= $2)
          AND ($4 IS NULL OR s.id IN (
                SELECT i.signal_id FROM project_impact i WHERE i.project_id = ANY ($4) AND i.workspace_id = ANY ($11)))
          AND ($5 IS NULL
               OR s.recherche @@ $5
               OR EXISTS (SELECT 1 FROM signal_package sp
                          WHERE sp.signal_id = s.id
                            AND (lower(sp.ecosystem) = ANY ($7)
                                 OR split_part(substr(sp.package_key, 5), '/', 1) = ANY ($6)
                                 OR lower(regexp_replace(sp.package_key, '^.*/', '')) = ANY ($6))))
          AND ($8 IS NULL OR upper(btrim(s.severity)) = ANY ($8))
          AND ($9 IS NULL OR EXISTS (
                SELECT 1 FROM signal_package sp WHERE sp.signal_id = s.id AND sp.package_key = ANY ($9)))
          AND ($10 IS NULL OR s.source = ANY ($10))
        $q$ USING p_requete, p_depuis, p_types, p_projets, p_techno_requete, p_techno_termes, p_techno_ecosystemes,
                  p_gravites, p_paquets, p_sources, v_espaces, v_compte;
    END IF;

    -- Actualités web : une par empreinte (WEB-05), la ligne d'un espace de préférence.
    IF p_gravites IS NULL AND p_paquets IS NULL AND p_sources IS NULL
       AND (p_types IS NULL OR 'ARTICLE' = ANY (p_types) OR 'CHANGEMENT_PAGE' = ANY (p_types)) THEN
        RETURN QUERY EXECUTE $q$
        WITH visibles AS (
            SELECT a.id, a.empreinte, a.espace_id, a.recu_le, a.type, coalesce(a.publie_le, a.recu_le) AS date_ref,
                   CASE WHEN $1 IS NULL THEN 0::real ELSE ts_rank(a.recherche, $1) END AS rang
            FROM actualite a
            WHERE (a.proprietaire_id = $12 OR a.espace_id = ANY ($11))
              AND EXISTS (SELECT 1 FROM actualite_source x JOIN source_web sw ON sw.id = x.source_id
                          WHERE x.actualite_id = a.id
                            AND ((sw.portee = 'PERSONNELLE' AND sw.proprietaire_id = $12)
                                 OR (sw.portee = 'ESPACE' AND sw.espace_id = ANY ($11))))
              AND ($1 IS NULL OR a.id IN (
                      SELECT t.id FROM actualite t WHERE t.recherche @@ $1
                      UNION
                      SELECT ai.actualite_id FROM actualite_impact ai
                      WHERE ai.workspace_id = ANY ($11)
                        AND ai.project_id IN (SELECT p.id FROM project p
                                              WHERE p.workspace_id = ANY ($11)
                                                AND argos_vecteur_recherche(p.name, NULL) @@ $1)))
              AND ($3 IS NULL OR a.type = ANY ($3))
              AND ($2 IS NULL OR coalesce(a.publie_le, a.recu_le) >= $2)
              AND ($4 IS NULL OR a.id IN (
                    SELECT ai.actualite_id FROM actualite_impact ai
                    WHERE ai.project_id = ANY ($4) AND ai.workspace_id = ANY ($11)))
              AND ($5 IS NULL
                   OR a.recherche @@ $5
                   OR EXISTS (SELECT 1 FROM actualite_impact ai
                              WHERE ai.actualite_id = a.id AND ai.workspace_id = ANY ($11)
                                AND lower(ai.technologie) = ANY ($6)))
        )
        SELECT DISTINCT ON (v.empreinte) v.type, v.id, v.rang, v.date_ref
        FROM visibles v
        ORDER BY v.empreinte, (v.espace_id IS NULL), v.recu_le, v.id
        $q$ USING p_requete, p_depuis, p_types, p_projets, p_techno_requete, p_techno_termes, p_techno_ecosystemes,
                  p_gravites, p_paquets, p_sources, v_espaces, v_compte;
    END IF;
END
$fn$;

REVOKE EXECUTE ON FUNCTION argos_rechercher_veille(tsquery, timestamptz, varchar[], uuid[], tsquery, varchar[],
    varchar[], varchar[], varchar[], varchar[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_rechercher_veille(tsquery, timestamptz, varchar[], uuid[], tsquery, varchar[],
    varchar[], varchar[], varchar[], varchar[]) TO argos_app;
GRANT EXECUTE ON FUNCTION argos_vecteur_recherche(text, text), argos_requete_recherche(text) TO argos_app;

-- ---------------------------------------------------------------------------
-- Vues enregistrées (module workspace, RCH-04)
-- ---------------------------------------------------------------------------
-- Une vue est une recherche nommée (mots et filtres, document JSON validé par l'API) ; elle n'est visible
-- que de son auteur, n'accorde aucun droit et est rejouée sous les droits courants de son auteur.
CREATE TABLE workspace_vue (
    id        uuid         PRIMARY KEY,
    compte_id uuid         NOT NULL REFERENCES compte (id) ON DELETE CASCADE,
    nom       varchar(120) NOT NULL CHECK (length(btrim(nom)) BETWEEN 1 AND 120),
    criteres  jsonb        NOT NULL CHECK (jsonb_typeof(criteres) = 'object'),
    cree_le   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT workspace_vue_nom_uk UNIQUE (compte_id, nom)
);

COMMENT ON TABLE workspace_vue IS
    'Vues enregistrées : recherche nommée d''un compte, visible de lui seul. Jamais utilisée pour une autorisation.';

-- Garde-fou en base (ADR-0016) : sous argos_app, un compte ne lit, ne crée et ne supprime que ses vues.
GRANT SELECT, INSERT, DELETE ON workspace_vue TO argos_app;
ALTER TABLE workspace_vue ENABLE ROW LEVEL SECURITY;
CREATE POLICY workspace_vue_compte ON workspace_vue FOR ALL TO argos_app
    USING (compte_id = argos_compte_courant())
    WITH CHECK (compte_id = argos_compte_courant());
