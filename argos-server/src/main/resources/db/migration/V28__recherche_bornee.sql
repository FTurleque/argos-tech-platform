-- Recherche bornée (changement OpenSpec accelerer-requetes-chaudes, tâche 6.1 ; constat AUD-PER-07 de l'audit du
-- 2026-11-21). Exigence : RCH-07 (seule la page demandée est classée, total plafonné). Conception : design.md, D9.
--
-- argos_selectionner_veille applique les mêmes critères et les mêmes règles de visibilité que la fonction de V11,
-- mais renvoie au plus p_plafond candidats, les plus récents (date de publication, sinon de modification ou de
-- réception), sans calculer de pertinence : chaque branche s'arrête à p_plafond lignes et aucune sélection complète
-- n'est matérialisée. RechercheVeille calcule la pertinence (ts_rank) sur ces seuls candidats, compte le total
-- plafonné et classe la page. L'application relit ensuite chaque élément restitué sous argos_app : la RLS reste le
-- garde-fou de la restitution.
--
-- Paramètres : ceux de la fonction de V11 (voir V11__recherche_veille.sql), puis p_plafond, nombre maximal de
-- candidats renvoyés.
CREATE FUNCTION argos_selectionner_veille(p_requete tsquery, p_depuis timestamptz, p_types varchar[],
        p_projets uuid[], p_techno_requete tsquery, p_techno_termes varchar[], p_techno_ecosystemes varchar[],
        p_gravites varchar[], p_paquets varchar[], p_sources varchar[], p_plafond integer)
    RETURNS TABLE (type varchar, id uuid, date_ref timestamptz)
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

    -- $1 requête, $2 depuis, $3 types, $4 projets, $5 requête technologies, $6 termes, $7 écosystèmes,
    -- $8 gravités, $9 paquets, $10 sources, $11 espaces lisibles, $12 compte courant, $13 plafond.

    -- Signaux de sécurité, les plus récents d'abord (index signal_date).
    IF p_types IS NULL OR 'AVIS_SECURITE' = ANY (p_types) THEN
        RETURN QUERY EXECUTE $q$
        SELECT 'AVIS_SECURITE'::varchar, s.id, coalesce(s.published_at, s.modified_at)
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
        ORDER BY coalesce(s.published_at, s.modified_at) DESC, s.id
        LIMIT $13
        $q$ USING p_requete, p_depuis, p_types, p_projets, p_techno_requete, p_techno_termes, p_techno_ecosystemes,
                  p_gravites, p_paquets, p_sources, v_espaces, v_compte, p_plafond;
    END IF;

    -- Actualités web : une par empreinte (WEB-05), la ligne d'un espace de préférence ; les plus récentes d'abord.
    IF p_gravites IS NULL AND p_paquets IS NULL AND p_sources IS NULL
       AND (p_types IS NULL OR 'ARTICLE' = ANY (p_types) OR 'CHANGEMENT_PAGE' = ANY (p_types)) THEN
        RETURN QUERY EXECUTE $q$
        WITH visibles AS (
            SELECT a.id, a.empreinte, a.espace_id, a.recu_le, a.type, coalesce(a.publie_le, a.recu_le) AS date_ref
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
        ),
        uniques AS (
            SELECT DISTINCT ON (v.empreinte) v.type, v.id, v.date_ref
            FROM visibles v
            ORDER BY v.empreinte, (v.espace_id IS NULL), v.recu_le, v.id
        )
        SELECT u.type, u.id, u.date_ref FROM uniques u ORDER BY u.date_ref DESC, u.id LIMIT $13
        $q$ USING p_requete, p_depuis, p_types, p_projets, p_techno_requete, p_techno_termes, p_techno_ecosystemes,
                  p_gravites, p_paquets, p_sources, v_espaces, v_compte, p_plafond;
    END IF;
END
$fn$;

REVOKE EXECUTE ON FUNCTION argos_selectionner_veille(tsquery, timestamptz, varchar[], uuid[], tsquery, varchar[],
    varchar[], varchar[], varchar[], varchar[], integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION argos_selectionner_veille(tsquery, timestamptz, varchar[], uuid[], tsquery, varchar[],
    varchar[], varchar[], varchar[], varchar[], integer) TO argos_app;

-- La fonction de V11, qui classait et renvoyait toute la sélection, n'a plus d'appelant.
DROP FUNCTION argos_rechercher_veille(tsquery, timestamptz, varchar[], uuid[], tsquery, varchar[], varchar[],
    varchar[], varchar[], varchar[]);
