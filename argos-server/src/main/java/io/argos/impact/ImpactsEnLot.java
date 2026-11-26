package io.argos.impact;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import io.argos.commun.Jdbc;
import io.argos.inventory.InventoryQueries;

/**
 * Instructions ensemblistes de la corrélation d'un lot de signaux (COR-13, design.md D3) : chacune traite toutes les
 * correspondances du lot en une instruction, paramètres passés en tableaux ({@code unnest}). Elles s'exécutent dans la
 * transaction et la connexion de {@link CorrelationDeterministe}, sous l'utilisateur de connexion (traitement
 * technique).
 */
final class ImpactsEnLot {

    /** Triplet unique d'un impact (COR-07). */
    record Triplet(UUID signalId, UUID projectId, UUID componentId) {
    }

    /** Correspondance vérifiée entre un signal et un composant, avec la ligne d'impact qu'elle doit produire. */
    record Ligne(Triplet triplet, UUID signalVersionId, UUID workspaceId, MatchRules.Level level, Boolean transitive,
            String inventoryOrigin, Instant inventoryDate, String provenance, String packageKey, String version) {
    }

    /** Impact existant d'un triplet : niveau, version de l'avis, état et vrai s'il est redevenu actif depuis sa dernière écriture. */
    record Existant(UUID id, MatchRules.Level level, UUID signalVersionId, EtatImpact etat, boolean reactive) {
    }

    /** Impact écrit par l'upsert : identifiant, vrai s'il vient d'être créé, état enregistré. */
    record Ecrit(UUID id, boolean insere, EtatImpact etat) {
    }

    /** Impact actif d'un signal du lot qui n'est plus vérifié. */
    record Perime(UUID id, UUID signalId, UUID projectId, EtatImpact etat, UUID componentId, String packageKey) {
    }

    private ImpactsEnLot() {
    }

    /**
     * Verrous consultatifs des signaux du lot, pris en une instruction dans l'ordre des identifiants : une corrélation
     * globale et une corrélation de projet d'un même signal se sérialisent (COR-11) sans interblocage.
     */
    static void verrouiller(Connection connexion, List<UUID> signalIds) {
        List<UUID> ordonnes = signalIds.stream().distinct().sorted().toList();
        Jdbc.unique(connexion, """
                SELECT count(*) FROM (
                    SELECT pg_advisory_xact_lock(hashtext('project_impact:' || ordonnes.id::text))
                    FROM (SELECT v.id FROM unnest(?::uuid[]) WITH ORDINALITY AS v(id, rang) ORDER BY v.rang) ordonnes) verrous
                """, requete -> requete.setArray(1, uuids(connexion, ordonnes, Function.identity())),
                resultat -> resultat.getLong(1));
    }

    /**
     * Rattache à chaque composant revenu dans l'inventaire l'impact que son signal portait sur la même clé de paquet
     * et la même version avant son retrait (composant nul), s'il n'existe pas déjà d'impact pour ce composant (INV-C4) :
     * l'upsert qui suit met alors à jour cet impact, avec son historique (COR-07).
     */
    static void rattacher(Connection connexion, List<Ligne> lignes) {
        if (lignes.isEmpty()) {
            return;
        }
        Jdbc.modifier(connexion, """
                WITH donnees AS (
                    SELECT * FROM unnest(?::uuid[], ?::uuid[], ?::uuid[], ?::varchar[], ?::varchar[])
                           AS d(signal_id, project_id, component_id, package_key, version)),
                cibles AS (
                    SELECT DISTINCT ON (orphelin.id) orphelin.id, d.component_id
                    FROM donnees d
                    CROSS JOIN LATERAL (SELECT i.id FROM project_impact i
                                        WHERE i.signal_id = d.signal_id AND i.project_id = d.project_id
                                          AND i.component_id IS NULL AND i.package_key = d.package_key
                                          AND coalesce(i.component_version, '') = coalesce(d.version, '')
                                        ORDER BY i.updated_at DESC, i.id LIMIT 1) orphelin
                    WHERE NOT EXISTS (SELECT 1 FROM project_impact j WHERE j.signal_id = d.signal_id
                                        AND j.project_id = d.project_id AND j.component_id = d.component_id)
                    ORDER BY orphelin.id, d.component_id)
                UPDATE project_impact i SET component_id = c.component_id, updated_at = now()
                FROM cibles c WHERE i.id = c.id
                """, requete -> {
                    requete.setArray(1, uuids(connexion, lignes, ligne -> ligne.triplet().signalId()));
                    requete.setArray(2, uuids(connexion, lignes, ligne -> ligne.triplet().projectId()));
                    requete.setArray(3, uuids(connexion, lignes, ligne -> ligne.triplet().componentId()));
                    requete.setArray(4, textes(connexion, lignes, Ligne::packageKey));
                    requete.setArray(5, textes(connexion, lignes, Ligne::version));
                });
    }

    /** Impacts existants des triplets du lot, lus après le rattachement. */
    static Map<Triplet, Existant> existants(Connection connexion, List<Ligne> lignes) {
        Map<Triplet, Existant> existants = new HashMap<>();
        if (lignes.isEmpty()) {
            return existants;
        }
        Jdbc.lister(connexion, """
                SELECT d.signal_id, d.project_id, d.component_id, i.id, i.level, i.signal_version_id, i.state,
                       EXISTS (SELECT 1 FROM project_impact_transition t WHERE t.impact_id = i.id
                               AND t.to_state IN ('OUVERT', 'CONFIRME') AND t.occurred_at > i.updated_at)
                FROM unnest(?::uuid[], ?::uuid[], ?::uuid[]) AS d(signal_id, project_id, component_id)
                JOIN project_impact i ON i.signal_id = d.signal_id AND i.project_id = d.project_id
                                     AND i.component_id = d.component_id
                """, requete -> lierTriplets(connexion, requete, lignes),
                resultat -> Map.entry(triplet(resultat), new Existant(resultat.getObject(4, UUID.class),
                        ImpactQueries.niveau(resultat.getString(5)), resultat.getObject(6, UUID.class),
                        EtatImpact.valueOf(resultat.getString(7)), resultat.getBoolean(8))))
                .forEach(entree -> existants.put(entree.getKey(), entree.getValue()));
        return existants;
    }

    /**
     * Crée l'impact de chaque triplet, ou met à jour l'impact existant (COR-07) seulement si l'une de ses valeurs
     * change ou s'il est redevenu actif depuis sa dernière écriture : un impact inchangé n'est pas réécrit et garde sa
     * date de mise à jour (COR-07-S2). {@code component_id}, égal par la clé de conflit, n'est pas dans le {@code SET}.
     *
     * @return impacts créés ou modifiés, par triplet ; un impact inchangé est absent
     */
    static Map<Triplet, Ecrit> ecrire(Connection connexion, List<Ligne> lignes) {
        Map<Triplet, Ecrit> ecrits = new HashMap<>();
        if (lignes.isEmpty()) {
            return ecrits;
        }
        Jdbc.lister(connexion, """
                INSERT INTO project_impact(id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                       level, method, transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
                SELECT gen_random_uuid(), d.signal_id, d.version_id, d.project_id, d.workspace_id, d.component_id, d.level,
                       ?, d.transitive::boolean, d.origine, d.date::timestamptz, d.provenance::jsonb, now(), now()
                FROM unnest(?::uuid[], ?::uuid[], ?::uuid[], ?::uuid[], ?::uuid[], ?::varchar[], ?::varchar[],
                            ?::varchar[], ?::varchar[], ?::text[])
                     AS d(signal_id, project_id, component_id, version_id, workspace_id, level, transitive, origine, date,
                          provenance)
                ON CONFLICT (signal_id, project_id, component_id) DO UPDATE SET
                       signal_version_id = excluded.signal_version_id, level = excluded.level, method = excluded.method,
                       transitive = excluded.transitive, inventory_origin = excluded.inventory_origin,
                       inventory_date = excluded.inventory_date, provenance = excluded.provenance, updated_at = now()
                WHERE (project_impact.signal_version_id, project_impact.level, project_impact.method,
                       project_impact.transitive, project_impact.inventory_origin, project_impact.inventory_date,
                       project_impact.provenance)
                      IS DISTINCT FROM (excluded.signal_version_id, excluded.level, excluded.method, excluded.transitive,
                       excluded.inventory_origin, excluded.inventory_date, excluded.provenance)
                   OR EXISTS (SELECT 1 FROM project_impact_transition t WHERE t.impact_id = project_impact.id
                              AND t.to_state IN ('OUVERT', 'CONFIRME') AND t.occurred_at > project_impact.updated_at)
                RETURNING signal_id, project_id, component_id, id, (xmax = 0), state
                """, requete -> {
                    requete.setString(1, MatchRules.METHOD);
                    requete.setArray(2, uuids(connexion, lignes, ligne -> ligne.triplet().signalId()));
                    requete.setArray(3, uuids(connexion, lignes, ligne -> ligne.triplet().projectId()));
                    requete.setArray(4, uuids(connexion, lignes, ligne -> ligne.triplet().componentId()));
                    requete.setArray(5, uuids(connexion, lignes, Ligne::signalVersionId));
                    requete.setArray(6, uuids(connexion, lignes, Ligne::workspaceId));
                    requete.setArray(7, textes(connexion, lignes, ligne -> ligne.level().name()));
                    requete.setArray(8, textes(connexion, lignes,
                            ligne -> ligne.transitive() == null ? null : ligne.transitive().toString()));
                    requete.setArray(9, textes(connexion, lignes, Ligne::inventoryOrigin));
                    requete.setArray(10, textes(connexion, lignes, ligne -> ligne.inventoryDate() == null ? null
                            : ligne.inventoryDate().atOffset(ZoneOffset.UTC).toString()));
                    requete.setArray(11, connexion.createArrayOf("text",
                            lignes.stream().map(Ligne::provenance).toArray()));
                }, resultat -> Map.entry(triplet(resultat), new Ecrit(resultat.getObject(4, UUID.class),
                        resultat.getBoolean(5), EtatImpact.valueOf(resultat.getString(6)))))
                .forEach(entree -> ecrits.put(entree.getKey(), entree.getValue()));
        return ecrits;
    }

    /** Impacts actifs des signaux du lot (et du projet donné) qui ne sont pas parmi ceux retenus. */
    static List<Perime> perimes(Connection connexion, Collection<UUID> signalIds, UUID projectId, Collection<UUID> retenus) {
        return Jdbc.lister(connexion, """
                SELECT id, signal_id, project_id, state, component_id, package_key FROM project_impact
                WHERE signal_id = ANY (?) AND (?::uuid IS NULL OR project_id = ?::uuid)
                  AND state IN ('OUVERT', 'CONFIRME') AND NOT (id = ANY (?))
                ORDER BY id
                """, requete -> {
                    requete.setArray(1, connexion.createArrayOf("uuid", signalIds.toArray()));
                    requete.setObject(2, projectId);
                    requete.setObject(3, projectId);
                    requete.setArray(4, connexion.createArrayOf("uuid", retenus.toArray()));
                }, resultat -> new Perime(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                        resultat.getObject(3, UUID.class), EtatImpact.valueOf(resultat.getString(4)),
                        resultat.getObject(5, UUID.class), resultat.getString(6)));
    }

    /** Paquets (projet + clé) encore présents dans l'inventaire, parmi les impacts périmés donnés. */
    static Set<String> paquetsPresents(Connection connexion, List<Perime> perimes) {
        if (perimes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(Jdbc.lister(connexion, """
                SELECT DISTINCT c.project_id, c.package_key FROM component c
                JOIN unnest(?::uuid[], ?::varchar[]) AS d(project_id, package_key)
                  ON c.project_id = d.project_id AND c.package_key = d.package_key
                """, requete -> {
                    requete.setArray(1, uuids(connexion, perimes, Perime::projectId));
                    requete.setArray(2, textes(connexion, perimes, Perime::packageKey));
                }, resultat -> clePaquet(resultat.getObject(1, UUID.class), resultat.getString(2))));
    }

    /** Clé d'un paquet d'un projet. */
    static String clePaquet(UUID projectId, String packageKey) {
        return projectId + " " + packageKey;
    }

    /** Ligne d'impact d'une correspondance. */
    static Ligne ligne(UUID signalId, UUID signalVersionId, InventoryQueries.ComponentMatch component,
            MatchRules.Level level, String provenance) {
        return new Ligne(new Triplet(signalId, component.projectId(), component.componentId()), signalVersionId,
                component.workspaceId(), level, component.transitive(), component.inventoryOrigin(),
                component.inventoryDate(), provenance, component.packageKey(), component.version());
    }

    private static Triplet triplet(ResultSet resultat) throws SQLException {
        return new Triplet(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                resultat.getObject(3, UUID.class));
    }

    private static void lierTriplets(Connection connexion, PreparedStatement requete, List<Ligne> lignes)
            throws SQLException {
        requete.setArray(1, uuids(connexion, lignes, ligne -> ligne.triplet().signalId()));
        requete.setArray(2, uuids(connexion, lignes, ligne -> ligne.triplet().projectId()));
        requete.setArray(3, uuids(connexion, lignes, ligne -> ligne.triplet().componentId()));
    }

    private static <T> Array uuids(Connection connexion, List<T> elements, Function<T, UUID> valeur) throws SQLException {
        return connexion.createArrayOf("uuid", elements.stream().map(valeur).toArray());
    }

    private static <T> Array textes(Connection connexion, List<T> elements, Function<T, String> valeur) throws SQLException {
        return connexion.createArrayOf("varchar", elements.stream().map(valeur).toArray());
    }
}
